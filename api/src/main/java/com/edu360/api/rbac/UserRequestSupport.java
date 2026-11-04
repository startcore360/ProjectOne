package com.edu360.api.rbac;

import java.util.UUID;

import org.springframework.security.core.Authentication;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;

/**
 * WHAT: the two checks every USER_VIEW/USER_EDIT/USER_DEACTIVATE/USER_PASSWORD_RESET_UNLOCK
 * controller in this package runs before calling PermissionEvaluatorService, factored out once
 * they needed to be done identically in four separate controllers instead of copy-pasted four
 * times (the fifth copy - UserCreationController - still has its own inline version; left as-is
 * rather than touched, since UserCreationController already predates this helper and changing a
 * controller outside this story's four to "clean up" the duplication is out of scope here).
 *
 * WHY the tenant-isolation re-check (Addendum rule 13): a path parameter is attacker-controlled
 * input; the principal's own tenantId comes from the already-verified token and is the only
 * value ever trusted for "which tenant is this request actually for".
 */
final class UserRequestSupport {

    private UserRequestSupport() {
    }

    static TenantPrincipal requireTenantPrincipal(Authentication authentication, UUID tenantId) {
        if (!(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("This is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }
        return principal;
    }

    static RbacScope scopeOf(ScopeType scopeType, UUID institutionId, UUID departmentId) {
        return switch (scopeType) {
            case TENANT -> RbacScope.tenant();
            case INSTITUTION -> RbacScope.institution(institutionId);
            case DEPARTMENT -> RbacScope.department(institutionId, departmentId);
        };
    }
}
