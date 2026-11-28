-- T7 database contract checks for migration 67.
DO $$
BEGIN
    IF to_regclass('app.assignment') IS NULL OR to_regclass('app.assignment_submission') IS NULL
       OR to_regclass('app.submission_grade') IS NULL OR to_regclass('app.plagiarism_log') IS NULL THEN
        RAISE EXCEPTION 'T7-DB-67-01: assignment lifecycle tables are incomplete';
    END IF;
    IF NOT (SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='app.assignment'::regclass) THEN
        RAISE EXCEPTION 'T7-DB-67-02: assignment must have forced tenant RLS';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.assignment'::regclass
                    AND conname='fk_assignment_section'
                    AND pg_get_constraintdef(oid) LIKE '%institution_id%') THEN
        RAISE EXCEPTION 'T7-DB-67-03: assignment section FK must pin institution scope';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.assignment_submission'::regclass
                    AND conname='uq_assignment_submission_student') THEN
        RAISE EXCEPTION 'T7-DB-67-04: one submission per student and assignment must be enforced';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.assignment_submission'::regclass
                    AND conname='ck_assignment_submission_hash') THEN
        RAISE EXCEPTION 'T7-DB-67-05: submission SHA-256 checksum must be validated';
    END IF;
END $$;
