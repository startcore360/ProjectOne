package com.edu360.api.platformops.subscription;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.subscription.dto.TenantAddonRequest;
import com.edu360.api.platformops.subscription.dto.TenantAddonResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@Service
public class TenantSubscriptionRequestService {
    private final PermissionEvaluatorService permissionEvaluator;
    private final PlatformSubscriptionService subscriptionService;
    private final AuditLogService auditLogService;

    public TenantSubscriptionRequestService(PermissionEvaluatorService permissionEvaluator,
            PlatformSubscriptionService subscriptionService, AuditLogService auditLogService) {
        this.permissionEvaluator = permissionEvaluator;
        this.subscriptionService = subscriptionService;
        this.auditLogService = auditLogService;
    }

    public TenantAddonResponse request(UUID tenantId, TenantPrincipal actor, TenantAddonRequest request) {
        requireOwnTenant(tenantId, actor);
        permissionEvaluator.authorize(new RbacPrincipal(actor.tenantId(), actor.userAccountId()),
                "SUBSCRIPTION_ADDON_REQUEST", RbacScope.tenant(), RbacTarget.NONE);
        TenantAddonResponse response = subscriptionService.requestAddon(tenantId, actor.userAccountId(), request);
        auditLogService.record(new AuditEntry(AuditEventType.CREATE, "SUBSCRIPTION_ADDON_REQUEST",
                "SUBSCRIPTION_ADDON", response.subscriptionAddonId(), null, null,
                java.util.Map.of("catalog_item_id", request.catalogItemId().toString(),
                        "quantity", request.quantity().toPlainString()),
                1, "P", "SUCCESS"));
        return response;
    }

    public List<TenantAddonResponse> list(UUID tenantId, TenantPrincipal actor) {
        requireOwnTenant(tenantId, actor);
        permissionEvaluator.authorize(new RbacPrincipal(actor.tenantId(), actor.userAccountId()),
                "SUBSCRIPTION_ADDON_REQUEST", RbacScope.tenant(), RbacTarget.NONE);
        return subscriptionService.listTenantAddonRequests(tenantId);
    }

    private static void requireOwnTenant(UUID tenantId, TenantPrincipal actor) {
        if (actor == null || actor.tenantId() == null || !actor.tenantId().equals(tenantId)) {
            throw new UnauthorizedException("The subscription request must belong to the authenticated tenant.");
        }
    }
}
