DO $$ BEGIN IF EXISTS(SELECT 1 FROM platform.approval_delegation) THEN RAISE EXCEPTION 'PLATFORM_APPROVAL_DELEGATIONS_EXIST'; END IF; END $$;
DROP TRIGGER IF EXISTS trg_platform_approval_delegation_dpo ON platform.approval_delegation;
DROP FUNCTION IF EXISTS platform.fn_validate_approval_delegation_dpo();
DROP TABLE platform.approval_delegation;
