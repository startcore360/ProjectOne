-- T8 database contract checks for migration 70.
DO $$
DECLARE v_count integer; v_rule text;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM ref.standard_role_action
     WHERE (role_code, action_code) IN (
       ('MANAGEMENT','CONCESSION_APPROVE'),
       ('GOVERNING_BODY_APPROVER','CONCESSION_APPROVE'),
       ('FINANCE_DIRECTOR','CONCESSION_APPROVE'),
       ('PRINCIPAL_COLLEGE','CONCESSION_APPROVE'),
       ('SCHOOL_PRINCIPAL','CONCESSION_APPROVE')
     );
    IF v_count <> 5 THEN
        RAISE EXCEPTION 'T8-DB-08: PO-T04 concession approver roles are not fully mapped';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM ref.standard_role_action
     WHERE (role_code, action_code) IN (
       ('MANAGEMENT','SCHOLARSHIP_LIST_APPROVE'),
       ('GOVERNING_BODY_APPROVER','SCHOLARSHIP_LIST_APPROVE'),
       ('FINANCE_DIRECTOR','SCHOLARSHIP_LIST_APPROVE'),
       ('PRINCIPAL_COLLEGE','SCHOLARSHIP_LIST_APPROVE'),
       ('SCHOOL_PRINCIPAL','SCHOLARSHIP_LIST_APPROVE')
     );
    IF v_count <> 5 THEN
        RAISE EXCEPTION 'T8-DB-15: PO-T04 scholarship approver roles are not fully mapped';
    END IF;
    IF EXISTS (
        SELECT 1 FROM app.role_definition rd
         WHERE rd.role_origin='STANDARD' AND rd.role_code IN ('MANAGEMENT','GOVERNING_BODY_APPROVER','FINANCE_DIRECTOR')
           AND (
             NOT EXISTS (
               SELECT 1 FROM app.role_action ra
                WHERE ra.tenant_id=rd.tenant_id
                  AND ra.role_definition_id=rd.role_definition_id
                  AND ra.action_code='CONCESSION_APPROVE'
             )
             OR NOT EXISTS (
               SELECT 1 FROM app.role_action ra
                WHERE ra.tenant_id=rd.tenant_id
                  AND ra.role_definition_id=rd.role_definition_id
                  AND ra.action_code='SCHOLARSHIP_LIST_APPROVE'
             )
           )
    ) THEN
        RAISE EXCEPTION 'T8-DB-14: existing tenant role permissions were not provisioned';
    END IF;

    SELECT approver_rule INTO v_rule
      FROM ref.approval_chain_step
     WHERE process_code='CONCESSION' AND step_no=1 AND action_code='CONCESSION_APPROVE';
    IF v_rule IS NULL OR position('Management' IN v_rule)=0 THEN
        RAISE EXCEPTION 'T8-DB-09: concession approval precedence is not Management-first';
    END IF;
    IF (SELECT skip_if_role_absent FROM ref.approval_chain_step
         WHERE process_code='CONCESSION' AND step_no=1) IS DISTINCT FROM false THEN
        RAISE EXCEPTION 'T8-DB-10: concession approval step must never be skipped';
    END IF;
    SELECT approver_rule INTO v_rule
      FROM ref.approval_chain_step
     WHERE process_code='SCHOLARSHIP_LIST' AND step_no=1 AND action_code='SCHOLARSHIP_LIST_APPROVE';
    IF v_rule IS NULL OR position('Management' IN v_rule)=0 THEN
        RAISE EXCEPTION 'T8-DB-16: scholarship approval precedence is not Management-first';
    END IF;
    IF (SELECT skip_if_role_absent FROM ref.approval_chain_step
         WHERE process_code='SCHOLARSHIP_LIST' AND step_no=1) IS DISTINCT FROM false THEN
        RAISE EXCEPTION 'T8-DB-17: scholarship approval step must never be skipped';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_concession_guard' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'T8-DB-11: concession transition guard is missing';
    END IF;
    IF to_regclass('app.uq_ledger_entry_concession_once') IS NULL
       OR to_regclass('app.uq_payment_gateway_event_provider_event') IS NULL
       OR to_regclass('app.uq_gateway_order_provider_order') IS NULL THEN
        RAISE EXCEPTION 'T8-DB-12: finance idempotency indexes are missing';
    END IF;
    IF to_regprocedure('app.fn_allocation_guard()') IS NULL THEN
        RAISE EXCEPTION 'T8-DB-13: existing full-installment allocation guard is missing';
    END IF;
END $$;
