package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.edu360.api.academic.CreateSubjectAllocationRequest;
import com.edu360.api.academic.SubjectAllocation;
import com.edu360.api.academic.SubjectAllocationRepository;
import com.edu360.api.academic.SubjectAllocationService;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLog;
import com.edu360.api.config.ActiveRoleRequiredFilter;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ActiveRoleRequiredException;
import com.edu360.api.exception.GlobalExceptionHandler;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.institution.DepartmentRepository;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.OfferingDepartmentResolver;
import com.edu360.api.selfservice.UserMeService;
import com.edu360.api.selfservice.dto.SwitchActiveRoleResponse;
import com.edu360.api.tenant.TenantContext;

/**
 * A-02 (H1, story 6): the evaluator uses ONLY the active assignment; several roles and no choice
 * gives 409 ACTIVE_ROLE_REQUIRED; the audit records the active role. Real
 * PermissionEvaluatorService, real UserMeService, real TenantUserLookup and real AuditLogService
 * over in-memory repositories holding real role/action rows from sql/05_ref_data.sql.
 */
class ActiveRoleA02Test {

    private RbacTestWorld world;
    private UserMeService userMeService;
    private SubjectAllocationService allocationService;
    private final List<SubjectAllocation> savedAllocations = new ArrayList<>();
    private final OfferingDepartmentResolver departments = mock(OfferingDepartmentResolver.class);

    private final UUID collegeA = UUID.randomUUID();
    private final UUID deptA = UUID.randomUUID();
    private final UUID collegeB = UUID.randomUUID();
    private final UUID deptB = UUID.randomUUID();
    private final UUID offeringInDeptA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        world = new RbacTestWorld();
        userMeService = new UserMeService(world.userAccountRepository, world.userRoleAssignmentRepository,
                world.roleDefinitionRepository, mock(InstitutionRepository.class), mock(DepartmentRepository.class),
                world.auditLogService, world.userActiveRoleRepository, world.roleActionRepository);

        SubjectAllocationRepository allocations = mock(SubjectAllocationRepository.class);
        when(allocations.save(any())).thenAnswer(inv -> {
            SubjectAllocation a = inv.getArgument(0);
            savedAllocations.add(a);
            return a;
        });
        allocationService = new SubjectAllocationService(allocations, world.evaluator, world.auditLogService);
        allocationService.setOfferingDepartmentResolver(departments);
        when(departments.departmentOf(world.tenantId, offeringInDeptA)).thenReturn(Optional.of(deptA));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private TenantPrincipal tenantPrincipal(UUID userId) {
        return new TenantPrincipal(world.tenantId, userId, UserType.STAFF, "user", false);
    }

    private SubjectAllocation allocateInDeptA(UUID userId) {
        allocationService.allocateSubject(world.tenantId, tenantPrincipal(userId),
                new CreateSubjectAllocationRequest(collegeA, offeringInDeptA, UUID.randomUUID(), null, UUID.randomUUID()));
        return savedAllocations.get(savedAllocations.size() - 1);
    }

