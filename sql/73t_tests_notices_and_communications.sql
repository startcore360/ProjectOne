DO $$
DECLARE t text;
BEGIN
 IF to_regclass('app.notice_bulletin') IS NULL OR to_regclass('app.notice_acknowledgment') IS NULL
 OR to_regclass('app.notice_target') IS NULL OR to_regclass('app.communication_campaign') IS NULL
 OR to_regclass('app.message_template') IS NULL OR to_regclass('app.message_dispatch') IS NULL
 THEN RAISE EXCEPTION 'T10-73 tables missing'; END IF;
 FOREACH t IN ARRAY ARRAY['notice_bulletin','notice_target','notice_acknowledgment','communication_campaign','message_template','message_dispatch'] LOOP
  IF NOT (SELECT relrowsecurity FROM pg_class WHERE oid=format('app.%I',t)::regclass) THEN RAISE EXCEPTION 'RLS missing on app.%',t; END IF;
 END LOOP;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.notice_target'::regclass AND conname='fk_notice_target_scope') THEN RAISE EXCEPTION 'notice target scope FK missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='message_dispatch' AND column_name='attempt_order') THEN RAISE EXCEPTION 'dispatch fallback order missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='message_dispatch' AND column_name='claimed_at') THEN RAISE EXCEPTION 'dispatch lease column missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='message_dispatch' AND column_name='attempt_count') THEN RAISE EXCEPTION 'dispatch retry counter missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.message_dispatch'::regclass AND conname='ck_message_dispatch_status') THEN RAISE EXCEPTION 'dispatch worker status constraint missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='app' AND indexname='ix_dispatch_usage') THEN
  RAISE EXCEPTION 'SMS usage index missing'; END IF;
END $$;
