-- E7-S1 contract check. Run after 44_v41_release_scheduling.sql.
\set ON_ERROR_STOP 1
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='platform' AND table_name='release' AND column_name='scheduled_at') THEN
  RAISE EXCEPTION 'RELEASE_SCHEDULED_AT_MISSING';
 END IF;
END $$;

DO $$
BEGIN
 IF to_regclass('platform.request_metric_hourly') IS NULL THEN
  RAISE EXCEPTION 'REQUEST_METRIC_HOURLY_MISSING';
 END IF;
 IF NOT EXISTS (
  SELECT 1 FROM information_schema.columns
   WHERE table_schema='platform' AND table_name='request_metric_hourly'
     AND column_name='route_template'
 ) THEN
  RAISE EXCEPTION 'REQUEST_METRIC_ROUTE_TEMPLATE_MISSING';
 END IF;
 IF NOT EXISTS (
  SELECT 1 FROM information_schema.role_table_grants
   WHERE table_schema='platform' AND table_name='request_metric_hourly'
     AND grantee='edu_platform' AND privilege_type='INSERT'
 ) THEN
  RAISE EXCEPTION 'REQUEST_METRIC_PLATFORM_GRANT_MISSING';
 END IF;
END $$;
