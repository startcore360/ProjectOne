package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLog;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.dto.CreateCustomRoleRequest;
import com.edu360.api.rbac.dto.RoleDetailResponse;
import com.edu360.api.rbac.dto.UpdateCustomRoleRequest;
import com.edu360.api.rbac.dto.UpdateRoleActionSetRequest;
import com.edu360.api.tenant.TenantContext;

/**
 * A-03 (C7, CR-01, CR-03, CR-04, CR-10, Addendum rule 11): custom roles fully guarded. Real
 * PermissionEvaluatorService and RoleCustomService over real role/action rows.
 */
class CustomRoleA03Test {

    private RbacTestWorld world;
    private RoleCustomController controller;
    private final UUID collegeA = UUID.randomUUID();
    private final UUID collegeB = UUID.randomUUID();
    private UUID collegeAdminA;

    @BeforeEach
    void setUp() {
        world = new RbacTestWorld();
        RoleCustomService service = new RoleCustomService(world.roleDefinitionRepository, world.roleActionRepository,
                world.actionCatalogRepository, world.evaluator, world.auditLogService);
        controller = new RoleCustomController(service);
        collegeAdminA = world.user("College Admin A");
        world.assign(collegeAdminA, "COLLEGE_ADMIN", ScopeType.INSTITUTION, collegeA, null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Authentication auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                new TenantPrincipal(world.tenantId, userId, UserType.STAFF, "user", false), null, List.of());
    }

    private CreateCustomRoleRequest create(String code, String... actions) {
        return new CreateCustomRoleRequest(code, "Custom " + code, RoleLevel.COLLEGE, CustomScope.INSTITUTION, List.of(actions));
    }

    private int customRolesSaved() {
        return (int) world.roles.values().stream().filter(r -> r.getRoleOrigin() == RoleOrigin.CUSTOM).count();
    }

