-- EDU 360 V4.1 — batch B4 database tests DB-210 .. DB-212 (file 22).
-- Run after 21_tests_audit_guard_v41.sql (expect_* helpers: 04_tests.sql). Seeding runs as the superuser.
\set ON_ERROR_STOP 1
SET client_min_messages = warning;

SELECT public.expect_value('DB-210', 'L-07 platform.audit_outbox has an event_type column',
  $q$SELECT count(*)::text FROM information_schema.columns WHERE table_schema='platform' AND table_name='audit_outbox' AND column_name='event_type'$q$, '1');

SELECT public.expect_ok('DB-211', 'L-01 a named event type is accepted',
  $q$INSERT INTO platform.audit_outbox (audit_outbox_id, occurred_at, actor_kind, operation, entity_type, entity_id, outcome, event_type, correlation_id, payload_hash, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b4a1', now(), 'SYSTEM', 'APPROVE', 'DELETION_REQUEST', '00000000-0000-0000-0000-00000000b4a2', 'SUCCESS', 'TENANT_DELETION_APPROVED', 'b4-test', repeat('a',64), '00000000-0000-0000-0000-00000000b4a3', '00000000-0000-0000-0000-00000000b4a3')$q$);

SELECT public.expect_fail('DB-212', 'L-07 an event type over 60 characters is refused',
  $q$INSERT INTO platform.audit_outbox (audit_outbox_id, occurred_at, actor_kind, operation, entity_type, entity_id, outcome, event_type, correlation_id, payload_hash, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b4a4', now(), 'SYSTEM', 'APPROVE', 'DELETION_REQUEST', '00000000-0000-0000-0000-00000000b4a2', 'SUCCESS', repeat('E',61), 'b4-test', repeat('a',64), '00000000-0000-0000-0000-00000000b4a3', '00000000-0000-0000-0000-00000000b4a3')$q$,
  'ck_audit_outbox_event_type');
