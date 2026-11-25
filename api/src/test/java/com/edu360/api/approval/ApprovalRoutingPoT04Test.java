package com.edu360.api.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.RoleDefinitionStatus;
import com.edu360.api.rbac.RoleOrigin;
import com.edu360.api.rbac.RoleLevel;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentId;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.rolldown.RoleRolldownDefault;
import com.edu360.api.rolldown.RoleRolldownDefaultRepository;
import com.edu360.api.rolldown.RoleRolldownOverrideRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalRoutingPoT04Test {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID MANAGEMENT_ROLE_ID = UUID.randomUUID();
    private static final UUID TRUSTEE_ROLE_ID = UUID.randomUUID();

    @Mock private DelegationRepository delegations;
    @Mock private PermissionEvaluatorService permissions;
    @Mock private RoleDefinitionRepository roleDefinitions;
    @Mock private UserRoleAssignmentRepository assignments;
    @Mock private RoleRolldownOverrideRepository overrides;
    @Mock private RoleRolldownDefaultRepository defaults;
    @Mock private InstitutionRepository institutions;

    private ApprovalRouting routing;

    @BeforeEach
    void setUp() {
        routing = new ApprovalRouting(delegations, permissions, roleDefinitions, assignments,
                overrides, defaults, institutions);
        when(delegations.findByIdTenantIdAndDelegateUserId(TENANT, USER)).thenReturn(List.of());
    }

    @Test
    void managementAssignmentBlocksPrincipalAndRoutesManagementFirst() {
        stubHolder("MANAGEMENT", MANAGEMENT_ROLE_ID, true);
        ApprovalRequest request = request("CONCESSION");
        ApprovalStep step = principalStep(request);

        assertThat(routing.route(TENANT, USER, "PRINCIPAL_COLLEGE", request, step)).isEmpty();
        assertThat(routing.route(TENANT, USER, "MANAGEMENT", request, step))
                .contains(new ApprovalRouting.Route(ApprovalRouting.Via.ROLE, null, null, null));
    }

    @Test
    void trusteeAssignmentActsOnlyWhenManagementIsNotAssigned() {
        stubHolder("MANAGEMENT", MANAGEMENT_ROLE_ID, false);
        stubHolder("GOVERNING_BODY_APPROVER", TRUSTEE_ROLE_ID, true);
        ApprovalRequest request = request("SCHOLARSHIP_LIST");
        ApprovalStep step = principalStep(request);

        assertThat(routing.route(TENANT, USER, "PRINCIPAL_COLLEGE", request, step)).isEmpty();
        assertThat(routing.route(TENANT, USER, "GOVERNING_BODY_APPROVER", request, step))
                .contains(new ApprovalRouting.Route(ApprovalRouting.Via.ROLE, null, null, null));
    }

    @Test
    void principalIsOnlyTheRollDownWhenNeitherManagementNorTrusteesAreAssigned() {
        when(overrides.findByIdTenantIdAndStatus(TENANT, "ACTIVE")).thenReturn(List.of());
        stubHolder("MANAGEMENT", MANAGEMENT_ROLE_ID, false);
        stubHolder("GOVERNING_BODY_APPROVER", TRUSTEE_ROLE_ID, false);
        when(defaults.findByContextOrderByMissingRoleCodeAscPreferenceAsc("UNIVERSITY_WIDE"))
                .thenReturn(List.of(new RoleRolldownDefault(1, "MANAGEMENT", "UNIVERSITY_WIDE",
                        "APPROVE", (short) 1, "PRINCIPAL_COLLEGE", "PO-T04 fallback")));

        ApprovalRequest request = request("CONCESSION");
        ApprovalStep step = principalStep(request);

        assertThat(routing.route(TENANT, USER, "PRINCIPAL_COLLEGE", request, step))
                .contains(new ApprovalRouting.Route(ApprovalRouting.Via.ROLLDOWN, "MANAGEMENT", null, null));
    }

    private void stubHolder(String roleCode, UUID roleId, boolean assigned) {
        when(roleDefinitions.findByIdTenantIdAndRoleCode(TENANT, roleCode))
                .thenReturn(assigned ? Optional.of(role(roleCode, roleId)) : Optional.empty());
        if (assigned) {
            when(assignments.findByIdTenantIdAndRoleDefinitionIdAndStatus(TENANT, roleId, AssignmentStatus.ACTIVE))
                    .thenReturn(List.of(assignment(roleId)));
        } else {
            when(assignments.findByIdTenantIdAndRoleDefinitionIdAndStatus(TENANT, roleId, AssignmentStatus.ACTIVE))
                    .thenReturn(List.of());
        }
    }

    private static RoleDefinition role(String code, UUID id) {
        return RoleDefinition.builder()
                .id(new RoleDefinitionId(TENANT, id))
                .roleCode(code).roleName(code)
                .roleOrigin(RoleOrigin.STANDARD).roleLevel(RoleLevel.TENANT)
                .status(RoleDefinitionStatus.ACTIVE)
                .build();
    }

    private static UserRoleAssignment assignment(UUID roleId) {
        return UserRoleAssignment.builder()
                .id(new UserRoleAssignmentId(TENANT, UUID.randomUUID()))
                .userAccountId(USER).roleDefinitionId(roleId)
                .scopeType(ScopeType.TENANT)
                .effectiveFrom(Instant.now().minusSeconds(60))
                .status(AssignmentStatus.ACTIVE)
                .build();
    }

    private static ApprovalRequest request(String process) {
        UUID requestId = UUID.randomUUID();
        return ApprovalRequest.builder()
                .id(new ApprovalRequestId(TENANT, requestId))
                .processCode(process)
                .subjectType("CONCESSION")
                .subjectId(UUID.randomUUID())
                .requestedByUserId(UUID.randomUUID())
                .requesterActiveRole("COLLEGE_ACCOUNTANT")
                .status("PENDING").currentStepNo((short) 1)
                .createdBy(USER).updatedBy(USER)
                .build();
    }

    private static ApprovalStep principalStep(ApprovalRequest request) {
        return ApprovalStep.builder()
                .id(new ApprovalStepId(TENANT, UUID.randomUUID()))
                .approvalRequestId(request.getId().getApprovalRequestId())
                .stepNo((short) 1)
                .approverRoleCode("PRINCIPAL_COLLEGE")
                .createdBy(USER).updatedBy(USER)
                .build();
    }
}