    private void assertGuard(Runnable call, String code) {
        int roles = customRolesSaved();
        int actions = world.roleActions.size();
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(e -> ((BusinessRuleViolationException) e).getErrorCode())
                .isEqualTo(code);
        assertThat(customRolesSaved()).as("no role saved").isEqualTo(roles);
        assertThat(world.roleActions).as("no action saved").hasSize(actions);
    }

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-ALLOW: College Admin at institution scope creates a role with actions they hold: 201")
    void actRoleCustomManageAllow() {
        assertThat(RbacTestWorld.standardRoleHolds("COLLEGE_ADMIN", "USER_VIEW")).isTrue();
        assertThat(RbacTestWorld.standardRoleHolds("COLLEGE_ADMIN", "STRUCTURE_MANAGE")).isTrue();
        world.signIn(collegeAdminA);

        ResponseEntity<RoleDetailResponse> response = controller.createCustomRole(world.tenantId,
                create("EXAM_CELL_HELPER", "USER_VIEW", "STRUCTURE_MANAGE"), auth(collegeAdminA));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RoleDefinition saved = world.roles.get(response.getBody().roleDefinitionId());
        assertThat(saved.getRoleOrigin()).isEqualTo(RoleOrigin.CUSTOM);
        assertThat(saved.getCustomScope()).isEqualTo(CustomScope.INSTITUTION);
        assertThat(saved.getOwnerLevel()).isEqualTo("COLLEGE");
        assertThat(saved.getOwnerInstitutionId()).isEqualTo(collegeA);
        assertThat(world.actionsOf(saved.getId().getRoleDefinitionId()))
                .extracting(RoleAction::getActionCode).containsExactlyInAnyOrder("USER_VIEW", "STRUCTURE_MANAGE");

        AuditLog row = world.auditRows.get(world.auditRows.size() - 1);
        assertThat(row.getEventType()).isEqualTo(AuditEventType.CUSTOM_ROLE_CREATED);
        assertThat(row.getActorRoleCode()).isEqualTo("COLLEGE_ADMIN");
        assertThat(row.getScopeType()).isEqualTo("INSTITUTION");
        assertThat(row.getInstitutionId()).isEqualTo(collegeA);
    }

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-DENY: a Teacher gives 403")
    void actRoleCustomManageDeny() {
        UUID teacher = world.user("Teacher");
        world.assign(teacher, "SUBJECT_TEACHER", ScopeType.INSTITUTION, collegeA, null);
        assertThat(RbacTestWorld.standardRoleHolds("SUBJECT_TEACHER", "ROLE_CUSTOM_MANAGE")).isFalse();
        world.signIn(teacher);
        int roles = customRolesSaved();

        assertThatThrownBy(() -> controller.createCustomRole(world.tenantId, create("TEACHER_ROLE", "USER_VIEW"), auth(teacher)))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(customRolesSaved()).isEqualTo(roles);
        assertThat(world.auditRows).noneMatch(r -> r.getEventType() == AuditEventType.CUSTOM_ROLE_CREATED);
    }

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-SCOPE: College Admin of A editing a role owned by college B gives 404")
    void actRoleCustomManageScope() {
        RoleDefinition ownedByB = world.customRole("COLLEGE_B_HELPER", "COLLEGE", collegeB, "USER_VIEW");
        world.signIn(collegeAdminA);

        assertThatThrownBy(() -> controller.updateCustomRole(world.tenantId, ownedByB.getId().getRoleDefinitionId(),
                new UpdateCustomRoleRequest("Renamed by A", null, null), auth(collegeAdminA)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> controller.updateRoleActionSet(world.tenantId, ownedByB.getId().getRoleDefinitionId(),
                new UpdateRoleActionSetRequest(List.of("USER_VIEW")), auth(collegeAdminA)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(ownedByB.getRoleName()).isEqualTo("Custom COLLEGE_B_HELPER");
    }

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-GUARD: adding PAYROLL_APPROVE_TOTALS gives 422 LOCKED_ACTION_FORBIDDEN")
    void guardPayrollTotalsLocked() {
        world.signIn(collegeAdminA);
        assertGuard(() -> controller.createCustomRole(world.tenantId, create("PAY_ROLE", "PAYROLL_APPROVE_TOTALS"),
                auth(collegeAdminA)), "LOCKED_ACTION_FORBIDDEN");
    }

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-GUARD: adding SALARY_INDIVIDUAL_VIEW gives 422")
    void guardIndividualSalaryLocked() {
        // Even a creator whose role holds it may not put it in a custom role (Decision D).
        world.grant(world.standardRole("COLLEGE_ADMIN"), "SALARY_INDIVIDUAL_VIEW");
        world.signIn(collegeAdminA);
        assertGuard(() -> controller.createCustomRole(world.tenantId, create("SALARY_ROLE", "SALARY_INDIVIDUAL_VIEW"),
                auth(collegeAdminA)), "LOCKED_ACTION_FORBIDDEN");
    }

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-GUARD: adding an approval action gives 422")
    void guardApprovalAction() {
        assertThat(RbacTestWorld.ACTIONS.get("GUARDIAN_CHANGE_APPROVE").approval()).isTrue();
        // Even when the creator holds it: approval power comes only from the chain (CR-10).
        world.grant(world.standardRole("COLLEGE_ADMIN"), "GUARDIAN_CHANGE_APPROVE");
        world.signIn(collegeAdminA);
        assertGuard(() -> controller.createCustomRole(world.tenantId, create("APPROVER_ROLE", "GUARDIAN_CHANGE_APPROVE"),
                auth(collegeAdminA)), "APPROVAL_ACTION_FORBIDDEN");
    }

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-GUARD: adding an action the creator doesn't hold gives 422 ACTION_NOT_HELD")
    void guardActionNotHeld() {
        assertThat(RbacTestWorld.ACTIONS.get("MARKS_ENTER").approval()).isFalse();
        assertThat(RbacTestWorld.standardRoleHolds("COLLEGE_ADMIN", "MARKS_ENTER")).isFalse();
        world.signIn(collegeAdminA);
        assertGuard(() -> controller.createCustomRole(world.tenantId, create("MARKS_ROLE", "USER_VIEW", "MARKS_ENTER"),
                auth(collegeAdminA)), "ACTION_NOT_HELD");
    }

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-GUARD: editing a role you hold gives 403")
    void guardEditingOwnRole() {
        RoleDefinition mine = world.customRole("ADMISSIONS_HELPER", "COLLEGE", collegeA, "USER_VIEW");
        world.assign(collegeAdminA, mine, ScopeType.INSTITUTION, collegeA, null);
        // Two roles now: the College Admin role is the chosen active one.
        UserRoleAssignment admin = world.assignments.stream()
                .filter(a -> a.getUserAccountId().equals(collegeAdminA)
                        && !a.getRoleDefinitionId().equals(mine.getId().getRoleDefinitionId()))
                .findFirst().orElseThrow();
        world.choose(collegeAdminA, admin);
        world.signIn(collegeAdminA);

        assertThatThrownBy(() -> controller.updateRoleActionSet(world.tenantId, mine.getId().getRoleDefinitionId(),
                new UpdateRoleActionSetRequest(List.of("USER_VIEW", "STRUCTURE_MANAGE")), auth(collegeAdminA)))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(world.actionsOf(mine.getId().getRoleDefinitionId()))
                .extracting(RoleAction::getActionCode).containsExactly("USER_VIEW");
    }

    @Test
    @DisplayName("ACT-ROLE_CUSTOM_MANAGE-GUARD: a College Admin editing a University-owned role gives 403")
    void guardCollegeAdminEditingUniversityRole() {
        RoleDefinition universityRole = world.customRole("UNIVERSITY_OFFICE_HELPER", "UNIVERSITY", null, "USER_VIEW");
        world.signIn(collegeAdminA);

        assertThatThrownBy(() -> controller.retireCustomRole(world.tenantId, universityRole.getId().getRoleDefinitionId(),
                auth(collegeAdminA)))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(universityRole.getStatus()).isEqualTo(RoleDefinitionStatus.ACTIVE);
    }
}
