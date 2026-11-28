-- Roll back E3 invoice approval and renewal foundation (migration 38).
DROP TRIGGER IF EXISTS trg_invoice_line_draft_guard ON platform.invoice_line;
DROP FUNCTION IF EXISTS platform.fn_invoice_line_draft_guard();
DROP INDEX IF EXISTS platform.ix_invoice_due_at_unpaid;
DROP INDEX IF EXISTS platform.ix_tenant_next_renewal_at;
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS fk_tenant_pending_plan_id;
ALTER TABLE platform.invoice DROP CONSTRAINT IF EXISTS ck_invoice_approval_pair;
ALTER TABLE platform.invoice DROP COLUMN IF EXISTS approved_by, DROP COLUMN IF EXISTS approved_at, DROP COLUMN IF EXISTS due_at;
ALTER TABLE platform.tenant DROP COLUMN IF EXISTS next_renewal_at, DROP COLUMN IF EXISTS pending_plan_id;
