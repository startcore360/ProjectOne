package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.rbac.dto.AssignRoleRequest;
import com.edu360.api.tenant.TenantContext;

/**
 * Q-02 (DB-A11): app.role_definition.status may be DRAFT, ACTIVE, INACTIVE, RETIRED or ARCHIVED
 * (ck_role_definition_status, sql/16). Only ACTIVE grants rights. Real PermissionEvaluatorService
 * over real role/action rows: the role below holds the action in app.role_action, and the user's
 * one active assignment points at it, so the status is the only thing that can refuse.
 */
class RoleStatusQ02Test {

    private RbacTestWorld world;
    private final UUID collegeA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        world = new RbacTestWorld();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void assertRoleInStatusGrantsNothing(RoleDefinitionStatus status) {
        RoleDefinition role = world.customRole("HELPER_" + status, "COLLEGE", collegeA, "USER_VIEW");
        UUID user = world.user("Helper " + status);
        world.assign(user, role, ScopeType.INSTITUTION, collegeA, null);
        RbacPrincipal p = world.principal(user);

        // Control: while ACTIVE, the same assignment grants the action.
        assertThat(world.evaluator.can(p, "USER_VIEW", RbacScope.institution(collegeA), RbacTarget.NONE).allowed()).isTrue();

        role.setStatus(status);
        PermissionDecision decision = world.evaluator.can(p, "USER_VIEW", RbacScope.institution(collegeA), RbacTarget.NONE);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ROLE_NOT_ACTIVE");
        assertThatThrownBy(() -> world.evaluator.authorize(p, "USER_VIEW", RbacScope.institution(collegeA), RbacTarget.NONE))
                .isInstanceOf(UnauthorizedException.class);

        // ...and such a role cannot be newly given to anyone.
        UUID admin = world.user("Admin " + status);
        world.assign(admin, "COLLEGE_ADMIN", ScopeType.INSTITUTION, collegeA, null);
        UUID target = world.user("Target " + status);
        RoleAssignmentService assignments = new RoleAssignmentService(world.userRoleAssignmentRepository,
                world.roleDefinitionRepository, world.roleActionRepository, world.standardRoleRepository,
                mock(StandardRoleActionRepository.class), world.userAccountRepository,
                mock(UserIdentityLinkRepository.class), world.evaluator, mock(KeycloakAdminClient.class), world.auditLogService);
        int before = world.assignments.size();
        assertThatThrownBy(() -> assignments.assignRole(world.principal(admin), target, new AssignRoleRequest(
                role.getId().getRoleDefinitionId(), null, ScopeType.INSTITUTION, collegeA, null, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("only an ACTIVE role");
        assertThat(world.assignments).hasSize(before);
    }

    @Test
    @DisplayName("REG-Q02-1: a DRAFT role grants nothing")
    void regQ02_1_draftRoleGrantsNothing() {
        assertRoleInStatusGrantsNothing(RoleDefinitionStatus.DRAFT);
    }

    @Test
    @DisplayName("REG-Q02-2: an INACTIVE role grants nothing")
    void regQ02_2_inactiveRoleGrantsNothing() {
        assertRoleInStatusGrantsNothing(RoleDefinitionStatus.INACTIVE);
    }

    @Test
    @DisplayName("REG-Q02-3: an ARCHIVED role grants nothing")
    void regQ02_3_archivedRoleGrantsNothing() {
        assertRoleInStatusGrantsNothing(RoleDefinitionStatus.ARCHIVED);
    }
}
