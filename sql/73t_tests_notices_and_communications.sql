DO $$
BEGIN
 IF to_regclass('app.notice_bulletin') IS NULL OR to_regclass('app.notice_acknowledgment') IS NULL
 OR to_regclass('app.communication_campaign') IS NULL OR to_regclass('app.message_template') IS NULL
 OR to_regclass('app.message_dispatch') IS NULL THEN RAISE EXCEPTION 'T10-73 tables missing'; END IF;
 IF NOT (SELECT relrowsecurity FROM pg_class WHERE oid='app.notice_bulletin'::regclass) THEN
  RAISE EXCEPTION 'notice_bulletin RLS is not enabled'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='app' AND indexname='ix_dispatch_usage') THEN
  RAISE EXCEPTION 'SMS usage index missing'; END IF;
END $$;