    @Test
    @DisplayName("ACT-ROLE_SWITCH-ALLOW: switching to an owned assignment changes what the server allows (HOD in A: 200; as Professor in B: 403)")
    void actRoleSwitchAllow() {
        UUID user = world.user("Dual Role");
        UserRoleAssignment hodInA = world.assign(user, "HEAD_OF_DEPARTMENT", ScopeType.DEPARTMENT, collegeA, deptA);
        UserRoleAssignment professorInB = world.assign(user, "PROFESSOR", ScopeType.DEPARTMENT, collegeB, deptB);
        assertThat(RbacTestWorld.standardRoleHolds("HEAD_OF_DEPARTMENT", "SUBJECT_ALLOCATE")).isTrue();
        assertThat(RbacTestWorld.standardRoleHolds("PROFESSOR", "SUBJECT_ALLOCATE")).isFalse();

        // Request 1: choose HOD.
        world.signIn(user);
        SwitchActiveRoleResponse toHod = userMeService.switchActiveRole(tenantPrincipal(user), hodInA.getId().getUserRoleAssignmentId());
        assertThat(toHod.roleCode()).isEqualTo("HEAD_OF_DEPARTMENT");
        assertThat(world.activeChoices.get(user).getUserRoleAssignmentId()).isEqualTo(hodInA.getId().getUserRoleAssignmentId());

        // Request 2: as HOD, an HOD action in college A, department A is allowed and saved.
        world.signIn(user);
        SubjectAllocation saved = allocateInDeptA(user);
        assertThat(saved.getInstitutionId()).isEqualTo(collegeA);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        AuditLog allocationAudit = world.auditRows.get(world.auditRows.size() - 1);
        assertThat(allocationAudit.getActorRoleCode()).isEqualTo("HEAD_OF_DEPARTMENT");

        // Request 3: switch to Professor in college B.
        world.signIn(user);
        userMeService.switchActiveRole(tenantPrincipal(user), professorInB.getId().getUserRoleAssignmentId());

        // Request 4: the same call is now refused (403), even though the HOD assignment still exists.
        world.signIn(user);
        int before = savedAllocations.size();
        assertThatThrownBy(() -> allocateInDeptA(user)).isInstanceOf(UnauthorizedException.class);
        assertThat(savedAllocations).hasSize(before);
    }

