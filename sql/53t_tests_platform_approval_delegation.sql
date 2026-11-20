\set ON_ERROR_STOP 1
BEGIN;
DO $$
DECLARE dpo uuid:='00000000-0000-0000-0000-000000005301'; admin_user uuid:='00000000-0000-0000-0000-000000005302'; blocked boolean:=false;
BEGIN
 INSERT INTO platform.platform_user(platform_user_id,email,full_name,platform_role,status,mfa_required,identity_subject,created_by,updated_by)
 VALUES(dpo,'dpo-e5s7@example.test','DPO E5S7','DATA_PROTECTION_OFFICER','ACTIVE',true,'e5s7-dpo',dpo,dpo),(admin_user,'admin-e5s7@example.test','Admin E5S7','PLATFORM_ADMIN','ACTIVE',true,'e5s7-admin',admin_user,admin_user);
 BEGIN INSERT INTO platform.approval_delegation(delegation_id,delegator_user_id,delegate_user_id,from_date,to_date,created_by,updated_by) VALUES(gen_random_uuid(),dpo,admin_user,'2026-11-20','2026-11-21',dpo,dpo);
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'E5_S7_NON_DPO_DELEGATE_ACCEPTED'; END IF;
END $$;
ROLLBACK;
