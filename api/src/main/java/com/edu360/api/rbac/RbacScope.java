package com.edu360.api.rbac;

import java.util.UUID;

/**
 * WHAT: the "where" half of PermissionEvaluatorService.can(...) - the exact scope the caller
 * is asking to act in, shaped the same way as app.user_role_assignment's own
 * (scope_type, institution_id, department_id) triple so the evaluator can match it against a
 * row with no translation layer. The factory methods enforce the same shape the database's own
 * CHECK constraints do (ck_user_role_assignment_r1/r2), so an invalid combination (e.g.
 * DEPARTMENT scope with no departmentId) cannot be constructed in the first place.
 */
public record RbacScope(ScopeType scopeType, UUID institutionId, UUID departmentId) {

    public static RbacScope tenant() {
        return new RbacScope(ScopeType.TENANT, null, null);
    }

    public static RbacScope institution(UUID institutionId) {
        if (institutionId == null) {
            throw new IllegalArgumentException("institutionId is required for an INSTITUTION-scoped check");
        }
        return new RbacScope(ScopeType.INSTITUTION, institutionId, null);
    }

    public static RbacScope department(UUID institutionId, UUID departmentId) {
        if (institutionId == null || departmentId == null) {
            throw new IllegalArgumentException("institutionId and departmentId are both required for a DEPARTMENT-scoped check");
        }
        return new RbacScope(ScopeType.DEPARTMENT, institutionId, departmentId);
    }
}
