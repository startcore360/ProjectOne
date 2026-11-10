-- EDU 360 V4.1 — rollback for 16_v41_tenant_top_post_authority.sql.
-- Roll back 20_v41_never_allowed_guard.sql first (20r) if it was applied; dropping the tables
-- below also drops its triggers on them, but 20r removes the role_action trigger and functions.
--
-- DB-A12: app.tenant_step_in is append-only audit evidence (TPA-02). This rollback refuses to
-- drop it while it holds rows, unless the operator has exported them and confirms with
--   SET edu360.rollback_discard_step_ins = 'on';
-- in the same session. Nothing is dropped if the check fails.
DO $$
DECLARE
  n bigint := 0;
BEGIN
  IF to_regclass('app.tenant_step_in') IS NOT NULL THEN
    EXECUTE 'SELECT count(*) FROM app.tenant_step_in' INTO n;
    IF n > 0 AND coalesce(current_setting('edu360.rollback_discard_step_ins', true), '') <> 'on' THEN
      RAISE EXCEPTION 'app.tenant_step_in holds % step-in rows (append-only evidence). Export them, then SET edu360.rollback_discard_step_ins = ''on'' and re-run [DB-A12]', n;
    END IF;
  END IF;
END $$;

DROP TABLE IF EXISTS app.tenant_step_in CASCADE;
DROP TABLE IF EXISTS app.tenant_role_template_action CASCADE;
DROP TABLE IF EXISTS app.tenant_role_template CASCADE;

ALTER TABLE app.role_action DROP COLUMN IF EXISTS access_mode;
ALTER TABLE app.role_definition DROP COLUMN IF EXISTS owner_level;
ALTER TABLE app.role_definition DROP COLUMN IF EXISTS owner_institution_id;

-- DB-A12: Restore ck_role_definition_status to the original ('ACTIVE','RETIRED') (01_schema.sql).
-- Roles in the V4.1-only states are mapped to RETIRED first so the restored CHECK holds.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'app' AND table_name = 'role_definition') THEN
        ALTER TABLE app.role_definition DROP CONSTRAINT IF EXISTS ck_role_definition_status;
        UPDATE app.role_definition SET status = 'RETIRED' WHERE status NOT IN ('ACTIVE', 'RETIRED');
        ALTER TABLE app.role_definition ADD CONSTRAINT ck_role_definition_status CHECK (status IN ('ACTIVE','RETIRED'));
    END IF;
END $$;
