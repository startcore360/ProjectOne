-- Contract tests for migration 38: approval/renewal columns and DRAFT-only invoice-line guard.
\set ON_ERROR_STOP 1
BEGIN;
DO $$
DECLARE
 a uuid:='00000000-0000-0000-0000-000000003801'; p uuid:='00000000-0000-0000-0000-000000003802';
 t uuid:='00000000-0000-0000-0000-000000003803'; i uuid:='00000000-0000-0000-0000-000000003804';
 l uuid:='00000000-0000-0000-0000-000000003805'; n integer; blocked boolean;
BEGIN
 SELECT count(*) INTO n FROM information_schema.columns WHERE (table_schema,table_name,column_name) IN
 (('platform','invoice','approved_by'),('platform','invoice','approved_at'),('platform','invoice','due_at'),('platform','tenant','next_renewal_at'),('platform','tenant','pending_plan_id'));
 IF n<>5 THEN RAISE EXCEPTION 'E3_INVOICE_APPROVAL_RENEWAL_COLUMNS_MISSING: %',n; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='platform.invoice'::regclass AND conname='ck_invoice_status')
 OR NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='platform.invoice'::regclass AND conname='ck_invoice_r1')
 OR NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='platform.invoice'::regclass AND conname='ck_invoice_r5') THEN RAISE EXCEPTION 'E3_EXISTING_INVOICE_CHECKS_NOT_PRESERVED'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='platform.invoice_line'::regclass AND tgname='trg_invoice_line_draft_guard' AND NOT tgisinternal) THEN RAISE EXCEPTION 'E3_INVOICE_LINE_DRAFT_TRIGGER_MISSING'; END IF;
 INSERT INTO platform.plan(plan_id,plan_name,price_amount,status,created_by,updated_by) VALUES(p,'E3 migration test plan',1000,'ACTIVE',a,a);
 INSERT INTO platform.tenant(tenant_id,tenant_name,tenant_type,plan_id,state,isolation_mode,subdomain,created_by,updated_by) VALUES(t,'E3 migration test tenant','STANDALONE_SCHOOL',p,'ACTIVE','SHARED','e3guard',a,a);
 INSERT INTO platform.invoice(invoice_id,tenant_id,invoice_number,period_start,period_end,subtotal_amount,discount_amount,total_amount,currency_code,status,created_by,updated_by) VALUES(i,t,'E3-GUARD-TEST',DATE '2026-01-01',DATE '2026-01-31',10,0,10,'INR','DRAFT',a,a);
 INSERT INTO platform.invoice_line(invoice_line_id,invoice_id,line_type,description,quantity,unit_amount,line_amount,created_by,updated_by) VALUES(l,i,'OTHER','Draft guard test',1,10,10,a,a);
 UPDATE platform.invoice_line SET quantity=2,line_amount=20 WHERE invoice_line_id=l;
 UPDATE platform.invoice SET status='SENT',sent_at=now() WHERE invoice_id=i;
 blocked:=false;
 BEGIN UPDATE platform.invoice_line SET quantity=3,line_amount=30 WHERE invoice_line_id=l;
 EXCEPTION WHEN SQLSTATE 'P0001' THEN IF SQLERRM<>'INVOICE_NOT_DRAFT' THEN RAISE; END IF; blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'E3_SENT_INVOICE_LINE_UPDATE_WAS_ALLOWED'; END IF;
 blocked:=false;
 BEGIN DELETE FROM platform.invoice_line WHERE invoice_line_id=l;
 EXCEPTION WHEN SQLSTATE 'P0001' THEN IF SQLERRM<>'INVOICE_NOT_DRAFT' THEN RAISE; END IF; blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'E3_SENT_INVOICE_LINE_DELETE_WAS_ALLOWED'; END IF;
 blocked:=false;
 BEGIN INSERT INTO platform.invoice_line(invoice_line_id,invoice_id,line_type,description,quantity,unit_amount,line_amount,created_by,updated_by) VALUES('00000000-0000-0000-0000-000000003806',i,'OTHER','Rejected sent line',1,1,1,a,a);
 EXCEPTION WHEN SQLSTATE 'P0001' THEN IF SQLERRM<>'INVOICE_NOT_DRAFT' THEN RAISE; END IF; blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'E3_SENT_INVOICE_LINE_INSERT_WAS_ALLOWED'; END IF;
END $$;
ROLLBACK;
