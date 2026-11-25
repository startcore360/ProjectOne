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

-- Trustees (Governing Body Approver) are the alternate primary authority when no
-- Management assignment exists. The router enforces that precedence at decision time.
INSERT INTO ref.standard_role_action (standard_role_action_id, role_code, action_code, limited_to)
SELECT (SELECT COALESCE(MAX(standard_role_action_id),0)+1 FROM ref.standard_role_action),
       'GOVERNING_BODY_APPROVER', 'CONCESSION_APPROVE',
       'Own tenant; Trustee authority when no active Management assignment exists (PO-T04 / TPA-04)'
 WHERE NOT EXISTS (
       SELECT 1 FROM ref.standard_role_action
        WHERE role_code='GOVERNING_BODY_APPROVER' AND action_code='CONCESSION_APPROVE'
 );

-- Finance Director is permitted only when an authorized Management-step roll-down
-- explicitly designates that role; the action grant alone does not create an approval step.
INSERT INTO ref.standard_role_action (standard_role_action_id, role_code, action_code, limited_to)
SELECT (SELECT COALESCE(MAX(standard_role_action_id),0)+1 FROM ref.standard_role_action),
       'FINANCE_DIRECTOR', 'CONCESSION_APPROVE',
       'Own university scope; only when designated by the Management approval roll-down'
 WHERE NOT EXISTS (
       SELECT 1 FROM ref.standard_role_action
        WHERE role_code='FINANCE_DIRECTOR' AND action_code='CONCESSION_APPROVE'
 );

INSERT INTO ref.standard_role_action (standard_role_action_id, role_code, action_code, limited_to)
SELECT (SELECT COALESCE(MAX(standard_role_action_id),0)+1 FROM ref.standard_role_action),
       'MANAGEMENT', 'SCHOLARSHIP_LIST_APPROVE',
       'Own tenant; primary scholarship approval authority under PO-T04'
 WHERE NOT EXISTS (
       SELECT 1 FROM ref.standard_role_action
        WHERE role_code='MANAGEMENT' AND action_code='SCHOLARSHIP_LIST_APPROVE'
 );

INSERT INTO ref.standard_role_action (standard_role_action_id, role_code, action_code, limited_to)
SELECT (SELECT COALESCE(MAX(standard_role_action_id),0)+1 FROM ref.standard_role_action),
       'GOVERNING_BODY_APPROVER', 'SCHOLARSHIP_LIST_APPROVE',
       'Own tenant; Trustee authority when no active Management assignment exists (PO-T04 / TPA-04)'
 WHERE NOT EXISTS (
       SELECT 1 FROM ref.standard_role_action
        WHERE role_code='GOVERNING_BODY_APPROVER' AND action_code='SCHOLARSHIP_LIST_APPROVE'
 );

