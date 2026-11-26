DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['id_card_template','id_card_issuance','id_card_replacement','certificate_template','certificate_request','certificate_issuance','department_equipment_clearance'] LOOP
  IF to_regclass('app.'||t) IS NULL THEN RAISE EXCEPTION 'T9_DB_71 missing app.%',t; END IF;
  IF NOT (SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid=to_regclass('app.'||t)) THEN RAISE EXCEPTION 'T9_DB_71 RLS not forced: %',t; END IF;
  IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename=t AND policyname='p_tenant') THEN RAISE EXCEPTION 'T9_DB_71 tenant policy missing: %',t; END IF;
 END LOOP;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.id_card_issuance'::regclass AND contype='c' AND pg_get_constraintdef(oid) LIKE '%qr_hash%') THEN RAISE EXCEPTION 'T9_DB_71 QR digest constraint missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='certificate_issuance_verification_code_hash_key') THEN RAISE EXCEPTION 'T9_DB_71 unique certificate verification digest missing'; END IF;
 IF to_regprocedure('app.fn_no_dues_department_clearance(uuid,uuid,uuid)') IS NULL THEN
  RAISE EXCEPTION 'T9_DB_71 tenant-bound departmental No-Dues helper missing';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='department_equipment_clearance' AND policyname='p_institution' AND permissive='RESTRICTIVE') THEN
  RAISE EXCEPTION 'T9_DB_71 department clearance institution isolation policy missing';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.department_equipment_clearance'::regclass AND contype='f') THEN
  RAISE EXCEPTION 'T9_DB_71 department clearance parent references missing';
 END IF;
END $$;
