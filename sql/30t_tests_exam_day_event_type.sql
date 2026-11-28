SELECT public.expect_ok('S2-CAL-DB-01','calendar event type permits EXAM_DAY',$q$
 DO $d$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
      WHERE conrelid='app.academic_calendar_event'::regclass
        AND conname='ck_academic_calendar_event_event_type'
        AND position('EXAM_DAY' IN pg_get_constraintdef(oid)) > 0)
   THEN RAISE EXCEPTION 'EXAM_DAY_EVENT_TYPE_NOT_ALLOWED'; END IF;
 END $d$
$q$);

SELECT public.expect_ok('S2-CAL-DB-02','university EXAM_DAY is tenant-wide and institution EXAM_DAY is institution-scoped',$q$
 DO $d$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
      WHERE conrelid='app.academic_calendar_event'::regclass
        AND conname='ck_academic_calendar_event_r2'
        AND position('EXAM_DAY' IN pg_get_constraintdef(oid)) > 0)
   OR NOT EXISTS (SELECT 1 FROM pg_constraint
      WHERE conrelid='app.academic_calendar_event'::regclass
        AND conname='ck_academic_calendar_event_r3'
        AND position('EXAM_DAY' IN pg_get_constraintdef(oid)) > 0)
   THEN RAISE EXCEPTION 'EXAM_DAY_SCOPE_CONSTRAINTS_MISSING'; END IF;
 END $d$
$q$);
