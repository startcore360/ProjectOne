package com.edu360.api.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL integration coverage for the PO-T05 SECURITY DEFINER predicate. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.*")
class GrievanceComplaintGuardIT {
    @Autowired JdbcTemplate jdbc;

    private final UUID tenant = UUID.randomUUID();
    private final UUID institutionA = UUID.randomUUID();
    private final UUID institutionB = UUID.randomUUID();
    private final UUID iccUser = UUID.randomUUID();
    private final UUID principalUser = UUID.randomUUID();
    private final UUID adminUser = UUID.randomUUID();
    private final UUID hodUser = UUID.randomUUID();
    private final UUID accusedUser = UUID.randomUUID();
    private final UUID customUser = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final UUID accusedStaffId = UUID.randomUUID();
    private final UUID hodStaffId = UUID.randomUUID();
    private final UUID accusedAssignmentId = UUID.randomUUID();
    private final UUID hodAssignmentId = UUID.randomUUID();

    @BeforeEach
    void seedFixture() {
        setTenant();
        insertUser(iccUser, "GOVERNANCE");
        insertUser(principalUser, "GOVERNANCE");
        insertUser(adminUser, "GOVERNANCE");
        insertUser(hodUser, "STAFF");
        insertUser(accusedUser, "STAFF");
        insertUser(customUser, "GOVERNANCE");

        UUID iccRole = insertRole("INTERNAL_COMPLAINTS_COMMITTEE", "STANDARD", "INTERNAL_COMPLAINTS_COMMITTEE", null);
        UUID principalRole = insertRole("PRINCIPAL_COLLEGE", "STANDARD", "PRINCIPAL_COLLEGE", null);
        UUID adminRole = insertRole("COLLEGE_ADMIN", "STANDARD", "COLLEGE_ADMIN", null);
        UUID customRole = insertRole("CUSTOM_ICC_CLONE", "CUSTOM", null, "INSTITUTION");

        assign(iccUser, iccRole, institutionA);
        assign(principalUser, principalRole, institutionA);
        assign(adminUser, adminRole, institutionA);
        assign(hodUser, iccRole, institutionA); // HOD is otherwise in the ICC allowlist.
        assign(accusedUser, iccRole, institutionA); // accused is otherwise in the ICC allowlist.
        assign(customUser, customRole, institutionA);

        jdbc.update("""
            INSERT INTO app.staff_profile(tenant_id,staff_profile_id,user_account_id,staff_code,full_name,created_by,updated_by)
            VALUES (?,?,?,?,?,?,?)
            """, tenant, accusedStaffId, accusedUser, "ACC-01", "Accused Faculty", accusedUser, accusedUser);
        jdbc.update("""
            INSERT INTO app.staff_profile(tenant_id,staff_profile_id,user_account_id,staff_code,full_name,created_by,updated_by)
            VALUES (?,?,?,?,?,?,?)
            """, tenant, hodStaffId, hodUser, "HOD-01", "Department HOD", hodUser, hodUser);
        jdbc.update("""
            INSERT INTO app.department(tenant_id,department_id,institution_id,institution_type,department_code,
              department_name,status,created_by,updated_by)
            VALUES (?,?,?,'COLLEGE','T9TEST','T9 Test Department','ACTIVE',?,?)
            """, tenant, departmentId, institutionA, hodUser, hodUser);
        jdbc.update("""
            INSERT INTO app.staff_assignment(tenant_id,staff_assignment_id,staff_profile_id,institution_id,
              department_id,designation,effective_from,status,created_by,updated_by)
            VALUES (?,?,?,?,?,'Faculty',current_date,'ACTIVE',?,?)
            """, tenant, accusedAssignmentId, accusedStaffId, institutionA, departmentId, accusedUser, accusedUser);
        jdbc.update("""
            INSERT INTO app.staff_assignment(tenant_id,staff_assignment_id,staff_profile_id,institution_id,
              department_id,designation,effective_from,status,created_by,updated_by)
            VALUES (?,?,?,?,?,'Head of Department',current_date,'ACTIVE',?,?)
            """, tenant, hodAssignmentId, hodStaffId, institutionA, departmentId, hodUser, hodUser);
        jdbc.update("UPDATE app.department SET hod_staff_assignment_id=?,updated_by=? WHERE tenant_id=? AND department_id=?",
                hodAssignmentId, hodUser, tenant, departmentId);
    }

