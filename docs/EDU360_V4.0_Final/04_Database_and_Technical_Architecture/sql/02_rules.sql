-- EDU 360 V3.3 — database-enforced rules (triggers, functions, exclusion constraints, RLS, grants, views).
-- Each block names the rule ID (K-xxx from Document 1 §4.25.4, or NEW-xx for rules added by this correction).

-- ---------------------------------------------------------------- generic helpers
CREATE OR REPLACE FUNCTION app.fn_touch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := now();
  NEW.row_version := OLD.row_version + 1;
  RETURN NEW;
END$$;
-- Same helper in platform and ref, so the central database (platform + ref) needs nothing from app (split deployment, PQ-70).
CREATE OR REPLACE FUNCTION platform.fn_touch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := now();
  NEW.row_version := OLD.row_version + 1;
  RETURN NEW;
END$$;
CREATE OR REPLACE FUNCTION ref.fn_touch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := now();
  NEW.row_version := OLD.row_version + 1;
  RETURN NEW;
END$$;

CREATE OR REPLACE FUNCTION app.fn_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'APPEND_ONLY: % on %.% is not allowed', TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = '42501';
END$$;
CREATE OR REPLACE FUNCTION platform.fn_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'APPEND_ONLY: % on %.% is not allowed', TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = '42501';
END$$;

-- audit rows: only the ship columns may change, and only by the shipper role
CREATE OR REPLACE FUNCTION app.fn_audit_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'AUDIT_IMMUTABLE: delete refused' USING ERRCODE = '42501'; END IF;
  IF (to_jsonb(NEW) - 'ship_status' - 'shipped_at' - 'store_object_key' - 'updated_at' - 'updated_by' - 'row_version')
     <> (to_jsonb(OLD) - 'ship_status' - 'shipped_at' - 'store_object_key' - 'updated_at' - 'updated_by' - 'row_version') THEN
    RAISE EXCEPTION 'AUDIT_IMMUTABLE: only shipment columns may change' USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END$$;
CREATE OR REPLACE FUNCTION platform.fn_audit_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'AUDIT_IMMUTABLE: delete refused' USING ERRCODE = '42501'; END IF;
  IF (to_jsonb(NEW) - 'ship_status' - 'shipped_at' - 'store_object_key' - 'updated_at' - 'updated_by' - 'row_version')
     <> (to_jsonb(OLD) - 'ship_status' - 'shipped_at' - 'store_object_key' - 'updated_at' - 'updated_by' - 'row_version') THEN
    RAISE EXCEPTION 'AUDIT_IMMUTABLE: only shipment columns may change' USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END$$;

-- student notes: no UPDATE; DELETE only by the retention role, past delete_after, no active hold (K-054)
CREATE OR REPLACE FUNCTION app.fn_note_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'UPDATE' THEN RAISE EXCEPTION 'NOTE_IMMUTABLE: notes are corrected by a new linked note [MD-19]' USING ERRCODE = '42501'; END IF;
  IF current_user <> 'edu_retention' AND current_user <> 'postgres' THEN
    RAISE EXCEPTION 'NOTE_IMMUTABLE: only the retention job may delete [MD-22]' USING ERRCODE = '42501'; END IF;
  IF OLD.delete_after IS NULL OR OLD.delete_after > current_date THEN
    RAISE EXCEPTION 'NOTE_RETENTION: delete_after not reached' USING ERRCODE = '23514'; END IF;
  IF EXISTS (SELECT 1 FROM app.legal_hold h WHERE h.tenant_id = OLD.tenant_id AND h.subject_kind = 'STUDENT'
             AND h.subject_id = OLD.student_profile_id AND h.released_at IS NULL) THEN
    RAISE EXCEPTION 'LEGAL_HOLD: deletion blocked' USING ERRCODE = '23514'; END IF;
  RETURN OLD;
END$$;

-- gateway events: append-only except processing columns
CREATE OR REPLACE FUNCTION app.fn_gateway_event_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'APPEND_ONLY: gateway events are kept' USING ERRCODE = '42501'; END IF;
  IF (to_jsonb(NEW) - 'processed_at' - 'outcome' - 'institution_id' - 'gateway_order_id' - 'gateway_transaction_id' - 'updated_at' - 'updated_by' - 'row_version')
     <> (to_jsonb(OLD) - 'processed_at' - 'outcome' - 'institution_id' - 'gateway_order_id' - 'gateway_transaction_id' - 'updated_at' - 'updated_by' - 'row_version') THEN
    RAISE EXCEPTION 'APPEND_ONLY: payload and identity of a gateway event never change' USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END$$;

