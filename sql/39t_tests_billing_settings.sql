-- E3-S5 billing settings migration contract tests.
\set ON_ERROR_STOP 1
BEGIN;
DO $$
DECLARE v_days integer;
BEGIN
 SELECT invoice_due_days INTO v_days FROM platform.billing_settings WHERE billing_settings_id=1;
 IF v_days IS DISTINCT FROM 15 THEN RAISE EXCEPTION 'E3_BILLING_DUE_DAYS_DEFAULT_EXPECTED_15_GOT_%',v_days; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='platform.billing_settings'::regclass AND conname='ck_billing_settings_invoice_due_days')
 THEN RAISE EXCEPTION 'E3_BILLING_DUE_DAYS_CONSTRAINT_MISSING'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='platform.billing_settings'::regclass AND tgname='trg_touch' AND NOT tgisinternal)
 THEN RAISE EXCEPTION 'E3_BILLING_SETTINGS_TOUCH_TRIGGER_MISSING'; END IF;
 BEGIN
  UPDATE platform.billing_settings SET invoice_due_days=0 WHERE billing_settings_id=1;
  RAISE EXCEPTION 'E3_BILLING_DUE_DAYS_ZERO_WAS_ALLOWED';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
END $$;
ROLLBACK;
