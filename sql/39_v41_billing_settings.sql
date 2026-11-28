-- E3-S5: billing-owned due-days setting. This is deliberately not a security-policy setting.
CREATE TABLE IF NOT EXISTS platform.billing_settings (
 billing_settings_id integer NOT NULL DEFAULT 1,
 invoice_due_days integer NOT NULL DEFAULT 15,
 created_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(),
 updated_by uuid NOT NULL,
 row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_billing_settings PRIMARY KEY (billing_settings_id),
 CONSTRAINT ck_billing_settings_singleton CHECK (billing_settings_id=1),
 CONSTRAINT ck_billing_settings_invoice_due_days CHECK (invoice_due_days BETWEEN 1 AND 365),
 CONSTRAINT ck_billing_settings_row_version CHECK (row_version>=1),
 CONSTRAINT ck_billing_settings_upd CHECK (updated_at>=created_at)
);
INSERT INTO platform.billing_settings(billing_settings_id,invoice_due_days,created_by,updated_by)
VALUES(1,15,'00000000-0000-0000-0000-000000000000','00000000-0000-0000-0000-000000000000')
ON CONFLICT (billing_settings_id) DO NOTHING;
DROP TRIGGER IF EXISTS trg_touch ON platform.billing_settings;
CREATE TRIGGER trg_touch BEFORE UPDATE ON platform.billing_settings
 FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();
GRANT SELECT, INSERT, UPDATE ON platform.billing_settings TO edu_platform;
COMMENT ON TABLE platform.billing_settings IS 'Billing configuration; not part of security policy [E3-S5].';
COMMENT ON COLUMN platform.billing_settings.invoice_due_days IS 'Calendar days from invoice sent_at to due_at; default 15 [E3-S5].';
