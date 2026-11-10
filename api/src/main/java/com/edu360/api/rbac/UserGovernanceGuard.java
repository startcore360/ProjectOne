package com.edu360.api.rbac;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.edu360.api.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enforces T01 (institution scope boundary) and T02 (governance hierarchy boundary)
 * checks on target user accounts before administrative mutations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserGovernanceGuard {

    public static final Set<String> TOP_GOVERNANCE_ROLES = Set.of(
            "UNIVERSITY_ADMIN",
            "VICE_CHANCELLOR",
            "REGISTRAR",
            "MANAGEMENT",
            "PRINCIPAL_COLLEGE",
            "SCHOOL_PRINCIPAL"
    );

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;

    /**
     * Verifies that the caller has authority over the target user:
     * 1. If institutionId is scoped, the target user must belong to that institution (T01).
     * 2. Lower-tier admins cannot reset or deactivate top governance leaders (T02).
     */
    public void checkTargetUserAuthority(UUID tenantId, UUID callerUserAccountId, UUID targetUserAccountId, UUID institutionId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(callerUserAccountId, "callerUserAccountId is required");
        Objects.requireNonNull(targetUserAccountId, "targetUserAccountId is required");

        if (callerUserAccountId.equals(targetUserAccountId)) {
            return; // Self-checks (e.g. self-deactivation) handled by calling controller
        }

        List<UserRoleAssignment> targetAssignments = userRoleAssignmentRepository
                .findByIdTenantIdAndUserAccountIdAndStatus(tenantId, targetUserAccountId, AssignmentStatus.ACTIVE);

        // T01: Institution scope boundary
        if (institutionId != null && !targetAssignments.isEmpty()) {
            boolean inInstitution = targetAssignments.stream()
                    .anyMatch(a -> institutionId.equals(a.getInstitutionId()));
            if (!inInstitution) {
                log.warn("T01 violation: caller {} attempted to manage target {} outside scoped institution {}",
                        callerUserAccountId, targetUserAccountId, institutionId);
                throw new UnauthorizedException("Target user does not belong to the requested institution scope.");
            }
        }

        // T02: Hierarchy protection
        if (!targetAssignments.isEmpty()) {
            boolean targetIsTopGovernance = targetAssignments.stream().anyMatch(a -> {
                RoleDefinition def = roleDefinitionRepository
                        .findById(new RoleDefinitionId(tenantId, a.getRoleDefinitionId()))
                        .orElse(null);
                return def != null && TOP_GOVERNANCE_ROLES.contains(def.getRoleCode());
            });

            if (targetIsTopGovernance) {
                List<UserRoleAssignment> callerAssignments = userRoleAssignmentRepository
                        .findByIdTenantIdAndUserAccountIdAndStatus(tenantId, callerUserAccountId, AssignmentStatus.ACTIVE);

                boolean callerIsTopGovernance = callerAssignments.stream().anyMatch(a -> {
                    RoleDefinition def = roleDefinitionRepository
                            .findById(new RoleDefinitionId(tenantId, a.getRoleDefinitionId()))
                            .orElse(null);
                    return def != null && TOP_GOVERNANCE_ROLES.contains(def.getRoleCode());
                });

                if (!callerIsTopGovernance) {
                    log.warn("T02 violation: lower admin {} attempted to modify top governance account {}",
                            callerUserAccountId, targetUserAccountId);
                    throw new UnauthorizedException("Insufficient governance authority to modify this account.");
                }
            }
        }
    }
}
