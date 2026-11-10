package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;

class PlatformPermissionServiceTest {

    private final PlatformPermissionService service = com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());

    private PlatformPrincipal principal(PlatformRole role) {
        return new PlatformPrincipal(UUID.randomUUID(), role, "someone@edu360.test", false);
    }

    @Test
    void authorize_allowsProductOwnerForAllFourActions() {
        PlatformPrincipal productOwner = principal(PlatformRole.PRODUCT_OWNER);

        assertThatCode(() -> service.authorize(productOwner, "PLATFORM_AUDIT_VIEW")).doesNotThrowAnyException();
        assertThatCode(() -> service.authorize(productOwner, "USAGE_COUNTS_VIEW")).doesNotThrowAnyException();
        assertThatCode(() -> service.authorize(productOwner, "BACKUP_STATUS_VIEW")).doesNotThrowAnyException();
        assertThatCode(() -> service.authorize(productOwner, "INTEGRATION_TYPES_MANAGE")).doesNotThrowAnyException();
    }

    @Test
    void authorize_allowsPlatformAdminForAllFourActions() {
        PlatformPrincipal platformAdmin = principal(PlatformRole.PLATFORM_ADMIN);

        assertThatCode(() -> service.authorize(platformAdmin, "PLATFORM_AUDIT_VIEW")).doesNotThrowAnyException();
        assertThatCode(() -> service.authorize(platformAdmin, "USAGE_COUNTS_VIEW")).doesNotThrowAnyException();
        assertThatCode(() -> service.authorize(platformAdmin, "BACKUP_STATUS_VIEW")).doesNotThrowAnyException();
        assertThatCode(() -> service.authorize(platformAdmin, "INTEGRATION_TYPES_MANAGE")).doesNotThrowAnyException();
    }

    @Test
    void authorize_deniesOtherPlatformRoles() {
        PlatformPrincipal billingManager = principal(PlatformRole.BILLING_MANAGER);

        assertThatThrownBy(() -> service.authorize(billingManager, "PLATFORM_AUDIT_VIEW"))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.authorize(billingManager, "INTEGRATION_TYPES_MANAGE"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void authorize_deniesUnrecognizedActionCode() {
        PlatformPrincipal productOwner = principal(PlatformRole.PRODUCT_OWNER);

        assertThatThrownBy(() -> service.authorize(productOwner, "SOME_UNKNOWN_ACTION"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("not a recognized platform action");
    }

    @Test
    void authorize_deniesNullPrincipal() {
        assertThatThrownBy(() -> service.authorize(null, "PLATFORM_AUDIT_VIEW"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void platformAdminAndProductOwnerAreTheOnlyRolesAllowedForUsageCountsView() {
        for (PlatformRole role : PlatformRole.values()) {
            PlatformPrincipal p = principal(role);
            boolean shouldAllow = role == PlatformRole.PRODUCT_OWNER || role == PlatformRole.PLATFORM_ADMIN;
            if (shouldAllow) {
                assertThatCode(() -> service.authorize(p, "USAGE_COUNTS_VIEW")).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> service.authorize(p, "USAGE_COUNTS_VIEW"))
                        .isInstanceOf(UnauthorizedException.class);
            }
        }
        assertThat(PlatformRole.values()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void tenantLifecycleIsAllowedForProductOwnerOnly() {
        for (String action : List.of("TENANT_ACTIVATE", "TENANT_SUSPEND_ARCHIVE")) {
            for (PlatformRole role : PlatformRole.values()) {
                PlatformPrincipal p = principal(role);
                if (role == PlatformRole.PRODUCT_OWNER) {
                    assertThatCode(() -> service.authorize(p, action)).doesNotThrowAnyException();
                } else {
                    assertThatThrownBy(() -> service.authorize(p, action))
                            .isInstanceOf(UnauthorizedException.class);
                }
            }
        }
    }

    private PlatformPermissionService withLookup(PlatformAccessLookup lookup) {
        return com.edu360.api.platformops.PlatformTestSupport.permissions(lookup);
    }

    private static class StubLookup implements PlatformAccessLookup {
        Set<String> customActions;
        Set<String> handedOver = Set.of();
        Set<String> steppedIn = Set.of();

        @Override
        public Optional<Set<String>> customRoleActions(UUID platformUserId) {
            return Optional.ofNullable(customActions);
        }

        @Override
        public boolean isHandedOver(String actionCode) {
            return handedOver.contains(actionCode);
        }

        @Override
        public List<String> handedOverActions() {
            return List.copyOf(handedOver);
        }

        @Override
        public boolean hasActiveStepIn(UUID platformUserId, String actionCode) {
            return steppedIn.contains(actionCode);
        }

        @Override
        public List<String> activeStepIns(UUID platformUserId) {
            return List.copyOf(steppedIn);
        }
    }

    @Test
    void productOwnerHoldsTheTwentyThreePlatformActionsAndNoApprovals() {
        assertThat(PlatformPermissionService.standardActions(PlatformRole.PRODUCT_OWNER))
                .hasSize(23)
                .doesNotContain("TENANT_EXPORT_APPROVE", "TENANT_DELETION_APPROVE",
                        "RETENTION_CHANGE_APPROVE", "APPROVAL_DELEGATE");
    }

    @Test
    void productOwnerCannotApproveDataProtectionRequests() {
        PlatformPrincipal owner = principal(PlatformRole.PRODUCT_OWNER);
        for (String action : List.of("TENANT_EXPORT_APPROVE", "TENANT_DELETION_APPROVE", "RETENTION_CHANGE_APPROVE")) {
            assertThatThrownBy(() -> service.authorize(owner, action)).isInstanceOf(UnauthorizedException.class);
        }
    }

    @Test
    void customRoleReplacesStandardRights() {
        StubLookup lookup = new StubLookup();
        lookup.customActions = Set.of("OVERDUE_FLAG");
        PlatformPermissionService custom = withLookup(lookup);
        PlatformPrincipal staff = principal(PlatformRole.BILLING_MANAGER);

        assertThatCode(() -> custom.authorize(staff, "OVERDUE_FLAG")).doesNotThrowAnyException();
        assertThatThrownBy(() -> custom.authorize(staff, "INVOICE_MANAGE")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void inactiveCustomRoleDeniesEverything() {
        StubLookup lookup = new StubLookup();
        lookup.customActions = Set.of();
        PlatformPermissionService custom = withLookup(lookup);
        PlatformPrincipal staff = principal(PlatformRole.PLATFORM_ADMIN);

        assertThatThrownBy(() -> custom.authorize(staff, "PLATFORM_AUDIT_VIEW")).isInstanceOf(UnauthorizedException.class);
        assertThat(custom.heldActions(staff)).isEmpty();
    }

    @Test
    void handedOverActionNeedsAProductOwnerToStepIn() {
        StubLookup lookup = new StubLookup();
        lookup.handedOver = Set.of("RELEASE_MANAGE");
        PlatformPermissionService handed = withLookup(lookup);
        PlatformPrincipal owner = principal(PlatformRole.PRODUCT_OWNER);

        assertThatThrownBy(() -> handed.authorize(owner, "RELEASE_MANAGE"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("STEP_IN_REQUIRED");
        assertThat(handed.actionsNeedingStepIn(owner)).containsExactly("RELEASE_MANAGE");
        assertThatCode(() -> handed.authorize(owner, "PLAN_MANAGE")).doesNotThrowAnyException();

        lookup.steppedIn = Set.of("RELEASE_MANAGE");
        assertThatCode(() -> handed.authorize(owner, "RELEASE_MANAGE")).doesNotThrowAnyException();
        assertThat(handed.actionsNeedingStepIn(owner)).isEmpty();
    }

    @Test
    void stepInRuleDoesNotApplyToOtherStaff() {
        StubLookup lookup = new StubLookup();
        lookup.handedOver = Set.of("RELEASE_MANAGE");
        PlatformPermissionService handed = withLookup(lookup);

        assertThatCode(() -> handed.authorize(principal(PlatformRole.RELEASE_MANAGER), "RELEASE_MANAGE"))
                .doesNotThrowAnyException();
    }

    @Test
    void authorizeAnyPassesOnOneHeldActionAndFailsOnNone() {
        assertThatCode(() -> service.authorizeAny(principal(PlatformRole.BILLING_MANAGER), List.of("PLAN_MANAGE", "INVOICE_MANAGE")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.authorizeAny(principal(PlatformRole.RELEASE_MANAGER), List.of("PLAN_MANAGE", "INVOICE_MANAGE")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void noPlatformRoleCanGrantSupportAccess() {
        for (PlatformRole role : PlatformRole.values()) {
            PlatformPrincipal p = principal(role);
            assertThatThrownBy(() -> service.authorize(p, "SUPPORT_ACCESS_GRANT"))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    @Test
    void platformAdminRightsMatchTheMatrix() {
        PlatformPrincipal admin = principal(PlatformRole.PLATFORM_ADMIN);

        assertThatCode(() -> service.authorize(admin, "SUPPORT_TICKET_MANAGE")).doesNotThrowAnyException();
        for (String action : List.of("SUPPORT_ACCESS_USE", "PLATFORM_REPORTS_VIEW")) {
            assertThatThrownBy(() -> service.authorize(admin, action))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    @Test
    void supportAccessUseIsLimitedToSupportManagerAndProductOwner() {
        for (PlatformRole role : PlatformRole.values()) {
            PlatformPrincipal p = principal(role);
            if (role == PlatformRole.SUPPORT_MANAGER || role == PlatformRole.PRODUCT_OWNER) {
                assertThatCode(() -> service.authorize(p, "SUPPORT_ACCESS_USE")).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> service.authorize(p, "SUPPORT_ACCESS_USE"))
                        .isInstanceOf(UnauthorizedException.class);
            }
        }
    }

    @Test
    void platformUserManageIsAllowedForProductOwnerOnly() {
        for (PlatformRole role : PlatformRole.values()) {
            PlatformPrincipal p = principal(role);
            if (role == PlatformRole.PRODUCT_OWNER) {
                assertThatCode(() -> service.authorize(p, "PLATFORM_USER_MANAGE")).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> service.authorize(p, "PLATFORM_USER_MANAGE"))
                        .isInstanceOf(UnauthorizedException.class);
            }
        }
    }
}