-- ---------------------------------------------------------------- go-live gate (NEW-01, C7)
CREATE OR REPLACE FUNCTION app.fn_institution_activation_gate() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.status = 'ACTIVE' AND OLD.status IS DISTINCT FROM 'ACTIVE' THEN
    IF NOT EXISTS (SELECT 1 FROM app.leave_approver_setting s
                   WHERE s.tenant_id = NEW.tenant_id AND s.subject_role = 'PRINCIPAL'
                     AND s.institution_id = NEW.institution_id AND s.status = 'ACTIVE') THEN
      RAISE EXCEPTION 'GO_LIVE_BLOCKED: Principal''s-leave approver not set for institution % [C7]', NEW.institution_id USING ERRCODE = '23514';
    END IF;
    NEW.activated_at := coalesce(NEW.activated_at, now());
  END IF;
  RETURN NEW;
END$$;

-- tenant go-live check used by TENANT_ACTIVATE (the platform service calls it inside the tenant database)
CREATE OR REPLACE FUNCTION app.go_live_blockers(p_tenant uuid) RETURNS TABLE(blocker text, institution_id uuid) LANGUAGE sql STABLE AS $$
  SELECT 'PRINCIPAL_LEAVE_APPROVER_MISSING', i.institution_id FROM app.institution i
   WHERE i.tenant_id = p_tenant AND i.status IN ('DRAFT','ACTIVE')
     AND NOT EXISTS (SELECT 1 FROM app.leave_approver_setting s WHERE s.tenant_id = i.tenant_id
                     AND s.subject_role = 'PRINCIPAL' AND s.institution_id = i.institution_id AND s.status = 'ACTIVE')
  UNION ALL
  SELECT 'VC_LEAVE_APPROVER_MISSING', NULL::uuid FROM app.tenant_local t
   WHERE t.tenant_id = p_tenant AND t.tenant_type = 'UNIVERSITY_GROUP'
     AND NOT EXISTS (SELECT 1 FROM app.leave_approver_setting s WHERE s.tenant_id = t.tenant_id
                     AND s.subject_role = 'VICE_CHANCELLOR' AND s.status = 'ACTIVE')
$$;

-- approver is never the person whose leave it is (D-002)
CREATE OR REPLACE FUNCTION app.fn_leave_approver_not_subject() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_kind text;
BEGIN
  SELECT user_type INTO v_kind FROM app.user_account WHERE tenant_id = NEW.tenant_id AND user_account_id = NEW.approver_user_id;
  IF NEW.approver_kind IN ('MANAGEMENT','GOVERNING_BODY_APPROVER') AND v_kind IS DISTINCT FROM 'GOVERNANCE' THEN
    RAISE EXCEPTION 'APPROVER_KIND_MISMATCH: Management / Governing Body Approver must be a governance account [C6]' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END$$;

-- ---------------------------------------------------------------- approval engine (K-014..K-018)
CREATE OR REPLACE FUNCTION app.fn_approval_step_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_req uuid;
BEGIN
  IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'APPROVAL_IMMUTABLE: steps are never deleted' USING ERRCODE = '42501'; END IF;
  IF TG_OP = 'UPDATE' AND OLD.decision <> 'PENDING' THEN
    RAISE EXCEPTION 'APPROVAL_IMMUTABLE: decided step % cannot change [R2-01]', OLD.approval_step_id USING ERRCODE = '42501';
  END IF;
  IF NEW.decision IN ('APPROVED','REJECTED','FORWARDED') THEN
    SELECT requested_by_user_id INTO v_req FROM app.approval_request
     WHERE tenant_id = NEW.tenant_id AND approval_request_id = NEW.approval_request_id;
    IF NEW.acted_by_user_id = v_req THEN
      RAISE EXCEPTION 'SELF_APPROVAL: requester cannot decide own request [AP-04, PQ-18, D-002]' USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END$$;

