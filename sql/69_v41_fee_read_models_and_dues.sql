-- EDU 360 V4.1 / T8 — finance read models and report-card fee-balance contract.
-- Reuses the existing append-only ledger, student installments, payments and receipts.
-- No invoice/payment source tables are introduced. Phase 1 prohibits partial payments,
-- refunds and tax handling; fn_allocation_guard() is intentionally unchanged.
BEGIN;

CREATE OR REPLACE FUNCTION app.fn_fee_balance(
    p_tenant_id uuid,
    p_student_profile_id uuid
) RETURNS numeric
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = pg_catalog, app
AS $$
    SELECT COALESCE(SUM(GREATEST(b.signed_balance, 0)), 0)::numeric
      FROM app.student_fee_account a
      JOIN app.v_student_fee_balance b
        ON b.tenant_id = a.tenant_id
       AND b.student_fee_account_id = a.student_fee_account_id
     WHERE a.tenant_id = p_tenant_id
       AND a.student_profile_id = p_student_profile_id
$$;

CREATE OR REPLACE VIEW app.v_student_dues_flag
WITH (security_invoker = true)
AS
SELECT students.tenant_id,
       students.student_profile_id,
       EXISTS (
           SELECT 1
             FROM app.student_fee_account a
             JOIN app.v_student_fee_balance b
               ON b.tenant_id = a.tenant_id
              AND b.student_fee_account_id = a.student_fee_account_id
            WHERE a.tenant_id = students.tenant_id
              AND a.student_profile_id = students.student_profile_id
              AND b.signed_balance > 0
       ) AS has_dues
  FROM (
      SELECT DISTINCT tenant_id, student_profile_id
        FROM app.student_fee_account
  ) students;

-- Read-only invoice projection over the canonical installment and ledger records.
CREATE OR REPLACE VIEW app.v_fee_invoice
WITH (security_invoker = true)
AS
SELECT si.tenant_id,
       si.institution_id,
       sfa.student_profile_id,
       si.student_fee_account_id,
       si.student_installment_id AS invoice_id,
       si.student_installment_id,
       fa.fee_assignment_id,
       fa.fee_structure_id,
       si.installment_no,
       si.due_date,
       si.billed_amount AS base_amount,
       COALESCE(le.concession_amount, 0)::numeric(14,2) AS concession_amount,
       COALESCE(le.scholarship_amount, 0)::numeric(14,2) AS scholarship_amount,
       COALESCE(le.late_fee_amount, 0)::numeric(14,2) AS late_fee_amount,
       COALESCE(le.late_fee_waiver_amount, 0)::numeric(14,2) AS late_fee_waiver_amount,
       COALESCE(pay.paid_amount, 0)::numeric(14,2) AS paid_amount,
       nd.net_due,
       CASE
         WHEN si.status IN ('CANCELLED', 'PAID') THEN 0::numeric
         ELSE GREATEST(nd.net_due, 0)
       END AS outstanding_amount,
       CASE
         WHEN si.status = 'CANCELLED' THEN 'CANCELLED'
         WHEN si.status = 'PAID' THEN 'PAID'
         WHEN si.due_date < CURRENT_DATE AND GREATEST(nd.net_due, 0) > 0 THEN 'OVERDUE'
         ELSE 'ISSUED'
       END AS status,
       si.paid_at,
       si.created_at
  FROM app.student_installment si
  JOIN app.student_fee_account sfa
    ON sfa.tenant_id = si.tenant_id
   AND sfa.student_fee_account_id = si.student_fee_account_id
  JOIN app.fee_assignment fa
    ON fa.tenant_id = si.tenant_id
   AND fa.fee_assignment_id = si.fee_assignment_id
  JOIN app.v_installment_net_due nd
    ON nd.tenant_id = si.tenant_id
   AND nd.student_installment_id = si.student_installment_id
  LEFT JOIN LATERAL (
      SELECT
        SUM(l.amount) FILTER (WHERE l.entry_type = 'CONCESSION') AS concession_amount,
        SUM(l.amount) FILTER (WHERE l.entry_type = 'SCHOLARSHIP') AS scholarship_amount,
        SUM(l.amount) FILTER (WHERE l.entry_type = 'LATE_FEE') AS late_fee_amount,
        SUM(l.amount) FILTER (WHERE l.entry_type = 'LATE_FEE_WAIVER') AS late_fee_waiver_amount
      FROM app.ledger_entry l
      WHERE l.tenant_id = si.tenant_id
        AND l.student_installment_id = si.student_installment_id
  ) le ON true
  LEFT JOIN LATERAL (
      SELECT
        COALESCE((SELECT SUM(pa.allocated_amount)
                    FROM app.payment_allocation pa
                   WHERE pa.tenant_id = si.tenant_id
                     AND pa.student_installment_id = si.student_installment_id), 0)
        + COALESCE((SELECT SUM(ca.amount)
                      FROM app.credit_application ca
                     WHERE ca.tenant_id = si.tenant_id
                       AND ca.student_installment_id = si.student_installment_id), 0)
        AS paid_amount
  ) pay ON true;

