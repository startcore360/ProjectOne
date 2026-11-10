package com.edu360.api.platformops.role;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;

/**
 * Which actions a custom platform role may hold (D3 CR-01, CR-03, CR-04, TPA-03). The database
 * enforces the reserved list too (ck_platform_role_action_reserved). The limits by base role carry
 * the register's "Never allowed" rules into custom roles, so renaming a role can't get round them.
 */
public final class PlatformActionPolicy {

    public static final Set<PlatformRole> CUSTOMISABLE_ROLES = Set.of(
            PlatformRole.PLATFORM_ADMIN, PlatformRole.BILLING_MANAGER,
            PlatformRole.SUPPORT_MANAGER, PlatformRole.RELEASE_MANAGER);

    private static final Map<String, String> RESERVED = Map.of(
            "TENANT_EXPORT_APPROVE", "Stays with the Data Protection Officer.",
            "TENANT_DELETION_APPROVE", "Stays with the Data Protection Officer.",
            "RETENTION_CHANGE_APPROVE", "Stays with the Data Protection Officer.",
            "APPROVAL_DELEGATE", "Stays with the Data Protection Officer.",
            "PLATFORM_USER_MANAGE", "Stays with the Product Owner, so nobody can raise their own access.",
            "TENANT_DELETION_CONFIRM", "Stays with the Product Owner, as one step of the deletion chain.");

    private PlatformActionPolicy() {
    }

    /** Why this action can never go in a custom role, or empty if it can. */
    public static Optional<String> reservedReason(String actionCode) {
        return Optional.ofNullable(RESERVED.get(actionCode));
    }

    /** Why this action can't go in a role of this base, or empty if it can. */
    public static Optional<String> baseLimit(String actionCode, PlatformRole base) {
        if ("DISCOUNT_APPLY".equals(actionCode) && base == PlatformRole.BILLING_MANAGER) {
            return Optional.of("A Billing Manager can't give discounts (D2 action 208).");
        }
        if ("SUPPORT_ACCESS_USE".equals(actionCode) && base != PlatformRole.SUPPORT_MANAGER) {
            return Optional.of("Only support roles can use tenant-granted access (D2 action 213).");
        }
        return Optional.empty();
    }

    public static boolean isGrantable(String actionCode) {
        return PlatformPermissionService.standardActions(PlatformRole.PRODUCT_OWNER).contains(actionCode)
                && !RESERVED.containsKey(actionCode);
    }

    public static void requireCustomisable(PlatformRole base) {
        if (base == null || !CUSTOMISABLE_ROLES.contains(base)) {
            throw new BusinessRuleViolationException("A custom role can be based only on Platform Admin, Billing Manager, "
                    + "Support Manager or Release Manager.");
        }
    }

    public static void validate(Collection<String> actionCodes, PlatformRole base) {
        for (String action : actionCodes) {
            reservedReason(action).ifPresent(reason -> {
                throw new BusinessRuleViolationException(action + " can't go in a custom role. " + reason);
            });
            baseLimit(action, base).ifPresent(reason -> {
                throw new BusinessRuleViolationException(action + " can't go in this role. " + reason);
            });
            if (!isGrantable(action)) {
                throw new BusinessRuleViolationException(action + " is not an action you can give on the platform.");
            }
        }
    }
}