-- ---------------------------------------------------------------- delegation (K-013, D-001)
CREATE OR REPLACE FUNCTION app.fn_delegation_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM app.delegation d WHERE d.tenant_id = NEW.tenant_id AND d.status = 'ACTIVE'
             AND d.delegate_user_id = NEW.delegator_user_id
             AND tstzrange(d.starts_at, d.ends_at) && tstzrange(NEW.starts_at, NEW.ends_at)) THEN
    RAISE EXCEPTION 'REDELEGATION: a delegate cannot delegate again [AP-08]' USING ERRCODE = '23514';
  END IF;
  IF NEW.process_code IS NOT NULL AND EXISTS (SELECT 1 FROM ref.approval_process p WHERE p.process_code = NEW.process_code AND NOT p.allows_delegation) THEN
    RAISE EXCEPTION 'DELEGATION_NOT_ALLOWED: process % cannot be delegated [WF-37]', NEW.process_code USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END$$;

-- ---------------------------------------------------------------- guardians: at most 3 non-blocked (K-011)
CREATE OR REPLACE FUNCTION app.fn_guardian_limit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF (SELECT count(*) FROM app.student_guardian_link l WHERE l.tenant_id = NEW.tenant_id
        AND l.student_profile_id = NEW.student_profile_id AND l.status <> 'BLOCKED') > 3 THEN
    RAISE EXCEPTION 'GUARDIAN_LIMIT_REACHED: at most 3 guardians [WF-24]' USING ERRCODE = '23514';
  END IF;
  RETURN NULL;
END$$;

-- ---------------------------------------------------------------- term lock (K-027)
CREATE OR REPLACE FUNCTION app.is_term_locked(p_tenant uuid, p_section uuid, p_date date) RETURNS boolean LANGUAGE sql STABLE AS $$
  SELECT EXISTS (
    SELECT 1 FROM app.section s
      JOIN app.academic_session a ON a.tenant_id = s.tenant_id AND a.academic_session_id = s.academic_session_id
      JOIN app.term_lock l ON l.tenant_id = s.tenant_id AND l.institution_id = s.institution_id
                          AND l.academic_session_id = s.academic_session_id
                          AND (l.semester_id IS NULL OR l.semester_id = s.semester_id)
     WHERE s.tenant_id = p_tenant AND s.section_id = p_section
       AND p_date BETWEEN a.start_date AND a.end_date
       AND (l.lock_state = 'LOCKED' OR l.unlock_until < now()))
$$;

CREATE OR REPLACE FUNCTION app.fn_attendance_entry_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_sec uuid; v_date date; v_year smallint; v_held text;
BEGIN
  SELECT coalesce(s.section_id, (SELECT min(m.section_id::text)::uuid FROM app.subject_group g
                                  JOIN app.course_offering o ON o.tenant_id=g.tenant_id AND o.course_offering_id=g.course_offering_id
                                  JOIN app.section m ON m.tenant_id=o.tenant_id AND m.academic_session_id=o.academic_session_id
                                  WHERE g.tenant_id=s.tenant_id AND g.subject_group_id=s.subject_group_id)),
         s.attendance_date, s.academic_year, s.held_status
    INTO v_sec, v_date, v_year, v_held
    FROM app.attendance_session s WHERE s.tenant_id = NEW.tenant_id AND s.attendance_session_id = NEW.attendance_session_id;
  IF NEW.academic_year <> v_year THEN RAISE EXCEPTION 'PARTITION_YEAR_MISMATCH' USING ERRCODE = '23514'; END IF;
  IF (NEW.status = 'NOT_HELD') <> (v_held = 'NOT_HELD') THEN
    RAISE EXCEPTION 'NOT_HELD_MISMATCH: entry status must mirror the session [AT-12]' USING ERRCODE = '23514'; END IF;
  IF app.is_term_locked(NEW.tenant_id, v_sec, v_date) THEN
    RAISE EXCEPTION 'TERM_LOCKED: attendance cannot change while the term is locked [R2-08]' USING ERRCODE = '55000'; END IF;
  RETURN NEW;
END$$;

