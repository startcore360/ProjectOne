DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_guardian_limit' AND tgrelid='app.student_guardian_link'::regclass AND NOT tgisinternal) THEN
    RAISE EXCEPTION 'T4-61: guardian cap trigger is not installed';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_guardian_primary_exactly_one' AND tgrelid='app.student_guardian_link'::regclass AND NOT tgisinternal) THEN
    RAISE EXCEPTION 'T4-61: primary guardian constraint trigger is not installed';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_student_profile_no_delete' AND tgrelid='app.student_profile'::regclass AND NOT tgisinternal) THEN
    RAISE EXCEPTION 'T4-61: student hard-delete guard is not installed';
  END IF;
END $$;
