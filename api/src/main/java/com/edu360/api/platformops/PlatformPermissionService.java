package com.edu360.api.platformops;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.platform.PlatformRole;

/**
 * Platform-scoped authorization. Fail-closed: an unrecognized action or a role without it is denied.
 *
 * ALLOWED_ROLES holds the standard rights from ref.standard_role_action, with the Product Owner
 * additions approved in TPA-01 and TPA-04 (docs/GOVERNANCE_DECISION_LOG.md, 2026-11-07). Someone
 * holding a custom role gets that role's actions instead. The Product Owner holds everything marked
 * here, but while an active person holds a handed-over action, the Product Owner must step in first.
 */
@Service
public class PlatformPermissionService {

    private static final Set<PlatformRole> OWNER = Set.of(PlatformRole.PRODUCT_OWNER);
    private static final Set<PlatformRole> OWNER_AND_ADMIN = Set.of(PlatformRole.PRODUCT_OWNER, PlatformRole.PLATFORM_ADMIN);
    private static final Set<PlatformRole> DPO = Set.of(PlatformRole.DATA_PROTECTION_OFFICER);

    private static final Map<String, Set<PlatformRole>> ALLOWED_ROLES = Map.ofEntries(
            Map.entry("TENANT_CREATE", OWNER_AND_ADMIN),
            Map.entry("TENANT_ACTIVATE", OWNER), // D1 §11.1, D2 action 203
            Map.entry("TENANT_SUSPEND_ARCHIVE", OWNER),
            Map.entry("TENANT_SETTINGS", OWNER_AND_ADMIN),
            Map.entry("FEATURE_TOGGLE", OWNER_AND_ADMIN),
            Map.entry("PLAN_MANAGE", OWNER_AND_ADMIN),
            Map.entry("INTEGRATION_TYPES_MANAGE", OWNER_AND_ADMIN),
            Map.entry("PLATFORM_AUDIT_VIEW", OWNER_AND_ADMIN),
            Map.entry("USAGE_COUNTS_VIEW", OWNER_AND_ADMIN),
            Map.entry("BACKUP_STATUS_VIEW", OWNER_AND_ADMIN),
            Map.entry("PLATFORM_USER_MANAGE", OWNER),
            Map.entry("SECURITY_POLICY_SET", OWNER),
            Map.entry("DISCOUNT_APPLY", OWNER), // Rule [F-4]: Billing Manager denied
            Map.entry("INVOICE_MANAGE", Set.of(PlatformRole.BILLING_MANAGER, PlatformRole.PRODUCT_OWNER)),
            Map.entry("OVERDUE_FLAG", Set.of(PlatformRole.BILLING_MANAGER, PlatformRole.PRODUCT_OWNER)),
            Map.entry("SUPPORT_TICKET_MANAGE", Set.of(PlatformRole.SUPPORT_MANAGER, PlatformRole.PLATFORM_ADMIN, PlatformRole.PRODUCT_OWNER)),
            // SUPPORT_ACCESS_GRANT and SUPPORT_TICKET_RAISE are deliberately absent: they belong to
            // tenant users (TenantSupportController). Platform roles never approve their own access.
            Map.entry("SUPPORT_ACCESS_USE", Set.of(PlatformRole.SUPPORT_MANAGER, PlatformRole.PRODUCT_OWNER)),
            Map.entry("RELEASE_MANAGE", Set.of(PlatformRole.RELEASE_MANAGER, PlatformRole.PRODUCT_OWNER, PlatformRole.PLATFORM_ADMIN)),
            Map.entry("PLATFORM_REPORTS_VIEW", Set.of(PlatformRole.BILLING_MANAGER, PlatformRole.SUPPORT_MANAGER, PlatformRole.PRODUCT_OWNER)),
            Map.entry("TENANT_EXPORT_REQUEST", OWNER),
            Map.entry("TENANT_DELETION_REQUEST", OWNER),
            Map.entry("TENANT_DELETION_CONFIRM", OWNER),
            Map.entry("RBAC_MAPPING_CHECK", Set.of(PlatformRole.DATA_PROTECTION_OFFICER, PlatformRole.PRODUCT_OWNER)),
            // Data protection approvals stay with a separate person (TPA-04, D2 action 198).
            Map.entry("TENANT_EXPORT_APPROVE", DPO),
            Map.entry("TENANT_DELETION_APPROVE", DPO),
            Map.entry("RETENTION_CHANGE_APPROVE", DPO),
            Map.entry("APPROVAL_DELEGATE", DPO)
    );

