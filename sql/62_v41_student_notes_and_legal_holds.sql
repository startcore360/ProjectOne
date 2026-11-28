-- EDU 360 V4.1 / T4-S3 + T4-S4. Existing NOTE_IMMUTABLE code is intentionally retained.
BEGIN;
ALTER TABLE app.student_note DROP CONSTRAINT IF EXISTS ck_student_note_note_type;
ALTER TABLE app.student_note ADD CONSTRAINT ck_student_note_note_type
  CHECK (note_type IN ('DISCIPLINE','HEALTH','ACADEMIC','BEHAVIORAL','MEDICAL','COUNSELING'));
CREATE INDEX IF NOT EXISTS ix_student_note_timeline
  ON app.student_note(tenant_id, student_profile_id, created_at DESC, student_note_id);
CREATE INDEX IF NOT EXISTS ix_legal_hold_student_active
  ON app.legal_hold(tenant_id, subject_id) WHERE subject_kind='STUDENT' AND released_at IS NULL;

CREATE OR REPLACE FUNCTION app.fn_note_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'UPDATE' THEN
    RAISE EXCEPTION 'NOTE_IMMUTABLE: notes are corrected by a new linked note [MD-19]' USING ERRCODE='42501';
  END IF;
  IF current_user <> 'edu_retention' THEN
    RAISE EXCEPTION 'NOTE_IMMUTABLE: only the retention job may delete [MD-22]' USING ERRCODE='42501';
  END IF;
  IF OLD.delete_after IS NULL OR OLD.delete_after > current_date THEN
    RAISE EXCEPTION 'NOTE_RETENTION: delete_after not reached' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM app.legal_hold h
     WHERE h.tenant_id=OLD.tenant_id AND h.subject_kind='STUDENT'
       AND h.subject_id=OLD.student_profile_id AND h.released_at IS NULL
  ) THEN
    RAISE EXCEPTION 'LEGAL_HOLD: deletion blocked' USING ERRCODE='23514';
  END IF;
  RETURN OLD;
END $$;

-- A legal hold is visible to application/reporting roles, but this migration deliberately
-- does not expose or grant placement/release operations. Those remain blocked by OD-34.
REVOKE INSERT, UPDATE, DELETE ON app.legal_hold FROM edu_app;
GRANT SELECT ON app.legal_hold TO edu_app;
GRANT SELECT ON app.legal_hold TO edu_report;
COMMIT;