    @Test
    void onlyIccAndPrincipalPassAndGenericAdminAndCustomRoleFail() {
        setUser(iccUser);
        assertThat(guard(null, institutionA)).isTrue();

        setUser(principalUser);
        assertThat(guard(null, institutionA)).isTrue();

        setUser(adminUser);
        assertThat(guard(null, institutionA)).isFalse();

        setUser(customUser);
        assertThat(guard(null, institutionA)).isFalse();
    }

    @Test
    void accusedPartyAndImmediateHodAreDeniedEvenWhenTheirActiveRoleIsIcc() {
        setUser(accusedUser);
        assertThat(guard(accusedUser, institutionA)).isFalse();

        setUser(hodUser);
        assertThat(guard(accusedUser, institutionA)).isFalse();
    }

    @Test
    void activeIccRoleCannotCrossInstitutionBoundary() {
        setUser(iccUser);
        assertThat(guard(null, institutionA)).isTrue();
        assertThat(guard(null, institutionB)).isFalse();
    }

    @Test
    void missingTenantOrUserContextFailsClosed() {
        jdbc.queryForObject("SELECT set_config('app.user_id','',true)", String.class);
        assertThat(guard(null, institutionA)).isFalse();
        jdbc.queryForObject("SELECT set_config('app.user_id',?,true)", String.class, iccUser.toString());
        jdbc.queryForObject("SELECT set_config('app.tenant_id','',true)", String.class);
        assertThat(guard(null, institutionA)).isFalse();
    }

    private boolean guard(UUID accused, UUID institution) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT app.fn_complaint_guard(?,?,?)", Boolean.class, tenant, accused, institution));
    }

    private void setTenant() {
        jdbc.queryForObject("SELECT set_config('app.tenant_id',?,true)", String.class, tenant.toString());
    }

    private void setUser(UUID user) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id',?,true)", String.class, tenant.toString());
        jdbc.queryForObject("SELECT set_config('app.user_id',?,true)", String.class, user.toString());
    }

    private void insertUser(UUID user, String type) {
        jdbc.update("""
            INSERT INTO app.user_account(tenant_id,user_account_id,user_type,display_name,status,created_by,updated_by)
            VALUES (?,?,?,?,'ACTIVE',?,?)
            """, tenant, user, type, "T9 test " + type, user, user);
    }

    private UUID insertRole(String code, String origin, String base, String customScope) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.role_definition(tenant_id,role_definition_id,role_code,role_name,role_origin,
              role_level,base_standard_role,custom_scope,is_sensitive_role,status,created_by,updated_by)
            VALUES (?,?,?,?,?,'COLLEGE',?,?,true,'ACTIVE',?,?)
            """, tenant, id, code, "T9 test " + code, origin, base, customScope, iccUser, iccUser);
        return id;
    }

    private void assign(UUID user, UUID role, UUID institution) {
        UUID assignment = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.user_role_assignment(tenant_id,user_role_assignment_id,user_account_id,role_definition_id,
              scope_type,institution_id,effective_from,status,created_by,updated_by)
            VALUES (?,?,?,?,'INSTITUTION',?,now(),'ACTIVE',?,?)
            """, tenant, assignment, user, role, institution, user, user);
        jdbc.update("""
            INSERT INTO app.user_active_role(tenant_id,user_account_id,user_role_assignment_id)
            VALUES (?,?,?)
            """, tenant, user, assignment);
    }
}
