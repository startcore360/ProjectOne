DO $$
DECLARE
    v_tenant_id UUID := 'bfec07a5-b51a-4080-84c3-f733942633f0';
    v_actor UUID := '00000000-0000-0000-0000-000000000001';
    v_univadmin_user_id UUID := '11111111-1111-1111-1111-111111111111';
    v_collegeadmin_user_id UUID := '22222222-2222-2222-2222-222222222222';
    v_teacher_user_id UUID := '91f64b1f-a7eb-49e0-887f-8700b44b8b88';
    
    v_rd_univ UUID;
    v_rd_college UUID;
    v_rd_teacher UUID;
BEGIN
    -- Ensure role definitions exist
    SELECT role_definition_id INTO v_rd_univ FROM app.role_definition WHERE tenant_id = v_tenant_id AND role_code = 'UNIVERSITY_ADMIN' LIMIT 1;
    IF v_rd_univ IS NULL THEN
        v_rd_univ := gen_random_uuid();
        INSERT INTO app.role_definition (tenant_id, role_definition_id, role_code, role_name, role_origin, role_level, base_standard_role, is_sensitive_role, status, created_by, updated_by)
        VALUES (v_tenant_id, v_rd_univ, 'UNIVERSITY_ADMIN', 'University Admin', 'STANDARD', 'UNIVERSITY', 'UNIVERSITY_ADMIN', true, 'ACTIVE', v_actor, v_actor);
    END IF;

    SELECT role_definition_id INTO v_rd_college FROM app.role_definition WHERE tenant_id = v_tenant_id AND role_code = 'COLLEGE_ADMIN' LIMIT 1;
    IF v_rd_college IS NULL THEN
        v_rd_college := gen_random_uuid();
        INSERT INTO app.role_definition (tenant_id, role_definition_id, role_code, role_name, role_origin, role_level, base_standard_role, is_sensitive_role, status, created_by, updated_by)
        VALUES (v_tenant_id, v_rd_college, 'COLLEGE_ADMIN', 'College Admin', 'STANDARD', 'COLLEGE', 'COLLEGE_ADMIN', false, 'ACTIVE', v_actor, v_actor);
    END IF;

    SELECT role_definition_id INTO v_rd_teacher FROM app.role_definition WHERE tenant_id = v_tenant_id AND role_code = 'CLASS_TEACHER' LIMIT 1;
    IF v_rd_teacher IS NULL THEN
        v_rd_teacher := gen_random_uuid();
        INSERT INTO app.role_definition (tenant_id, role_definition_id, role_code, role_name, role_origin, role_level, base_standard_role, is_sensitive_role, status, created_by, updated_by)
        VALUES (v_tenant_id, v_rd_teacher, 'CLASS_TEACHER', 'Class Teacher', 'STANDARD', 'SCHOOL', 'CLASS_TEACHER', false, 'ACTIVE', v_actor, v_actor);
    END IF;

    -- Univ Admin
    IF NOT EXISTS (SELECT 1 FROM app.user_account WHERE tenant_id = v_tenant_id AND user_account_id = v_univadmin_user_id) THEN
        INSERT INTO app.user_account (tenant_id, user_account_id, user_type, display_name, email, status, created_by, updated_by)
        VALUES (v_tenant_id, v_univadmin_user_id, 'STAFF', 'ABC University Admin', 'univadmin1@abc-university.test', 'ACTIVE', v_actor, v_actor);
        INSERT INTO app.user_identity_link (tenant_id, user_identity_link_id, user_account_id, identity_realm, identity_subject, username_kind, status, created_by, updated_by)
        VALUES (v_tenant_id, gen_random_uuid(), v_univadmin_user_id, 'abc-university', '207e87b6-5e2d-4e06-9ee0-31cbaf25fcb6', 'EMAIL', 'ACTIVE', v_actor, v_actor);
        INSERT INTO app.user_role_assignment (tenant_id, user_role_assignment_id, user_account_id, role_definition_id, scope_type, status, created_by, updated_by)
        VALUES (v_tenant_id, gen_random_uuid(), v_univadmin_user_id, v_rd_univ, 'TENANT', 'ACTIVE', v_actor, v_actor);
    END IF;

    -- College Admin
    IF NOT EXISTS (SELECT 1 FROM app.user_account WHERE tenant_id = v_tenant_id AND user_account_id = v_collegeadmin_user_id) THEN
        INSERT INTO app.user_account (tenant_id, user_account_id, user_type, display_name, email, status, created_by, updated_by)
        VALUES (v_tenant_id, v_collegeadmin_user_id, 'STAFF', 'ABC College Admin', 'collegeadmin1@abc-university.test', 'ACTIVE', v_actor, v_actor);
        INSERT INTO app.user_identity_link (tenant_id, user_identity_link_id, user_account_id, identity_realm, identity_subject, username_kind, status, created_by, updated_by)
        VALUES (v_tenant_id, gen_random_uuid(), v_collegeadmin_user_id, 'abc-university', 'a7b1cf71-21d6-4511-8afd-acf8c45bff79', 'EMAIL', 'ACTIVE', v_actor, v_actor);
        INSERT INTO app.user_role_assignment (tenant_id, user_role_assignment_id, user_account_id, role_definition_id, scope_type, status, created_by, updated_by)
        VALUES (v_tenant_id, gen_random_uuid(), v_collegeadmin_user_id, v_rd_college, 'TENANT', 'ACTIVE', v_actor, v_actor);
    END IF;

    -- Teacher role assignment
    IF NOT EXISTS (SELECT 1 FROM app.user_role_assignment WHERE tenant_id = v_tenant_id AND user_account_id = v_teacher_user_id AND role_definition_id = v_rd_teacher) THEN
        INSERT INTO app.user_role_assignment (tenant_id, user_role_assignment_id, user_account_id, role_definition_id, scope_type, status, created_by, updated_by)
        VALUES (v_tenant_id, gen_random_uuid(), v_teacher_user_id, v_rd_teacher, 'TENANT', 'ACTIVE', v_actor, v_actor);
    END IF;
END $$;