INSERT INTO ref.standard_role_action (standard_role_action_id, role_code, action_code, limited_to)
SELECT (SELECT COALESCE(MAX(standard_role_action_id),0)+1 FROM ref.standard_role_action),
       'FINANCE_DIRECTOR', 'SCHOLARSHIP_LIST_APPROVE',
       'Own university scope; only when designated by the Management approval roll-down'
 WHERE NOT EXISTS (
       SELECT 1 FROM ref.standard_role_action
        WHERE role_code='FINANCE_DIRECTOR' AND action_code='SCHOLARSHIP_LIST_APPROVE'
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
   AND rd.role_code IN ('MANAGEMENT','GOVERNING_BODY_APPROVER','FINANCE_DIRECTOR')
   AND NOT EXISTS (
       SELECT 1 FROM app.role_action ra
        WHERE ra.tenant_id=rd.tenant_id
          AND ra.role_definition_id=rd.role_definition_id
          AND ra.action_code='CONCESSION_APPROVE'
   );

INSERT INTO app.role_action
    (tenant_id, role_action_id, role_definition_id, action_code, created_by, updated_by)
SELECT rd.tenant_id, gen_random_uuid(), rd.role_definition_id, 'SCHOLARSHIP_LIST_APPROVE',
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
   AND rd.role_code IN ('MANAGEMENT','GOVERNING_BODY_APPROVER','FINANCE_DIRECTOR')
   AND NOT EXISTS (
       SELECT 1 FROM app.role_action ra
        WHERE ra.tenant_id=rd.tenant_id
          AND ra.role_definition_id=rd.role_definition_id
          AND ra.action_code='SCHOLARSHIP_LIST_APPROVE'
   );

UPDATE ref.approval_chain_step
   SET approver_rule = 'Management first; if absent, active Governing Body Approver (Trustees); otherwise Principal or designated financial approver through V4-PD-01 roll-down (PO-T04)',
       skip_if_role_absent = false
 WHERE process_code='CONCESSION' AND step_no=1 AND action_code='CONCESSION_APPROVE';

UPDATE ref.approval_chain_step
   SET approver_rule = 'Management first; if absent, active Governing Body Approver (Trustees); otherwise Principal or designated financial approver through V4-PD-01 roll-down (PO-T04)',
       skip_if_role_absent = false
 WHERE process_code='SCHOLARSHIP_LIST' AND step_no=1 AND action_code='SCHOLARSHIP_LIST_APPROVE';

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

-- Payment idempotency and single-settlement invariants.
DO $
BEGIN
    IF EXISTS (SELECT 1 FROM app.payment GROUP BY tenant_id, request_key HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM app.payment_intent_item
                   GROUP BY tenant_id, payment_id, student_installment_id HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM app.payment_allocation
                   GROUP BY tenant_id, student_installment_id HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM app.receipt GROUP BY tenant_id, payment_id HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM app.gateway_transaction
                   GROUP BY tenant_id, gateway_order_id, provider_payment_id HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM app.ledger_entry
                   WHERE entry_type='PAYMENT' AND payment_id IS NOT NULL
                   GROUP BY tenant_id, payment_id, academic_session_id HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'DUPLICATE_PAYMENT_RECORDS: reconcile existing payment/receipt allocations before T8 idempotency indexes';
    END IF;
END $;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_request_key
    ON app.payment (tenant_id, request_key);
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_intent_item_installment
    ON app.payment_intent_item (tenant_id, payment_id, student_installment_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_allocation_installment
    ON app.payment_allocation (tenant_id, student_installment_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_receipt_payment
    ON app.receipt (tenant_id, payment_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_gateway_transaction_provider_payment
    ON app.gateway_transaction (tenant_id, gateway_order_id, provider_payment_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ledger_payment_session
    ON app.ledger_entry (tenant_id, payment_id, academic_session_id)
 WHERE entry_type='PAYMENT' AND payment_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_payment_account_status_created
    ON app.payment (tenant_id, student_fee_account_id, status, created_at DESC);

-- PO-T03 supersedes the earlier OD-28 draft modes. Do not silently reinterpret existing
-- FLAT / PERCENT_OF_DUE / PER_DAY rules: require an explicit data migration if any exist.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM app.late_fee_rule
                WHERE calculation_mode NOT IN ('FLAT_DAILY','PERCENTAGE_MONTHLY','NONE')) THEN
        RAISE EXCEPTION 'PO-T03_MODE_MIGRATION_REQUIRED: map existing OD-28 late-fee rules explicitly before applying migration 70';
    END IF;
END $$;
ALTER TABLE app.late_fee_rule DROP CONSTRAINT IF EXISTS ck_late_fee_rule_calculation_mode;
ALTER TABLE app.late_fee_rule
    ADD CONSTRAINT ck_late_fee_rule_calculation_mode
    CHECK (calculation_mode IN ('FLAT_DAILY','PERCENTAGE_MONTHLY','NONE'));
ALTER TABLE app.late_fee_rule DROP CONSTRAINT IF EXISTS ck_late_fee_rule_amount_or_percent;
ALTER TABLE app.late_fee_rule
    ADD CONSTRAINT ck_late_fee_rule_amount_or_percent
    CHECK (calculation_mode='NONE' OR amount_or_percent > 0);

CREATE INDEX IF NOT EXISTS ix_late_fee_rule_effective
    ON app.late_fee_rule (tenant_id, institution_id, status, effective_from DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_late_fee_entry_installment_day
    ON app.ledger_entry (tenant_id, student_installment_id, posting_date)
 WHERE entry_type='LATE_FEE' AND student_installment_id IS NOT NULL;

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

COMMENT ON TABLE app.late_fee_rule IS 'PO-T03: configurable FLAT_DAILY, PERCENTAGE_MONTHLY or NONE late-fee rule with grace period and cap.';
COMMENT ON FUNCTION app.fn_concession_guard() IS
    'T8 / PO-T04 / TPA-04: immutable concession request; exactly one matching ledger credit before APPLIED; no ledger movement on REJECTED.';
COMMENT ON INDEX app.uq_ledger_entry_concession_once IS
    'One concession ledger credit per approved concession; idempotent application.';
COMMENT ON INDEX app.uq_payment_gateway_event_provider_event IS
    'Provider event idempotency key within a tenant.';
COMMENT ON INDEX app.uq_gateway_order_provider_order IS
    'Provider order ids are unique within a tenant and provider.';

COMMIT;
