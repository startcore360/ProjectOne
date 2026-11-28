-- 55_v41_term_lock_engine.sql
-- T1-S3/T1-S4: immutable term-lock transitions, 72-hour unlock log and scheduled relock.
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS app.term_unlock_log (
    tenant_id uuid NOT NULL,
    id uuid NOT NULL,
    term_id uuid NOT NULL,
    unlocked_by_user_id uuid NOT NULL,
    reason text NOT NULL,
    unlocked_at timestamptz NOT NULL DEFAULT now(),
    relocked_at timestamptz,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_term_unlock_log PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_term_unlock_log_term FOREIGN KEY (tenant_id, term_id)
        REFERENCES app.term_lock (tenant_id, term_lock_id) ON DELETE RESTRICT,
    CONSTRAINT fk_term_unlock_log_actor FOREIGN KEY (tenant_id, unlocked_by_user_id)
        REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_term_unlock_log_reason CHECK (char_length(btrim(reason)) BETWEEN 20 AND 500),
    CONSTRAINT ck_term_unlock_log_row_version CHECK (row_version >= 1),
    CONSTRAINT ck_term_unlock_log_active CHECK (is_active = (relocked_at IS NULL)),
    CONSTRAINT ck_term_unlock_log_relocked CHECK (relocked_at IS NULL OR relocked_at >= unlocked_at),
    CONSTRAINT ck_term_unlock_log_updated CHECK (updated_at >= created_at),
    CONSTRAINT uq_term_unlock_log_tid UNIQUE (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS ix_term_unlock_log_due
    ON app.term_unlock_log (tenant_id, unlocked_at) WHERE is_active;
ALTER TABLE app.term_unlock_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.term_unlock_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.term_unlock_log;
CREATE POLICY p_tenant ON app.term_unlock_log
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE ON app.term_unlock_log TO edu_app;
GRANT SELECT ON app.term_unlock_log TO edu_report;

CREATE OR REPLACE FUNCTION app.fn_term_lock_immutability()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.tenant_id <> OLD.tenant_id
       OR NEW.term_lock_id <> OLD.term_lock_id
       OR NEW.institution_id <> OLD.institution_id
       OR NEW.academic_session_id <> OLD.academic_session_id
       OR NEW.semester_id IS DISTINCT FROM OLD.semester_id THEN
        RAISE EXCEPTION 'TERM_LOCK_IDENTITY_IMMUTABLE: term lock scope cannot be changed'
            USING ERRCODE = '23514';
    END IF;
    IF NEW.lock_state = 'TEMP_UNLOCKED' THEN
        IF NEW.unlock_until IS NULL OR NOT EXISTS (
            SELECT 1 FROM app.term_unlock_log l
             WHERE l.tenant_id = NEW.tenant_id AND l.term_id = NEW.term_lock_id
               AND l.is_active AND l.relocked_at IS NULL
               AND NEW.unlock_until = l.unlocked_at + interval '72 hours'
        ) THEN
            RAISE EXCEPTION 'TERM_UNLOCK_LOG_REQUIRED: temporary unlock must have a matching 72-hour log'
                USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.lock_state = 'LOCKED' THEN
        IF NEW.unlock_until IS NOT NULL THEN
            RAISE EXCEPTION 'TERM_LOCK_EXPIRY_FORBIDDEN' USING ERRCODE = '23514';
        END IF;
        IF OLD.lock_state = 'TEMP_UNLOCKED' AND EXISTS (
            SELECT 1 FROM app.term_unlock_log l
             WHERE l.tenant_id = OLD.tenant_id AND l.term_id = OLD.term_lock_id
               AND l.is_active
        ) THEN
            RAISE EXCEPTION 'TERM_RELOCK_LOG_REQUIRED: close the active unlock log before relocking'
                USING ERRCODE = '23514';
        END IF;
    ELSE
        RAISE EXCEPTION 'TERM_LOCK_STATE_INVALID' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_term_lock_immutability ON app.term_lock;
CREATE TRIGGER trg_term_lock_immutability
    BEFORE UPDATE ON app.term_lock
    FOR EACH ROW EXECUTE FUNCTION app.fn_term_lock_immutability();

CREATE OR REPLACE FUNCTION app.is_term_locked(p_term_id uuid)
RETURNS boolean LANGUAGE sql STABLE AS $$
    SELECT COALESCE((
        SELECT l.lock_state = 'LOCKED' OR l.unlock_until <= now()
          FROM app.term_lock l
         WHERE l.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
           AND l.term_lock_id = p_term_id
    ), true)
$$;
COMMENT ON FUNCTION app.is_term_locked(uuid) IS
    'T1 DB-42: fail-closed term-lock status for a term_lock_id in the current tenant context.';

CREATE OR REPLACE FUNCTION app.fn_relock_expired_terms(p_tenant_id uuid)
RETURNS integer LANGUAGE plpgsql AS $$
DECLARE v_lock record; v_now timestamptz; v_event_id uuid; v_payload jsonb; v_count integer := 0;
        v_system_user uuid := '00000000-0000-0000-0000-000000000000';
BEGIN
    IF p_tenant_id IS NULL
       OR NULLIF(current_setting('app.tenant_id', true), '')::uuid IS DISTINCT FROM p_tenant_id THEN
        RAISE EXCEPTION 'TENANT_CONTEXT_MISMATCH: relock requires matching tenant GUC'
            USING ERRCODE = '42501';
    END IF;
    v_now := clock_timestamp();
    FOR v_lock IN
        SELECT l.tenant_id, l.term_lock_id, l.institution_id
          FROM app.term_lock l
         WHERE l.tenant_id = p_tenant_id AND l.lock_state = 'TEMP_UNLOCKED'
           AND l.unlock_until <= v_now
         ORDER BY l.unlock_until FOR UPDATE SKIP LOCKED
    LOOP
        UPDATE app.term_unlock_log
           SET is_active = false, relocked_at = v_now, updated_at = v_now,
               updated_by = v_system_user, row_version = row_version + 1
         WHERE tenant_id = p_tenant_id AND term_id = v_lock.term_lock_id AND is_active;
        UPDATE app.term_lock
           SET lock_state = 'LOCKED', unlock_until = NULL, updated_at = v_now,
               updated_by = v_system_user, row_version = row_version + 1
         WHERE tenant_id = p_tenant_id AND term_lock_id = v_lock.term_lock_id;

        v_event_id := gen_random_uuid();
        INSERT INTO app.term_lock_event
            (tenant_id, term_lock_event_id, institution_id, term_lock_id, event_type,
             actor_user_id, reason, occurred_at, created_at, created_by, updated_at, updated_by)
        VALUES
            (p_tenant_id, v_event_id, v_lock.institution_id, v_lock.term_lock_id, 'AUTO_RELOCK',
             v_system_user, 'Automatically relocked after the 72-hour emergency unlock period',
             v_now, v_now, v_system_user, v_now, v_system_user);

        v_payload := jsonb_build_object('term_lock_id', v_lock.term_lock_id,
                                        'lock_state', 'LOCKED',
                                        'reason', '72-hour emergency unlock expired');
        INSERT INTO app.audit_outbox
            (tenant_id, audit_event_id, institution_id, occurred_at, actor_kind, operation,
             action_code, entity_type, entity_id, outcome, new_values, correlation_id,
             payload_hash, created_at, created_by, updated_at, updated_by)
        VALUES
            (p_tenant_id, gen_random_uuid(), v_lock.institution_id, v_now, 'SYSTEM', 'CONFIG_CHANGE',
             'TERM_AUTO_RELOCK', 'TERM_LOCK', v_lock.term_lock_id, 'SUCCESS', v_payload,
             v_event_id::text,
             encode(public.digest(convert_to(v_payload::text, 'UTF8'), 'sha256'), 'hex'),
             v_now, v_system_user, v_now, v_system_user);
        v_count := v_count + 1;
    END LOOP;
    RETURN v_count;
END;
$$;
REVOKE ALL ON FUNCTION app.fn_relock_expired_terms(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.fn_relock_expired_terms(uuid) TO edu_app;
GRANT EXECUTE ON FUNCTION app.is_term_locked(uuid) TO edu_app, edu_report;
COMMIT;