-- ---------------------------------------------------------------- marks (K-036)
CREATE OR REPLACE FUNCTION app.fn_marks_entry_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_status text; v_sub uuid; v_tenant uuid; v_sec uuid;
BEGIN
  v_sub := CASE WHEN TG_OP = 'DELETE' THEN OLD.marks_submission_id ELSE NEW.marks_submission_id END;
  v_tenant := CASE WHEN TG_OP = 'DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END;
  SELECT status, section_id INTO v_status, v_sec FROM app.marks_submission WHERE tenant_id = v_tenant AND marks_submission_id = v_sub;
  IF v_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'SUBMISSION_LOCKED: marks change only while the submission is DRAFT [R2-01]' USING ERRCODE = '55000'; END IF;
  IF v_sec IS NOT NULL AND app.is_term_locked(v_tenant, v_sec, current_date) THEN
    RAISE EXCEPTION 'TERM_LOCKED [R2-08]' USING ERRCODE = '55000'; END IF;
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END$$;

-- ---------------------------------------------------------------- results (K-037)
CREATE OR REPLACE FUNCTION app.fn_result_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.status = 'PUBLISHED' AND (NEW.status <> 'WITHDRAWN' OR
     (to_jsonb(NEW) - 'status' - 'updated_at' - 'updated_by' - 'row_version') <> (to_jsonb(OLD) - 'status' - 'updated_at' - 'updated_by' - 'row_version')) THEN
    RAISE EXCEPTION 'RESULT_PUBLISHED: a published result only changes to WITHDRAWN [PQ-40]' USING ERRCODE = '55000';
  END IF;
  IF OLD.status IN ('WITHDRAWN','SUPERSEDED') THEN
    RAISE EXCEPTION 'RESULT_FINAL: % results never change; issue a new version [PQ-40]', OLD.status USING ERRCODE = '55000';
  END IF;
  RETURN NEW;
END$$;

-- publish gate (K-038): every live submission of the exam (and section) must be PRINCIPAL_APPROVED; publisher must match the mode
CREATE OR REPLACE FUNCTION app.fn_publish_gate() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_type text; v_ext boolean; v_tt text; v_under boolean;
BEGIN
  IF EXISTS (SELECT 1 FROM app.marks_submission m WHERE m.tenant_id = NEW.tenant_id AND m.exam_id = NEW.exam_id
               AND (NEW.section_id IS NULL OR m.section_id = NEW.section_id)
               AND m.status NOT IN ('PRINCIPAL_APPROVED','REJECTED','WITHDRAWN','PUBLISHED')) THEN
    RAISE EXCEPTION 'RESULT_NOT_APPROVED: publish only after final approval [AP-16]' USING ERRCODE = '55000';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM app.marks_submission m WHERE m.tenant_id = NEW.tenant_id AND m.exam_id = NEW.exam_id
               AND (NEW.section_id IS NULL OR m.section_id = NEW.section_id) AND m.status = 'PRINCIPAL_APPROVED') THEN
    RAISE EXCEPTION 'RESULT_NOT_APPROVED: nothing approved to publish' USING ERRCODE = '55000';
  END IF;
  SELECT i.institution_type, i.external_university_mode, i.under_university INTO v_type, v_ext, v_under
    FROM app.institution i WHERE i.tenant_id = NEW.tenant_id AND i.institution_id = NEW.institution_id;
  IF v_type = 'SCHOOL' AND NEW.publisher_basis NOT IN ('SCHOOL_PRINCIPAL','SCHOOL_ADMIN')
     OR v_type = 'COLLEGE' AND v_under AND NOT v_ext AND NEW.publisher_basis <> 'CONTROLLER'
     OR v_type = 'COLLEGE' AND (NOT v_under OR v_ext) AND NEW.publisher_basis <> 'COLLEGE_ADMIN' THEN
    RAISE EXCEPTION 'WRONG_PUBLISHER_FOR_MODE: % cannot publish here [§7.1, HI-10]', NEW.publisher_basis USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END$$;

-- ---------------------------------------------------------------- fee structure (K-040 corrected, NEW-02 / finding 36)
CREATE OR REPLACE FUNCTION app.fn_fee_structure_child_lock() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_status text; v_t uuid; v_s uuid;
BEGIN
  v_t := CASE WHEN TG_OP = 'DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END;
  v_s := CASE WHEN TG_OP = 'DELETE' THEN OLD.fee_structure_id ELSE NEW.fee_structure_id END;
  SELECT status INTO v_status FROM app.fee_structure WHERE tenant_id = v_t AND fee_structure_id = v_s;
  IF v_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'FEE_STRUCTURE_LOCKED: only a DRAFT structure can change; any later change is a new version approved by the Principal [R2-03, FE-02]' USING ERRCODE = '55000';
  END IF;
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END$$;

