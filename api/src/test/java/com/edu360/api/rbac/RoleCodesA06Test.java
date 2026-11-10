package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.tenant.TenantContext;

/**
 * A-06 (Appendix C X4, X5): the School Principal role code is SCHOOL_PRINCIPAL everywhere (the
 * old PRINCIPAL_SCHOOL code does not exist in ref.standard_role). Real rows, real evaluator, real
 * UserGovernanceGuard.
 */
class RoleCodesA06Test {

    private RbacTestWorld world;
    private final UUID schoolA = UUID.randomUUID();
    private final UUID schoolB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        world = new RbacTestWorld();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("REG-A06-1: School Principal gets top-post coverage in their own school only")
    void regA06_1_schoolPrincipalCoverageOwnSchoolOnly() {
        assertThat(RbacTestWorld.STANDARD_ROLES).containsKey("SCHOOL_PRINCIPAL").doesNotContainKey("PRINCIPAL_SCHOOL");
        assertThat(RbacTestWorld.standardRoleHolds("SCHOOL_PRINCIPAL", "STRUCTURE_MANAGE")).isTrue();

        UUID principal = world.user("School Principal A");
        world.assign(principal, "SCHOOL_PRINCIPAL", ScopeType.INSTITUTION, schoolA, null);
        world.signIn(principal);
        RbacPrincipal p = world.principal(principal);

        // Covers a unit below the school (a narrower scope than the assignment) in their own school.
        PermissionDecision ownSchool = world.evaluator.can(p, "STRUCTURE_MANAGE",
                RbacScope.department(schoolA, UUID.randomUUID()), RbacTarget.NONE);
        assertThat(ownSchool.allowed()).isTrue();

        // Never in another school.
        PermissionDecision otherSchool = world.evaluator.can(p, "STRUCTURE_MANAGE",
                RbacScope.department(schoolB, UUID.randomUUID()), RbacTarget.NONE);
        assertThat(otherSchool.allowed()).isFalse();
        assertThat(otherSchool.reasonCode()).isEqualTo("NO_ACTIVE_ROLE_IN_SCOPE");
        assertThat(world.evaluator.can(p, "STRUCTURE_MANAGE", RbacScope.institution(schoolB), RbacTarget.NONE).allowed()).isFalse();

        // Never the tenant as a whole, and never a locked action through coverage.
        assertThat(world.evaluator.can(p, "STRUCTURE_MANAGE", RbacScope.tenant(), RbacTarget.NONE).allowed()).isFalse();
        PermissionDecision locked = world.evaluator.can(p, "PAYROLL_APPROVE_TOTALS",
                RbacScope.department(schoolA, UUID.randomUUID()), RbacTarget.NONE);
        assertThat(locked.allowed()).isFalse();
        assertThat(locked.reasonCode()).isEqualTo("ACTION_LOCKED");
    }

    @Test
    @DisplayName("REG-A06-2: School Principal is protected by the governance guard from a School Admin edit")
    void regA06_2_schoolPrincipalProtectedFromSchoolAdmin() {
        UUID schoolAdmin = world.user("School Admin A");
        world.assign(schoolAdmin, "SCHOOL_ADMIN", ScopeType.INSTITUTION, schoolA, null);
        UUID schoolPrincipal = world.user("School Principal A");
        world.assign(schoolPrincipal, "SCHOOL_PRINCIPAL", ScopeType.INSTITUTION, schoolA, null);
        UUID teacher = world.user("Teacher A");
        world.assign(teacher, "SUBJECT_TEACHER", ScopeType.INSTITUTION, schoolA, null);

        UserGovernanceGuard guard = new UserGovernanceGuard(world.userRoleAssignmentRepository, world.roleDefinitionRepository);
        assertThat(UserGovernanceGuard.TOP_GOVERNANCE_ROLES).contains("SCHOOL_PRINCIPAL").doesNotContain("PRINCIPAL_SCHOOL");

        assertThatThrownBy(() -> guard.checkTargetUserAuthority(world.tenantId, schoolAdmin, schoolPrincipal, schoolA))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("governance authority");
        // Control: the same School Admin may manage a teacher of the school.
        assertThatCode(() -> guard.checkTargetUserAuthority(world.tenantId, schoolAdmin, teacher, schoolA))
                .doesNotThrowAnyException();
    }
}
