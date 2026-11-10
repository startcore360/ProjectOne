package com.edu360.api.platformops.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.platform.PlatformRole;

class PlatformActionPolicyTest {

    @Test
    void reservedActionsCanNeverBeGiven() {
        for (String action : List.of("TENANT_EXPORT_APPROVE", "TENANT_DELETION_APPROVE", "RETENTION_CHANGE_APPROVE",
                "APPROVAL_DELEGATE", "PLATFORM_USER_MANAGE", "TENANT_DELETION_CONFIRM")) {
            assertThat(PlatformActionPolicy.reservedReason(action)).isPresent();
            assertThatThrownBy(() -> PlatformActionPolicy.validate(List.of(action), PlatformRole.PLATFORM_ADMIN))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Test
    void billingBasedRoleCannotGiveDiscounts() {
        assertThatThrownBy(() -> PlatformActionPolicy.validate(List.of("DISCOUNT_APPLY"), PlatformRole.BILLING_MANAGER))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Billing Manager");
    }

    @Test
    void onlySupportBasedRolesUseTenantGrantedAccess() {
        assertThatCode(() -> PlatformActionPolicy.validate(List.of("SUPPORT_ACCESS_USE"), PlatformRole.SUPPORT_MANAGER))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PlatformActionPolicy.validate(List.of("SUPPORT_ACCESS_USE"), PlatformRole.RELEASE_MANAGER))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void anActionTheProductOwnerDoesNotHoldCanNotBeGiven() {
        assertThatThrownBy(() -> PlatformActionPolicy.validate(List.of("USER_CREATE"), PlatformRole.PLATFORM_ADMIN))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void productOwnerAndDataProtectionOfficerCanNotBeCustomised() {
        assertThatThrownBy(() -> PlatformActionPolicy.requireCustomisable(PlatformRole.PRODUCT_OWNER))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> PlatformActionPolicy.requireCustomisable(PlatformRole.DATA_PROTECTION_OFFICER))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatCode(() -> PlatformActionPolicy.requireCustomisable(PlatformRole.RELEASE_MANAGER))
                .doesNotThrowAnyException();
    }
}
