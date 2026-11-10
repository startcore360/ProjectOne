package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.edu360.api.exception.UnauthorizedException;

/**
 * Unit tests for UserGovernanceGuard:
 * Enforces T01 (institution scope boundaries) and T02 (governance hierarchy protection).
 */
class UserGovernanceGuardTest {

    private final Map<UUID, List<UserRoleAssignment>> assignmentsByUser = new HashMap<>();
    private final Map<UUID, RoleDefinition> roleDefinitionsById = new HashMap<>();

    private UserRoleAssignmentRepository assignmentRepo;
    private RoleDefinitionRepository roleDefRepo;
    private UserGovernanceGuard guard;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID instA = UUID.randomUUID();
    private final UUID instB = UUID.randomUUID();

    private final UUID roleDefPrincipalId = UUID.randomUUID();
    private final UUID roleDefVcId = UUID.randomUUID();
    private final UUID roleDefStaffId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assignmentsByUser.clear();
        roleDefinitionsById.clear();

        // Seed role definitions
        roleDefinitionsById.put(roleDefPrincipalId, RoleDefinition.builder()
                .id(new RoleDefinitionId(tenantId, roleDefPrincipalId))
                .roleCode("PRINCIPAL_COLLEGE")
                .roleName("Principal (College)")
                .build());

        roleDefinitionsById.put(roleDefVcId, RoleDefinition.builder()
                .id(new RoleDefinitionId(tenantId, roleDefVcId))
                .roleCode("VICE_CHANCELLOR")
                .roleName("Vice Chancellor")
                .build());

        roleDefinitionsById.put(roleDefStaffId, RoleDefinition.builder()
                .id(new RoleDefinitionId(tenantId, roleDefStaffId))
                .roleCode("STAFF")
                .roleName("General Staff")
                .build());

        // Dynamic proxy for UserRoleAssignmentRepository
        assignmentRepo = (UserRoleAssignmentRepository) Proxy.newProxyInstance(
                UserRoleAssignmentRepository.class.getClassLoader(),
                new Class<?>[]{UserRoleAssignmentRepository.class},
                (proxy, method, args) -> {
                    if ("findByIdTenantIdAndUserAccountIdAndStatus".equals(method.getName())) {
                        UUID userId = (UUID) args[1];
                        return assignmentsByUser.getOrDefault(userId, List.of());
                    }
                    return null;
                }
        );

        // Dynamic proxy for RoleDefinitionRepository
        roleDefRepo = (RoleDefinitionRepository) Proxy.newProxyInstance(
                RoleDefinitionRepository.class.getClassLoader(),
                new Class<?>[]{RoleDefinitionRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        RoleDefinitionId id = (RoleDefinitionId) args[0];
                        return Optional.ofNullable(roleDefinitionsById.get(id.getRoleDefinitionId()));
                    }
                    return Optional.empty();
                }
        );

        guard = new UserGovernanceGuard(assignmentRepo, roleDefRepo);
    }

    @Test
    void t01_sameInstitution_allows() {
        UUID callerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        // Target has active assignment in instA
        assignmentsByUser.put(targetId, List.of(
                UserRoleAssignment.builder()
                        .id(new UserRoleAssignmentId(tenantId, UUID.randomUUID()))
                        .userAccountId(targetId)
                        .institutionId(instA)
                        .roleDefinitionId(roleDefStaffId)
                        .scopeType(ScopeType.INSTITUTION)
                        .status(AssignmentStatus.ACTIVE)
                        .build()
        ));

        assertThatCode(() -> guard.checkTargetUserAuthority(tenantId, callerId, targetId, instA))
                .doesNotThrowAnyException();
    }

    @Test
    void t01_differentInstitution_denies() {
        UUID callerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        // Target belongs to instB only
        assignmentsByUser.put(targetId, List.of(
                UserRoleAssignment.builder()
                        .id(new UserRoleAssignmentId(tenantId, UUID.randomUUID()))
                        .userAccountId(targetId)
                        .institutionId(instB)
                        .roleDefinitionId(roleDefStaffId)
                        .scopeType(ScopeType.INSTITUTION)
                        .status(AssignmentStatus.ACTIVE)
                        .build()
        ));

        // Caller at instA attempts to manage target -> Deny
        assertThatThrownBy(() -> guard.checkTargetUserAuthority(tenantId, callerId, targetId, instA))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("institution scope");
    }

    @Test
    void t02_lowerAdminModifyingTopGovernance_denies() {
        UUID lowerAdminId = UUID.randomUUID();
        UUID topLeaderId = UUID.randomUUID();

        // Lower admin is STAFF
        assignmentsByUser.put(lowerAdminId, List.of(
                UserRoleAssignment.builder()
                        .id(new UserRoleAssignmentId(tenantId, UUID.randomUUID()))
                        .userAccountId(lowerAdminId)
                        .institutionId(instA)
                        .roleDefinitionId(roleDefStaffId)
                        .scopeType(ScopeType.INSTITUTION)
                        .status(AssignmentStatus.ACTIVE)
                        .build()
        ));

        // Target is VICE_CHANCELLOR
        assignmentsByUser.put(topLeaderId, List.of(
                UserRoleAssignment.builder()
                        .id(new UserRoleAssignmentId(tenantId, UUID.randomUUID()))
                        .userAccountId(topLeaderId)
                        .roleDefinitionId(roleDefVcId)
                        .scopeType(ScopeType.TENANT)
                        .status(AssignmentStatus.ACTIVE)
                        .build()
        ));

        assertThatThrownBy(() -> guard.checkTargetUserAuthority(tenantId, lowerAdminId, topLeaderId, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("governance authority");
    }

    @Test
    void t02_topGovernanceModifyingStaff_allows() {
        UUID topLeaderId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        // Caller is VICE_CHANCELLOR
        assignmentsByUser.put(topLeaderId, List.of(
                UserRoleAssignment.builder()
                        .id(new UserRoleAssignmentId(tenantId, UUID.randomUUID()))
                        .userAccountId(topLeaderId)
                        .roleDefinitionId(roleDefVcId)
                        .scopeType(ScopeType.TENANT)
                        .status(AssignmentStatus.ACTIVE)
                        .build()
        ));

        // Target is STAFF
        assignmentsByUser.put(staffId, List.of(
                UserRoleAssignment.builder()
                        .id(new UserRoleAssignmentId(tenantId, UUID.randomUUID()))
                        .userAccountId(staffId)
                        .roleDefinitionId(roleDefStaffId)
                        .scopeType(ScopeType.INSTITUTION)
                        .institutionId(instA)
                        .status(AssignmentStatus.ACTIVE)
                        .build()
        ));

        assertThatCode(() -> guard.checkTargetUserAuthority(tenantId, topLeaderId, staffId, null))
                .doesNotThrowAnyException();
    }
}