CREATE OR REPLACE FUNCTION app.fn_fee_structure_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_lines numeric; v_inst numeric;
BEGIN
  IF OLD.status <> 'DRAFT' AND (to_jsonb(NEW) - 'status' - 'approval_request_id' - 'updated_at' - 'updated_by' - 'row_version')
                              <> (to_jsonb(OLD) - 'status' - 'approval_request_id' - 'updated_at' - 'updated_by' - 'row_version') THEN
    RAISE EXCEPTION 'FEE_STRUCTURE_LOCKED: content of a submitted structure never changes' USING ERRCODE = '55000';
  END IF;
  IF NOT (OLD.status = NEW.status
       OR (OLD.status, NEW.status) IN (('DRAFT','PENDING_APPROVAL'),('PENDING_APPROVAL','LIVE'),('PENDING_APPROVAL','REJECTED'),('LIVE','SUPERSEDED'))) THEN
    RAISE EXCEPTION 'STATE_TRANSITION_NOT_ALLOWED: fee_structure % -> %', OLD.status, NEW.status USING ERRCODE = '55000';
  END IF;
  IF OLD.status = 'DRAFT' AND NEW.status = 'PENDING_APPROVAL' THEN
    SELECT coalesce(sum(amount),0) INTO v_lines FROM app.fee_structure_line WHERE tenant_id = NEW.tenant_id AND fee_structure_id = NEW.fee_structure_id;
    SELECT coalesce(sum(amount),0) INTO v_inst FROM app.fee_structure_installment WHERE tenant_id = NEW.tenant_id AND fee_structure_id = NEW.fee_structure_id;
    IF v_lines <> v_inst OR v_lines = 0 THEN
      RAISE EXCEPTION 'INSTALLMENT_TOTAL_MISMATCH: lines % vs installments % (PTD-32)', v_lines, v_inst USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END$$;

-- a line outside the policy range needs an APPROVED exception for the same structure and head (K-041)
CREATE OR REPLACE FUNCTION app.fn_fee_line_range() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_min numeric; v_max numeric; v_ok boolean;
BEGIN
  SELECT r.min_amount, r.max_amount INTO v_min, v_max
    FROM app.fee_structure s JOIN app.fee_head_rule r ON r.tenant_id = s.tenant_id AND r.fee_policy_id = s.fee_policy_id AND r.fee_head_id = NEW.fee_head_id
   WHERE s.tenant_id = NEW.tenant_id AND s.fee_structure_id = NEW.fee_structure_id;
  IF v_min IS NULL THEN RAISE EXCEPTION 'FEE_HEAD_NOT_IN_POLICY' USING ERRCODE = '23514'; END IF;
  IF NEW.amount < v_min OR NEW.amount > v_max THEN
    SELECT EXISTS (SELECT 1 FROM app.fee_exception e WHERE e.tenant_id = NEW.tenant_id AND e.fee_exception_id = NEW.fee_exception_id
                    AND e.fee_structure_id = NEW.fee_structure_id AND e.fee_head_id = NEW.fee_head_id
                    AND e.requested_amount = NEW.amount AND e.status = 'APPROVED') INTO v_ok;
    IF NOT v_ok THEN RAISE EXCEPTION 'FEE_OUT_OF_POLICY_RANGE: approved exception required [R2-03]' USING ERRCODE = '23514'; END IF;
  END IF;
  RETURN NEW;
END$$;

-- ---------------------------------------------------------------- financial period lock (K-043)
CREATE OR REPLACE FUNCTION app.is_period_locked(p_tenant uuid, p_inst uuid, p_date date) RETURNS boolean LANGUAGE sql STABLE AS $$
  SELECT EXISTS (SELECT 1 FROM app.financial_period_lock l WHERE l.tenant_id = p_tenant
                   AND l.institution_id IS NOT DISTINCT FROM p_inst
                   AND l.period_month = date_trunc('month', p_date)::date AND l.lock_state <> 'UNLOCKED')