    @Test
    @DisplayName("ACT-ROLE_SWITCH-DENY: switching to an assignment the user doesn't own gives 404")
    void actRoleSwitchDeny() {
        UUID user = world.user("Caller");
        UUID other = world.user("Someone Else");
        world.assign(user, "PROFESSOR", ScopeType.DEPARTMENT, collegeA, deptA);
        UserRoleAssignment notMine = world.assign(other, "HEAD_OF_DEPARTMENT", ScopeType.DEPARTMENT, collegeA, deptA);
        world.signIn(user);

        assertThatThrownBy(() -> userMeService.switchActiveRole(tenantPrincipal(user), notMine.getId().getUserRoleAssignmentId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(world.activeChoices).doesNotContainKey(user);
        assertThat(world.auditRows).noneMatch(r -> r.getEventType() == AuditEventType.ROLE_SWITCHED);
    }

    @Test
    @DisplayName("ACT-ROLE_SWITCH-SCOPE: switching to another tenant's assignment gives 404")
    void actRoleSwitchScope() {
        UUID user = world.user("Caller");
        world.assign(user, "PROFESSOR", ScopeType.DEPARTMENT, collegeA, deptA);

        RbacTestWorld otherTenant = new RbacTestWorld();
        UUID foreignUser = otherTenant.user("Foreign");
        UserRoleAssignment foreign = otherTenant.assign(foreignUser, "HEAD_OF_DEPARTMENT", ScopeType.DEPARTMENT,
                UUID.randomUUID(), UUID.randomUUID());

        world.signIn(user);
        assertThatThrownBy(() -> userMeService.switchActiveRole(tenantPrincipal(user), foreign.getId().getUserRoleAssignmentId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(world.activeChoices).doesNotContainKey(user);
    }

    @Test
    @DisplayName("ACT-ROLE_SWITCH-GUARD: several roles and no choice gives 409 ACTIVE_ROLE_REQUIRED")
    void actRoleSwitchGuard() throws Exception {
        UUID user = world.user("Dual Role");
        world.assign(user, "HEAD_OF_DEPARTMENT", ScopeType.DEPARTMENT, collegeA, deptA);
        world.assign(user, "PROFESSOR", ScopeType.DEPARTMENT, collegeB, deptB);
        world.signIn(user);

        // The request context says a choice is required, and no role is active.
        assertThat(TenantContext.isActiveRoleRequired()).isTrue();
        assertThat(TenantContext.currentActiveRoleCode()).isNull();

        // 1) The evaluator refuses on its own - even for the exact scope of one of the roles.
        int before = savedAllocations.size();
        assertThatThrownBy(() -> allocateInDeptA(user))
                .isInstanceOf(ActiveRoleRequiredException.class);
        assertThat(savedAllocations).hasSize(before);
        PermissionDecision decision = world.evaluator.can(world.principal(user), "SUBJECT_ALLOCATE",
                RbacScope.department(collegeA, deptA), RbacTarget.NONE);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ACTIVE_ROLE_REQUIRED");

        // 2) GlobalExceptionHandler maps it to 409 with code ACTIVE_ROLE_REQUIRED.
        ResponseEntity<Map<String, Object>> mapped = new GlobalExceptionHandler()
                .handleActiveRoleRequired(new ActiveRoleRequiredException("choose"), new MockHttpServletRequest());
        assertThat(mapped.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapped.getBody()).containsEntry("code", "ACTIVE_ROLE_REQUIRED");

        // 3) The request filter answers 409 for any other tenant request...
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(tenantPrincipal(user), null, List.of()));
        ActiveRoleRequiredFilter filter = new ActiveRoleRequiredFilter();
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/tenants/" + world.tenantId + "/subject-allocations"),
                blocked, new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(409);
        assertThat(blocked.getContentAsString()).contains("ACTIVE_ROLE_REQUIRED");

        // ...but lets role switching and self-service reads through.
        for (MockHttpServletRequest allowed : List.of(
                new MockHttpServletRequest("POST", "/api/tenants/" + world.tenantId + "/me/active-role"),
                new MockHttpServletRequest("GET", "/api/me"),
                new MockHttpServletRequest("GET", "/api/tenants/" + world.tenantId + "/me/notifications"))) {
            MockHttpServletResponse passed = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(allowed, passed, chain);
            assertThat(chain.getRequest()).as(allowed.getRequestURI()).isNotNull();
            assertThat(passed.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("REG-A02-1: the audit row's active role equals the chosen role")
    void regA02_1_auditRecordsChosenRole() {
        UUID user = world.user("Dual Role");
        UserRoleAssignment hodInA = world.assign(user, "HEAD_OF_DEPARTMENT", ScopeType.DEPARTMENT, collegeA, deptA);
        world.assign(user, "PROFESSOR", ScopeType.DEPARTMENT, collegeB, deptB);

        world.signIn(user);
        userMeService.switchActiveRole(tenantPrincipal(user), hodInA.getId().getUserRoleAssignmentId());
        AuditLog switchRow = world.auditRows.stream()
                .filter(r -> r.getEventType() == AuditEventType.ROLE_SWITCHED).findFirst().orElseThrow();
        assertThat(switchRow.getActorRoleCode()).isEqualTo("HEAD_OF_DEPARTMENT");
        assertThat(switchRow.getScopeType()).isEqualTo("DEPARTMENT");
        assertThat(switchRow.getInstitutionId()).isEqualTo(collegeA);

        world.signIn(user);
        allocateInDeptA(user);
        AuditLog actionRow = world.auditRows.get(world.auditRows.size() - 1);
        assertThat(actionRow.getEventType()).isEqualTo(AuditEventType.ROLE_ASSIGNED);
        assertThat(actionRow.getActorRoleCode()).isEqualTo("HEAD_OF_DEPARTMENT");
        assertThat(actionRow.getScopeType()).isEqualTo("DEPARTMENT");
    }

    @Test
    @DisplayName("REG-A02-2: one role only works without a choice")
    void regA02_2_singleRoleNeedsNoChoice() {
        UUID user = world.user("Single Role");
        world.assign(user, "HEAD_OF_DEPARTMENT", ScopeType.DEPARTMENT, collegeA, deptA);
        world.signIn(user);

        assertThat(TenantContext.isActiveRoleRequired()).isFalse();
        assertThat(TenantContext.currentActiveRoleCode()).isEqualTo("HEAD_OF_DEPARTMENT");
        assertThat(world.activeChoices).doesNotContainKey(user);

        SubjectAllocation saved = allocateInDeptA(user);
        assertThat(saved.getInstitutionId()).isEqualTo(collegeA);
        assertThat(world.auditRows.get(world.auditRows.size() - 1).getActorRoleCode()).isEqualTo("HEAD_OF_DEPARTMENT");
        verify(world.userActiveRoleRepository, never()).save(any());
        verify(world.userRoleAssignmentRepository).findByIdTenantIdAndUserAccountIdAndStatus(eq(world.tenantId), eq(user), eq(AssignmentStatus.ACTIVE));
    }
}
