package com.edu360.api.rbac;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * No delete method anywhere on this interface, deliberately: an assignment is ENDED
 * (status flip via save()), never removed, so it remains in the audit trail (Addendum rules 17 and 18).
 */
public interface UserRoleAssignmentRepository extends Repository<UserRoleAssignment, UserRoleAssignmentId> {

    UserRoleAssignment save(UserRoleAssignment assignment);

    Optional<UserRoleAssignment> findById(UserRoleAssignmentId id);

    /**
     * Exact lookup: the assignment matching tenant, user, scope type, institution, and department.
     */
    List<UserRoleAssignment> findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
            UUID tenantId, UUID userAccountId, ScopeType scopeType, UUID institutionId, UUID departmentId,
            AssignmentStatus status);

    /**
     * Scope-agnostic: every ACTIVE assignment this user holds in this tenant, across all scopes.
     */
    List<UserRoleAssignment> findByIdTenantIdAndUserAccountIdAndStatus(
            UUID tenantId, UUID userAccountId, AssignmentStatus status);

    /**
     * Retrieves all assignments (both ACTIVE and ENDED) for a specific user in a tenant.
     */
    List<UserRoleAssignment> findByIdTenantIdAndUserAccountId(
            UUID tenantId, UUID userAccountId);

    /**
     * Looks up an assignment by its specific tenantId and userRoleAssignmentId.
     */
    Optional<UserRoleAssignment> findByIdTenantIdAndIdUserRoleAssignmentId(
            UUID tenantId, UUID userRoleAssignmentId);

    /**
     * Retrieves all active assignments belonging to a specific institution.
     */
    List<UserRoleAssignment> findByIdTenantIdAndInstitutionIdAndStatus(
            UUID tenantId, UUID institutionId, AssignmentStatus status);
}