    private final PlatformAccessLookup accessLookup;
    private final PlatformAuditWriter auditWriter;

    public PlatformPermissionService(PlatformAccessLookup accessLookup, PlatformAuditWriter auditWriter) {
        this.accessLookup = accessLookup;
        this.auditWriter = auditWriter;
    }

    /** L-07: a refusal is itself an audited event (outcome DENIED), written before the refusal is raised. */
    private UnauthorizedException deny(PlatformPrincipal principal, String actionCode, String message) {
        if (principal != null && principal.platformUserId() != null) {
            auditWriter.recordDenied(principal, (actionCode != null && ALLOWED_ROLES.containsKey(actionCode)) ? actionCode : null);
        }
        return new UnauthorizedException(message);
    }

    public void authorize(PlatformPrincipal principal, String actionCode) {
        if (!ALLOWED_ROLES.containsKey(actionCode)) {
            throw deny(principal, actionCode, "Action " + actionCode + " is not a recognized platform action.");
        }
        if (principal == null) {
            throw new UnauthorizedException(actionCode + " requires an authenticated platform principal.");
        }
        if (!holds(principal, actionCode)) {
            throw deny(principal, actionCode,
                    "Platform role " + principal.platformRole() + " does not hold action " + actionCode + ".");
        }
        if (requiresStepIn(principal, actionCode)) {
            throw deny(principal, actionCode,
                    "STEP_IN_REQUIRED: " + actionCode + " has been handed over. Step in on the Platform roles page, with a reason, to act on it.");
        }
    }

    /** Passes if the principal may perform any one of the actions, as D5 lists for shared reads. */
    public void authorizeAny(PlatformPrincipal principal, Collection<String> actionCodes) {
        if (principal == null) {
            throw new UnauthorizedException("This action requires an authenticated platform principal.");
        }
        boolean allowed = actionCodes.stream().anyMatch(action -> ALLOWED_ROLES.containsKey(action)
                && holds(principal, action)
                && !requiresStepIn(principal, action));
        if (!allowed) {
            throw deny(principal, null,
                    "Platform role " + principal.platformRole() + " holds none of " + actionCodes + ".");
        }
    }

    public boolean holds(PlatformPrincipal principal, String actionCode) {
        return accessLookup.customRoleActions(principal.platformUserId())
                .map(actions -> actions.contains(actionCode))
                .orElseGet(() -> ALLOWED_ROLES.getOrDefault(actionCode, Set.of()).contains(principal.platformRole()));
    }

    /** Actions this person holds, whether or not they must step in first. */
    public Set<String> heldActions(PlatformPrincipal principal) {
        Set<String> held = new TreeSet<>();
        for (String action : ALLOWED_ROLES.keySet()) {
            if (holds(principal, action)) {
                held.add(action);
            }
        }
        return held;
    }

    /** Held, but handed over and not stepped in on. Only ever non-empty for a Product Owner. */
    public List<String> actionsNeedingStepIn(PlatformPrincipal principal) {
        if (principal.platformRole() != PlatformRole.PRODUCT_OWNER) {
            return List.of();
        }
        return accessLookup.handedOverActions().stream()
                .filter(action -> holds(principal, action))
                .filter(action -> !accessLookup.hasActiveStepIn(principal.platformUserId(), action))
                .sorted()
                .toList();
    }

    public static Set<String> knownActions() {
        return ALLOWED_ROLES.keySet();
    }

    public static Set<String> standardActions(PlatformRole role) {
        Set<String> actions = new TreeSet<>();
        ALLOWED_ROLES.forEach((action, roles) -> {
            if (roles.contains(role)) {
                actions.add(action);
            }
        });
        return actions;
    }

    private boolean requiresStepIn(PlatformPrincipal principal, String actionCode) {
        return principal.platformRole() == PlatformRole.PRODUCT_OWNER
                && accessLookup.isHandedOver(actionCode)
                && !accessLookup.hasActiveStepIn(principal.platformUserId(), actionCode);
    }
}