$$;

CREATE OR REPLACE FUNCTION app.fn_ledger_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.financial_year := CASE WHEN extract(month FROM NEW.posting_date) >= 4 THEN extract(year FROM NEW.posting_date)
                             ELSE extract(year FROM NEW.posting_date) - 1 END;
  IF app.is_period_locked(NEW.tenant_id, NEW.institution_id, NEW.posting_date) THEN
    RAISE EXCEPTION 'PERIOD_LOCKED: posting into a locked month [FE-20, PQ-50]' USING ERRCODE = '55000';
  END IF;
  IF NEW.entry_type = 'REVERSAL' THEN
    PERFORM 1 FROM app.ledger_entry r WHERE r.tenant_id = NEW.tenant_id AND r.ledger_entry_id = NEW.reverses_entry_id
       AND r.student_fee_account_id = NEW.student_fee_account_id AND r.amount = NEW.amount AND r.direction <> NEW.direction;
    IF NOT FOUND THEN RAISE EXCEPTION 'REVERSAL_MISMATCH: same account, amount and opposite direction required [PQ-45]' USING ERRCODE = '23514'; END IF;
  END IF;
  RETURN NEW;
END$$;

-- ---------------------------------------------------------------- views (PTD-37, Doc 1 C3)
CREATE OR REPLACE VIEW app.v_installment_net_due WITH (security_invoker = true) AS
SELECT i.tenant_id, i.student_installment_id, i.student_fee_account_id, i.institution_id, i.due_date, i.status,
       i.billed_amount
       + coalesce(sum(l.amount) FILTER (WHERE l.entry_type = 'LATE_FEE'), 0)
       - coalesce(sum(l.amount) FILTER (WHERE l.entry_type IN ('SCHOLARSHIP','CONCESSION','LATE_FEE_WAIVER')), 0) AS net_due
  FROM app.student_installment i
  LEFT JOIN app.ledger_entry l ON l.tenant_id = i.tenant_id AND l.student_installment_id = i.student_installment_id
 GROUP BY i.tenant_id, i.student_installment_id;

CREATE OR REPLACE VIEW app.v_student_fee_balance WITH (security_invoker = true) AS
SELECT a.tenant_id, a.student_fee_account_id,
       coalesce(sum(l.amount) FILTER (WHERE l.direction = 'DEBIT'), 0) - coalesce(sum(l.amount) FILTER (WHERE l.direction = 'CREDIT'), 0) AS signed_balance,
       coalesce(sum(l.amount) FILTER (WHERE l.entry_type = 'CREDIT_ADDED'), 0) - coalesce(sum(l.amount) FILTER (WHERE l.entry_type = 'CREDIT_CONSUMED'), 0) AS credit_balance
  FROM app.student_fee_account a
  LEFT JOIN app.ledger_entry l ON l.tenant_id = a.tenant_id AND l.student_fee_account_id = a.student_fee_account_id
 GROUP BY a.tenant_id, a.student_fee_account_id;

CREATE OR REPLACE VIEW app.v_student_overdue_flag WITH (security_invoker = true) AS
SELECT a.tenant_id, a.student_profile_id,
       EXISTS (SELECT 1 FROM app.student_installment i WHERE i.tenant_id = a.tenant_id AND i.student_fee_account_id = a.student_fee_account_id
                AND i.status = 'UNPAID' AND i.due_date < current_date) AS has_overdue
  FROM app.student_fee_account a;

CREATE OR REPLACE VIEW app.v_student_dues_flag WITH (security_invoker = true) AS
SELECT a.tenant_id, a.student_profile_id,
       EXISTS (SELECT 1 FROM app.student_installment i WHERE i.tenant_id = a.tenant_id AND i.student_fee_account_id = a.student_fee_account_id
                AND i.status = 'UNPAID') AS has_dues   -- definition (any unpaid vs overdue) is OD-51
  FROM app.student_fee_account a;

