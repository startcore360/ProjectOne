-- Staff login aliases: allow verified official and personal email addresses to resolve
-- to one tenant account without creating duplicate Keycloak identities.
CREATE TABLE IF NOT EXISTS app.user_login_alias (
    tenant_id uuid NOT NULL,
    user_login_alias_id uuid NOT NULL,
    user_account_id uuid NOT NULL,
    email text NOT NULL,
    is_verified boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_user_login_alias PRIMARY KEY (tenant_id, user_login_alias_id),
    CONSTRAINT uq_user_login_alias_email UNIQUE (tenant_id, email),
    CONSTRAINT uq_user_login_alias_account_email UNIQUE (tenant_id, user_account_id, email),
    CONSTRAINT fk_user_login_alias_account FOREIGN KEY (tenant_id, user_account_id)
        REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE CASCADE,
    CONSTRAINT ck_user_login_alias_email CHECK (email = lower(btrim(email)) AND email ~ '^[^@[:space:]]+@[^@[:space:]]+\\.[^@[:space:]]+$'),
    CONSTRAINT ck_user_login_alias_row_version CHECK (row_version >= 1)
);

CREATE OR REPLACE FUNCTION app.fn_user_login_alias_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM app.user_account a
         WHERE a.tenant_id=NEW.tenant_id
           AND lower(btrim(a.email))=NEW.email
           AND a.user_account_id<>NEW.user_account_id
    ) THEN
        RAISE EXCEPTION 'LOGIN_EMAIL_IN_USE: email is already a primary login for another account'
            USING ERRCODE='23505';
    END IF;
    IF EXISTS (
        SELECT 1 FROM app.user_login_alias x
         WHERE x.tenant_id=NEW.tenant_id AND x.email=NEW.email
           AND x.user_login_alias_id<>NEW.user_login_alias_id
           AND x.user_account_id<>NEW.user_account_id
    ) THEN
        RAISE EXCEPTION 'LOGIN_EMAIL_IN_USE: email is already linked to another account'
            USING ERRCODE='23505';
    END IF;
    RETURN NEW;
END$$;

DROP TRIGGER IF EXISTS trg_user_login_alias_guard ON app.user_login_alias;
CREATE TRIGGER trg_user_login_alias_guard
    BEFORE INSERT OR UPDATE OF tenant_id, user_account_id, email
    ON app.user_login_alias FOR EACH ROW
    EXECUTE FUNCTION app.fn_user_login_alias_guard();

DROP TRIGGER IF EXISTS trg_touch ON app.user_login_alias;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.user_login_alias
    FOR EACH ROW EXECUTE FUNCTION app.fn_touch();

ALTER TABLE app.user_login_alias ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.user_login_alias FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.user_login_alias;
CREATE POLICY p_tenant ON app.user_login_alias
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON app.user_login_alias TO edu_app;
GRANT SELECT ON app.user_login_alias TO edu_report;
