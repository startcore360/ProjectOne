package com.edu360.api.config;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.RbacPrincipal;

public final class PrincipalExtractor {

    private PrincipalExtractor() {}

    public static RbacPrincipal extractTenantOrSupportPrincipal(UUID tenantId, Authentication authentication, String actionDescription) {
        if (authentication == null) {
            throw new UnauthorizedException(actionDescription + ": no principal on this request.");
        }
        if (authentication.getPrincipal() instanceof TenantPrincipal principal) {
            if (!tenantId.equals(principal.tenantId())) {
                throw new UnauthorizedException("tenantId in the path does not match the authenticated tenant.");
            }
            return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        }
        if (authentication.getPrincipal() instanceof SupportPrincipal support) {
            if (!tenantId.equals(support.tenantId())) {
                throw new UnauthorizedException("tenantId in the path does not match the authenticated support tenant.");
            }
            return new RbacPrincipal(support.tenantId(), support.platformUserId());
        }
        throw new UnauthorizedException(actionDescription + ": unsupported principal type.");
    }
}