-- payment allocation: installment must be UNPAID and amount must equal net due (K-045)
CREATE OR REPLACE FUNCTION app.fn_allocation_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_due numeric; v_status text; v_pay text;
BEGIN
  SELECT net_due, status INTO v_due, v_status FROM app.v_installment_net_due WHERE tenant_id = NEW.tenant_id AND student_installment_id = NEW.student_installment_id;
  SELECT status INTO v_pay FROM app.payment WHERE tenant_id = NEW.tenant_id AND payment_id = NEW.payment_id;
  IF v_status <> 'UNPAID' THEN RAISE EXCEPTION 'INSTALLMENT_ALREADY_PAID' USING ERRCODE = '23505'; END IF;
  IF NEW.allocated_amount <> v_due THEN RAISE EXCEPTION 'PARTIAL_PAYMENT_NOT_ALLOWED: % <> net due % [FE-04]', NEW.allocated_amount, v_due USING ERRCODE = '23514'; END IF;
  IF v_pay NOT IN ('STARTED','PENDING','PAID') THEN RAISE EXCEPTION 'PAYMENT_NOT_CONFIRMABLE' USING ERRCODE = '55000'; END IF;
  RETURN NEW;
END$$;

-- receipts: only ISSUED -> VOID with reason, approval and replacement (K-047)
CREATE OR REPLACE FUNCTION app.fn_receipt_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'RECEIPT_IMMUTABLE: receipts are never deleted [FE-12]' USING ERRCODE = '42501'; END IF;
  IF OLD.status = 'VOID' OR NEW.status <> 'VOID' OR
     (to_jsonb(NEW) - 'status' - 'void_reason' - 'void_approval_request_id' - 'voided_at' - 'replaced_by_receipt_id' - 'updated_at' - 'updated_by' - 'row_version')
     <> (to_jsonb(OLD) - 'status' - 'void_reason' - 'void_approval_request_id' - 'voided_at' - 'replaced_by_receipt_id' - 'updated_at' - 'updated_by' - 'row_version') THEN
    RAISE EXCEPTION 'RECEIPT_IMMUTABLE: only ISSUED -> VOID is allowed [FE-12]' USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END$$;

-- gap-free receipt number (K-048): called inside the confirmation transaction
CREATE OR REPLACE FUNCTION app.next_receipt_number(p_tenant uuid, p_inst uuid, p_fy smallint) RETURNS integer LANGUAGE plpgsql AS $$
DECLARE v integer;
BEGIN
  UPDATE app.receipt_sequence SET last_number = last_number + 1
   WHERE tenant_id = p_tenant AND institution_id = p_inst AND financial_year = p_fy RETURNING last_number INTO v;
  IF v IS NULL THEN RAISE EXCEPTION 'RECEIPT_SEQUENCE_MISSING' USING ERRCODE = '55000'; END IF;
  RETURN v;
END$$;

-- complaint: RESOLVED is final (K-055)
CREATE OR REPLACE FUNCTION app.fn_complaint_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.status = 'RESOLVED' THEN RAISE EXCEPTION 'COMPLAINT_FINAL: no reopening [CP-04]' USING ERRCODE = '55000'; END IF;
  RETURN NEW;
END$$;

-- ---------------------------------------------------------------- exclusion constraints
ALTER TABLE app.staff_assignment ADD CONSTRAINT ex_staff_assignment_overlap EXCLUDE USING gist (
  tenant_id WITH =, staff_profile_id WITH =, (coalesce(institution_id, '00000000-0000-0000-0000-000000000000'::uuid)) WITH =,
  (coalesce(department_id, '00000000-0000-0000-0000-000000000000'::uuid)) WITH =, designation WITH =,
  daterange(effective_from, effective_to, '[]') WITH &&) WHERE (status = 'ACTIVE');
ALTER TABLE app.leave_request ADD CONSTRAINT ex_leave_full_day_overlap EXCLUDE USING gist (
  tenant_id WITH =, (coalesce(for_enrollment_id, for_staff_profile_id)) WITH =, daterange(start_date, end_date, '[]') WITH &&)
  WHERE (status IN ('PENDING','APPROVED','BLOCKED') AND unit = 'FULL_DAY');
ALTER TABLE app.fee_assignment ADD CONSTRAINT ex_fee_assignment_overlap EXCLUDE USING gist (
  tenant_id WITH =, enrollment_id WITH =, daterange(effective_from, effective_to, '[]') WITH &&) WHERE (status = 'ACTIVE');