-- The existing finance model specifies one account per student and one installment
-- number per assignment/structure. Enforce those invariants before invoice assignment APIs.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM app.student_fee_account
         GROUP BY tenant_id, student_profile_id HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'DUPLICATE_STUDENT_FEE_ACCOUNTS: reconcile existing accounts before T8 invoice assignment';
    END IF;
    IF EXISTS (
        SELECT 1 FROM app.fee_assignment
         WHERE status='ACTIVE'
         GROUP BY tenant_id, enrollment_id, fee_structure_id HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'DUPLICATE_ACTIVE_FEE_ASSIGNMENTS: reconcile existing assignments before T8 invoice assignment';
    END IF;
    IF EXISTS (
        SELECT 1 FROM app.student_installment
         GROUP BY tenant_id, fee_assignment_id, installment_no HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'DUPLICATE_STUDENT_INSTALLMENTS: reconcile existing installment numbers before T8 invoice assignment';
    END IF;
    IF EXISTS (
        SELECT 1 FROM app.fee_structure_installment
         GROUP BY tenant_id, fee_structure_id, installment_no HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'DUPLICATE_FEE_STRUCTURE_INSTALLMENTS: reconcile existing template numbers before T8 invoice assignment';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_student_fee_account_student
    ON app.student_fee_account (tenant_id, student_profile_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fee_assignment_active_enrollment_structure
    ON app.fee_assignment (tenant_id, enrollment_id, fee_structure_id)
 WHERE status='ACTIVE';
CREATE UNIQUE INDEX IF NOT EXISTS uq_student_installment_assignment_no
    ON app.student_installment (tenant_id, fee_assignment_id, installment_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fee_structure_installment_structure_no
    ON app.fee_structure_installment (tenant_id, fee_structure_id, installment_no);

CREATE INDEX IF NOT EXISTS ix_student_fee_account_student
    ON app.student_fee_account (tenant_id, student_profile_id);
CREATE INDEX IF NOT EXISTS ix_student_installment_account_due
    ON app.student_installment (tenant_id, student_fee_account_id, due_date, status);
CREATE INDEX IF NOT EXISTS ix_ledger_entry_installment_type
    ON app.ledger_entry (tenant_id, student_installment_id, entry_type);
CREATE INDEX IF NOT EXISTS ix_payment_allocation_installment
    ON app.payment_allocation (tenant_id, student_installment_id);

GRANT SELECT ON app.v_fee_invoice, app.v_student_fee_balance, app.v_student_dues_flag
    TO edu_app, edu_report;
REVOKE ALL ON FUNCTION app.fn_fee_balance(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.fn_fee_balance(uuid, uuid) TO edu_app, edu_report;

COMMENT ON VIEW app.v_fee_invoice IS
    'T8 invoice read model over student_installment, fee_assignment and the append-only ledger; never a source-of-truth table.';
COMMENT ON FUNCTION app.fn_fee_balance(uuid, uuid) IS
    'T8/T7 contract: positive outstanding balance for a student, used by the fail-closed report-card publication gate.';

COMMIT;
