-- Sprint 2 Chunk 1 SQL checks. Run after 04_tests.sql.
SELECT public.expect_ok('S2-DB-01','2028-2030 attendance partitions provisioned',$q$
 DO $d$ BEGIN
  IF to_regclass('app.attendance_entry_y2028') IS NULL OR to_regclass('app.attendance_entry_y2029') IS NULL OR to_regclass('app.attendance_entry_y2030') IS NULL
  THEN RAISE EXCEPTION 'ATTENDANCE_PARTITION_MISSING'; END IF;
 END $d$
$q$);
SELECT public.expect_ok('S2-DB-02','six attendance setting definitions and defaults',$q$
 DO $d$ DECLARE n int; BEGIN
  SELECT count(*) INTO n FROM ref.setting_definition WHERE setting_key IN
   ('attendance.minimum_overall_percent','attendance.minimum_subject_percent','attendance.formula',
    'attendance.school_marking_pattern','attendance.risk_warning_percent','attendance.risk_critical_percent');
  IF n<>6 THEN RAISE EXCEPTION 'ATTENDANCE_SETTINGS_INCOMPLETE'; END IF;
  IF (SELECT default_value FROM ref.setting_definition WHERE setting_key='attendance.minimum_overall_percent')<>'75'::jsonb
   OR (SELECT default_value FROM ref.setting_definition WHERE setting_key='attendance.minimum_subject_percent')<>'75'::jsonb
   OR (SELECT default_value FROM ref.setting_definition WHERE setting_key='attendance.formula')<>'"SECTION_20_DEFAULT"'::jsonb
   OR (SELECT default_value FROM ref.setting_definition WHERE setting_key='attendance.school_marking_pattern')<>'"TWICE_DAILY"'::jsonb
   OR (SELECT default_value FROM ref.setting_definition WHERE setting_key='attendance.risk_warning_percent')<>'75'::jsonb
   OR (SELECT default_value FROM ref.setting_definition WHERE setting_key='attendance.risk_critical_percent')<>'65'::jsonb
  THEN RAISE EXCEPTION 'ATTENDANCE_SETTING_DEFAULT_MISMATCH'; END IF;
 END $d$
$q$);
SELECT public.expect_ok('S2-DB-03','DEC-14 syllabus view grants',$q$
 DO $d$ DECLARE n int; BEGIN
  SELECT count(*) INTO n FROM ref.standard_role_action WHERE action_code='SYLLABUS_VIEW'
   AND role_code IN ('PROFESSOR','ASSOCIATE_PROFESSOR','ASSISTANT_PROFESSOR','HEAD_OF_DEPARTMENT','CLASS_TEACHER',
    'SUBJECT_TEACHER','DEPARTMENT_COORDINATOR','PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL','VICE_PRINCIPAL_COLLEGE','VICE_PRINCIPAL_SCHOOL');
  IF n<>11 THEN RAISE EXCEPTION 'SYLLABUS_VIEW_GRANTS_INCOMPLETE'; END IF;
 END $d$
$q$);