ALTER TABLE app.weekly_off_rule ADD CONSTRAINT ex_weekly_off_overlap EXCLUDE USING gist (
  tenant_id WITH =, institution_id WITH =, day_of_week WITH =, daterange(effective_from, effective_to, '[]') WITH &&);

-- ---------------------------------------------------------------- named indexes from Document 1 §4.25.5 (not FK-derived)
CREATE INDEX ix_student_name_trgm ON app.student_profile USING gin (full_name gin_trgm_ops);
CREATE INDEX ix_student_search ON app.student_profile (tenant_id, institution_id, student_code);
CREATE INDEX ix_att_entry_student ON app.attendance_entry (tenant_id, enrollment_id, academic_year);
CREATE INDEX ix_att_session_day ON app.attendance_session (tenant_id, institution_id, attendance_date);
CREATE INDEX ix_leave_pending ON app.leave_request (tenant_id, institution_id, status) WHERE status = 'PENDING';
CREATE INDEX ix_approval_pending ON app.approval_step (tenant_id, approver_user_id) WHERE decision = 'PENDING';
CREATE INDEX ix_marks_submission_exam ON app.marks_submission (tenant_id, exam_id, status);
CREATE INDEX ix_ledger_account ON app.ledger_entry (tenant_id, student_fee_account_id, posting_date);
CREATE INDEX ix_ledger_institution_fy ON app.ledger_entry (tenant_id, institution_id, financial_year, posting_date);
CREATE INDEX ix_installment_due ON app.student_installment (tenant_id, due_date) WHERE status = 'UNPAID';
CREATE INDEX ix_payment_pending ON app.payment (tenant_id, status, created_at) WHERE status IN ('STARTED','PENDING');
CREATE INDEX ix_notification_user ON app.notification (tenant_id, recipient_user_id, created_at DESC);
CREATE INDEX ix_delivery_retry ON app.notification_delivery (tenant_id, status, next_attempt_at);
CREATE INDEX ix_outbox_pending ON app.outbox_event (status, next_attempt_at) WHERE status IN ('PENDING','FAILED');
CREATE INDEX ix_audit_ship ON app.audit_outbox (ship_status, occurred_at) WHERE ship_status <> 'SHIPPED';
CREATE INDEX ix_job_claim ON app.background_job (state, run_after) WHERE state IN ('QUEUED','RETRY_WAIT');
CREATE INDEX ix_note_student ON app.student_note (tenant_id, student_profile_id, created_at);
CREATE INDEX ix_note_delete ON app.student_note (delete_after) WHERE delete_after IS NOT NULL;
CREATE INDEX ix_grant_active ON platform.support_access_grant (tenant_id, expires_at);

-- ---------------------------------------------------------------- K-028: correction lines frozen once the request leaves DRAFT
CREATE OR REPLACE FUNCTION app.fn_attendance_correction_lock() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_status text; v_t uuid; v_r uuid;
BEGIN
  v_t := CASE WHEN TG_OP = 'DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END;
  v_r := CASE WHEN TG_OP = 'DELETE' THEN OLD.attendance_correction_request_id ELSE NEW.attendance_correction_request_id END;
  SELECT status INTO v_status FROM app.attendance_correction_request WHERE tenant_id = v_t AND attendance_correction_request_id = v_r;
  IF v_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'CORRECTION_LOCKED: correction lines change only while the request is DRAFT [PQ-33]' USING ERRCODE = '55000';
  END IF;
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END$$;

-- ---------------------------------------------------------------- K-033: grade bands frozen once the scheme version leaves DRAFT
CREATE OR REPLACE FUNCTION app.fn_grade_band_lock() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_status text; v_t uuid; v_g uuid;
BEGIN
  v_t := CASE WHEN TG_OP = 'DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END;
  v_g := CASE WHEN TG_OP = 'DELETE' THEN OLD.grading_scheme_version_id ELSE NEW.grading_scheme_version_id END;
  SELECT status INTO v_status FROM app.grading_scheme_version WHERE tenant_id = v_t AND grading_scheme_version_id = v_g;
  IF v_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'GRADING_SCHEME_FROZEN: an active or retired scheme version never changes; create a new version [PQ-38, PQ-43]' USING ERRCODE = '55000';
  END IF;
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END$$;
