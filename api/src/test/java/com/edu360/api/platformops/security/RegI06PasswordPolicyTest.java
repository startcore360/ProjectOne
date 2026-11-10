package com.edu360.api.platformops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.auth.PasswordPolicyService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.config.keycloak.KeycloakRealmSecurityService;
import com.edu360.api.config.keycloak.KeycloakRealmSecurityService.RealmSecuritySettings;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.tenant.TenantRepository;

/** Sprint 1 B3 / I-06: the saved security policy is what password changes and Keycloak realms use. */
class RegI06PasswordPolicyTest {

    private static SecurityPolicyService policyWithMinLength(int min) {
        PlatformSettingValueRepository repository = mock(PlatformSettingValueRepository.class);
        SecurityPolicyService json = new SecurityPolicyService(null);
        SecurityPolicyService.SecurityPolicy saved = new SecurityPolicyService.SecurityPolicy(
                min, false, false, 5, 15, 30, true, 90, 120);
        PlatformSettingValue row = PlatformSettingValue.builder().settingValueId(UUID.randomUUID())
                .settingKey(SecurityPolicyService.SETTING_KEY_SECURITY_POLICY).value(json.toJson(saved))
                .versionNo(1).status("ACTIVE").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(repository.findBySettingKeyAndStatus(SecurityPolicyService.SETTING_KEY_SECURITY_POLICY, "ACTIVE"))
                .thenReturn(Optional.of(row));
        return new SecurityPolicyService(repository);
    }

    @Test
    @DisplayName("REG-I06-1: saving min length 10 makes a 9-character password fail at change")
    void regI06_1_savedMinLengthIsApplied() {
        PasswordPolicyService passwords = new PasswordPolicyService(policyWithMinLength(10));

        assertThatThrownBy(() -> passwords.validate("Abcdefg-1"))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("PASSWORD_TOO_SHORT"));
        passwords.validate("Abcdefgh-1x");
    }

    @Test
    @DisplayName("REG-I06-2: a new tenant realm gets the current policy")
    void regI06_2_newRealmGetsCurrentPolicy() {
        KeycloakRealmSecurityService realms = mock(KeycloakRealmSecurityService.class);
        SecurityPolicyService policy = policyWithMinLength(10);
        RealmSecuritySyncService sync = new RealmSecuritySyncService(policy, realms, new KeycloakProperties(),
                mock(TenantRepository.class), false);

        sync.applyToRealm("new-tenant-realm");

        RealmSecuritySettings expected = RealmSecuritySyncService.settingsOf(policy.current());
        assertThat(expected.minPasswordLength()).isEqualTo(10);
        verify(realms).apply("new-tenant-realm", expected);
    }

    @Test
    @DisplayName("REG-I06-3: a denylisted password (Password@123) is refused")
    void regI06_3_denylistedPasswordRefused() {
        PasswordPolicyService passwords = new PasswordPolicyService(new SecurityPolicyService(null));

        assertThatThrownBy(() -> passwords.validate("Password@123"))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("PASSWORD_TOO_COMMON"));
    }

    @Test
    @DisplayName("REG-I06-4: a minimum length below 8 is refused with a business-rule error (422)")
    void regI06_4_minLengthBelowEightRefused() {
        PlatformPermissionService permissions = com.edu360.api.platformops.PlatformTestSupport.permissions(
                com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());
        PlatformSecurityPolicyController controller =
                new PlatformSecurityPolicyController(permissions, null, mock(com.edu360.api.platformops.security.PlatformSettingValueRepository.class), mock(com.edu360.api.platformops.role.PlatformAuditWriter.class), null);
        PlatformPrincipal owner = new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PRODUCT_OWNER, "po@edu360.local", false);
        var auth = new UsernamePasswordAuthenticationToken(owner, null, List.of());
        var request = new PlatformSecurityPolicyController.UpdateSecurityPolicyRequest(7, false, false, 5, 15, 30, true, 90);

        assertThatThrownBy(() -> controller.updatePolicy(request, auth))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("INVALID_MIN_PASSWORD_LENGTH"));
    }
}
