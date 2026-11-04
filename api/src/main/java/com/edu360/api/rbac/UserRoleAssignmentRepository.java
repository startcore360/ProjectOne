package com.edu360.api.rbac;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * No delete method anywhere on this interface, deliberately - same reasoning as
 * RoleDefinitionRepository: an assignment is ENDED (status flip via save()), never removed, so
 * it remains in the audit trail (Addendum rules 17 and 18). Extends the bare marker
 * {@link Repository} so a DELETE capability cannot reappear through an inherited default
 * method.
 */
public interface UserRoleAssignmentRepository extends Repository<UserRoleAssignment, UserRoleAssignmentId> {

    UserRoleAssignment save(UserRoleAssignment assignment);

    Optional<UserRoleAssignment> findById(UserRoleAssignmentId id);

    /**
     * The exact lookup PermissionEvaluatorService.can(...) runs: the ONE assignment (per
     * Addendum rule 10) for this tenant+user whose scope EXACTLY matches the scope the caller
     * is asking about. Deliberately exact, not "covers" (e.g. a TENANT-scoped assignment is NOT
     * treated as also covering an INSTITUTION-scoped request in this slice) - see
     * PermissionEvaluatorService's class javadoc for why that narrower rule was chosen for a
     * first slice, and Addendum rules 8/9 for why permissions from a different scope must never
     * be inferred in here instead of surfaced as a separate, explicit decision.
     *
     * institutionId/departmentId are frequently null (TENANT scope); Spring Data's derived
     * query support binds a null parameter as "IS NULL" automatically, so this single method
     * covers all three ScopeType cases without a null-handling branch in the caller.
     */
    List<UserRoleAssignment> findByIdTenantIdAndUserAccountIdAndScopeTypeAndInstitutionIdAndDepartmentIdAndStatus(
            UUID tenantId, UUID userAccountId, ScopeType scopeType, UUID institutionId, UUID departmentId,
            AssignmentStatus status);
}
