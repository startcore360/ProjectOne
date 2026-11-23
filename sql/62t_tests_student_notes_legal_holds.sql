DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_note_guard' AND tgrelid='app.student_note'::regclass AND NOT tgisinternal) THEN
    RAISE EXCEPTION 'T4-62: immutable note trigger is not installed';
  END IF;
  IF NOT has_table_privilege('edu_app','app.legal_hold','SELECT') THEN
    RAISE EXCEPTION 'T4-62: app role cannot read legal holds';
  END IF;
  IF has_table_privilege('edu_app','app.legal_hold','INSERT') OR has_table_privilege('edu_app','app.legal_hold','UPDATE') OR has_table_privilege('edu_app','app.legal_hold','DELETE') THEN
    RAISE EXCEPTION 'T4-62: app role has prohibited legal hold write privileges';
  END IF;
END $$;
