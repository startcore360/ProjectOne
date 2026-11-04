package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;

class PlatformPermissionServiceTest {

    private final PlatformPermissionService service = new PlatformPermissionService();

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
}
