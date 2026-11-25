-- EDU 360 V4.1 / T8 — concession approval precedence and payment idempotency constraints.
-- Reuses app.concession, app.ledger_entry, app.payment and app.payment_gateway_event.
-- No parallel finance tables, partial payments, refunds or tax handling are introduced.
BEGIN;

-- PO-T04: Management is the first approver. The generic approval router resolves a missing
-- MANAGEMENT role through the existing V4-PD-01 roll-down map (Principal by default; an
-- institution-specific designated financial approver may be configured by an authorized
-- roll-down override). This step is not optional and is never skipped.
INSERT INTO ref.standard_role_action (standard_role_action_id, role_code, action_code, limited_to)
SELECT (SELECT COALESCE(MAX(standard_role_action_id),0)+1 FROM ref.standard_role_action),
       'MANAGEMENT', 'CONCESSION_APPROVE',
       'Own tenant; primary fee concession authority under PO-T04'
 WHERE NOT EXISTS (
       SELECT 1 FROM ref.standard_role_action
        WHERE role_code='MANAGEMENT' AND action_code='CONCESSION_APPROVE'
 );

-- University Finance Director can be designated as the Management-step stand-in through
-- the existing role roll-down override mechanism. The approval router still requires that
-- the current step resolves to this role; this grant alone does not create an approval step.
INSERT INTO ref.standard_role_action (standard_role_action_id, role_code, action_code, limited_to)
SELECT (SELECT COALESCE(MAX(standard_role_action_id),0)+1 FROM ref.standard_role_action),
       'FINANCE_DIRECTOR', 'CONCESSION_APPROVE',
       'Own university scope; only when designated by the Management approval roll-down'
 WHERE NOT EXISTS (
       SELECT 1 FROM ref.standard_role_action
        WHERE role_code='FINANCE_DIRECTOR' AND action_code='CONCESSION_APPROVE'
 );

-- PermissionEvaluatorService checks app.role_action at runtime, not the reference template.
-- Provision the new standard action into already-provisioned tenants as well as future ones.
INSERT INTO app.role_action
    (tenant_id, role_action_id, role_definition_id, action_code, created_by, updated_by)
SELECT rd.tenant_id, gen_random_uuid(), rd.role_definition_id, 'CONCESSION_APPROVE',
       COALESCE(system_actor.user_account_id, rd.created_by),
       COALESCE(system_actor.user_account_id, rd.updated_by)
  FROM app.role_definition rd
  LEFT JOIN LATERAL (
      SELECT ua.user_account_id
        FROM app.user_account ua
       WHERE ua.tenant_id=rd.tenant_id AND ua.user_type='SYSTEM'
       ORDER BY ua.created_at
       LIMIT 1
  ) system_actor ON true
 WHERE rd.role_origin='STANDARD'
   AND rd.role_code IN ('MANAGEMENT','FINANCE_DIRECTOR')
   AND NOT EXISTS (
       SELECT 1 FROM app.role_action ra
        WHERE ra.tenant_id=rd.tenant_id
          AND ra.role_definition_id=rd.role_definition_id
          AND ra.action_code='CONCESSION_APPROVE'
   );

UPDATE ref.approval_chain_step
   SET approver_rule = 'Management / Trustees first; if no Management assignment, Principal or designated financial approver through V4-PD-01 roll-down (PO-T04)',
       skip_if_role_absent = false
 WHERE process_code='CONCESSION' AND step_no=1 AND action_code='CONCESSION_APPROVE';

-- The service creates the current approval step with role MANAGEMENT. ApprovalRouting applies
-- the default Management -> Principal mapping or a tenant override. Never skip this step.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ledger_entry_concession_once
    ON app.ledger_entry (tenant_id, concession_id)
 WHERE entry_type='CONCESSION';

-- Gateway event identity is provider-scoped and idempotent within a tenant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_gateway_event_provider_event
    ON app.payment_gateway_event (tenant_id, provider, provider_event_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gateway_order_provider_order
    ON app.gateway_order (tenant_id, provider, provider_order_id);

CREATE INDEX IF NOT EXISTS ix_payment_account_status_created
    ON app.payment (tenant_id, student_fee_account_id, status, created_at DESC);

CREATE OR REPLACE FUNCTION app.fn_concession_guard()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'CONCESSION_IMMUTABLE: concession requests are never deleted'
            USING ERRCODE='42501';
    END IF;

    IF OLD.status <> 'REQUESTED'
       OR NEW.status NOT IN ('APPLIED','REJECTED')
       OR (to_jsonb(NEW) - 'status' - 'updated_at' - 'updated_by' - 'row_version')
          <> (to_jsonb(OLD) - 'status' - 'updated_at' - 'updated_by' - 'row_version') THEN
        RAISE EXCEPTION 'CONCESSION_TRANSITION_INVALID: only REQUESTED to APPLIED or REJECTED is allowed'
            USING ERRCODE='55000';
    END IF;

    IF NEW.status='APPLIED' AND NOT EXISTS (
        SELECT 1 FROM app.ledger_entry l
         WHERE l.tenant_id=NEW.tenant_id
           AND l.concession_id=NEW.concession_id
           AND l.entry_type='CONCESSION'
           AND l.amount=NEW.amount
           AND l.direction='CREDIT'
    ) THEN
        RAISE EXCEPTION 'CONCESSION_LEDGER_ENTRY_REQUIRED: approval must post one matching ledger credit'
            USING ERRCODE='23514';
    END IF;

    IF NEW.status='REJECTED' AND EXISTS (
        SELECT 1 FROM app.ledger_entry l
         WHERE l.tenant_id=NEW.tenant_id
           AND l.concession_id=NEW.concession_id
           AND l.entry_type='CONCESSION'
    ) THEN
        RAISE EXCEPTION 'CONCESSION_REJECTED_HAS_LEDGER_ENTRY'
            USING ERRCODE='23514';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_concession_guard ON app.concession;
CREATE TRIGGER trg_concession_guard
    BEFORE UPDATE OR DELETE ON app.concession
    FOR EACH ROW EXECUTE FUNCTION app.fn_concession_guard();

GRANT EXECUTE ON FUNCTION app.fn_concession_guard() TO edu_app;

COMMENT ON FUNCTION app.fn_concession_guard() IS
    'T8 / PO-T04 / TPA-04: immutable concession request; exactly one matching ledger credit before APPLIED; no ledger movement on REJECTED.';
COMMENT ON INDEX app.uq_ledger_entry_concession_once IS
    'One concession ledger credit per approved concession; idempotent application.';
COMMENT ON INDEX app.uq_payment_gateway_event_provider_event IS
    'Provider event idempotency key within a tenant.';
COMMENT ON INDEX app.uq_gateway_order_provider_order IS
    'Provider order ids are unique within a tenant and provider.';

COMMIT;
