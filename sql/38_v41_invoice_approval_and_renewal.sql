-- E3-S3 / E3-S5 / E3-S6: invoice approval, due dates and tenant renewals.
-- Additive and repeatable. Preserve the existing ck_invoice_* constraints.
ALTER TABLE platform.invoice ADD COLUMN IF NOT EXISTS approved_by uuid, ADD COLUMN IF NOT EXISTS approved_at timestamptz, ADD COLUMN IF NOT EXISTS due_at date;
ALTER TABLE platform.tenant ADD COLUMN IF NOT EXISTS next_renewal_at date, ADD COLUMN IF NOT EXISTS pending_plan_id uuid;
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='platform.invoice'::regclass AND conname='ck_invoice_approval_pair') THEN
  ALTER TABLE platform.invoice ADD CONSTRAINT ck_invoice_approval_pair CHECK ((approved_by IS NULL) = (approved_at IS NULL));
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='platform.tenant'::regclass AND conname='fk_tenant_pending_plan_id') THEN
  ALTER TABLE platform.tenant ADD CONSTRAINT fk_tenant_pending_plan_id FOREIGN KEY (pending_plan_id) REFERENCES platform.plan(plan_id) ON DELETE RESTRICT;
 END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_invoice_due_at_unpaid ON platform.invoice(due_at) WHERE status='SENT';
CREATE INDEX IF NOT EXISTS ix_tenant_next_renewal_at ON platform.tenant(next_renewal_at) WHERE state='ACTIVE' AND next_renewal_at IS NOT NULL;
-- The baseline schema documents this guard but did not install it. Lock the parent invoice
-- to serialize line edits against approval/send status changes.
CREATE OR REPLACE FUNCTION platform.fn_invoice_line_draft_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_invoice_id uuid; v_status text;
BEGIN
 IF TG_OP='UPDATE' AND NEW.invoice_id IS DISTINCT FROM OLD.invoice_id THEN
  RAISE EXCEPTION USING ERRCODE='P0001', MESSAGE='INVOICE_LINE_INVOICE_IMMUTABLE';
 END IF;
 v_invoice_id := CASE WHEN TG_OP='DELETE' THEN OLD.invoice_id ELSE NEW.invoice_id END;
 SELECT status INTO v_status FROM platform.invoice WHERE invoice_id=v_invoice_id FOR UPDATE;
 IF NOT FOUND OR v_status IS DISTINCT FROM 'DRAFT' THEN
  RAISE EXCEPTION USING ERRCODE='P0001', MESSAGE='INVOICE_NOT_DRAFT';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END; $$;
DROP TRIGGER IF EXISTS trg_invoice_line_draft_guard ON platform.invoice_line;
CREATE TRIGGER trg_invoice_line_draft_guard BEFORE INSERT OR UPDATE OR DELETE ON platform.invoice_line
 FOR EACH ROW EXECUTE FUNCTION platform.fn_invoice_line_draft_guard();
COMMENT ON COLUMN platform.invoice.approved_by IS 'Product Owner platform user who approved the invoice [E3-S3].';
COMMENT ON COLUMN platform.invoice.approved_at IS 'Server timestamp of Product Owner approval [E3-S3].';
COMMENT ON COLUMN platform.invoice.due_at IS 'Payment due date, derived from sent_at plus configured billing due-days [E3-S5].';
COMMENT ON COLUMN platform.tenant.next_renewal_at IS 'Next subscription renewal date used by billing cycles and revenue reminders [E3-S1, E3-S7].';
COMMENT ON COLUMN platform.tenant.pending_plan_id IS 'Plan scheduled for application at the next renewal [E3-S6].';
