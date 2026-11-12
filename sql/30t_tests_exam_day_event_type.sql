SELECT public.expect_ok('S2-CAL-DB-01','calendar event type permits EXAM_DAY',$q$
 DO $d$ BEGIN
  IF position('EXAM_DAY' IN pg_get_constraintdef('app.ck_academic_calendar_event_event_type'::regclass)) = 0
   THEN RAISE EXCEPTION 'EXAM_DAY_EVENT_TYPE_NOT_ALLOWED'; END IF;
 END $d$
$q$);

SELECT public.expect_ok('S2-CAL-DB-02','university EXAM_DAY is tenant-wide and institution EXAM_DAY is institution-scoped',$q$
 DO $d$ BEGIN
  IF position('EXAM_DAY' IN pg_get_constraintdef('app.ck_academic_calendar_event_r2'::regclass)) = 0
   OR position('EXAM_DAY' IN pg_get_constraintdef('app.ck_academic_calendar_event_r3'::regclass)) = 0
   THEN RAISE EXCEPTION 'EXAM_DAY_SCOPE_CONSTRAINTS_MISSING'; END IF;
 END $d$
$q$);
