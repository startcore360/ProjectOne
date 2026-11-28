package com.edu360.api.platformops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.config.keycloak.KeycloakRealmSecurityService;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

class RealmSecuritySyncServiceTest {

    @Test
    @DisplayName("Preview is read-only and captures the platform and tenant realm targets")
    void previewDoesNotCallKeycloak() {
        KeycloakProperties properties = new KeycloakProperties();
        properties.setPlatformRealm("platform");
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findAll()).thenReturn(List.of(
                Tenant.builder().tenantId(UUID.randomUUID()).keycloakRealm("tenant-a").build()));
        KeycloakRealmSecurityService keycloak = mock(KeycloakRealmSecurityService.class);
        RealmSecuritySyncService service = new RealmSecuritySyncService(
                new SecurityPolicyService(null), keycloak, properties, tenants, false);

        var preview = service.previewAllRealms();

        assertThat(preview.previewId()).isNotNull();
        assertThat(preview.realms()).containsExactly("platform", "tenant-a");
        // D1_DEFAULT.minPasswordLength() is 12, not the 8 cited in SecurityPolicyService's own
        // javadoc (see T1-DISC-03, an open policy decision this test does not resolve).
        assertThat(preview.settings().minPasswordLength()).isEqualTo(12);
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("Failed realm results are sanitized and retry targets only that failed realm")
    void retryOnlyReappliesFailedRealmAndDoesNotExposeProviderMessage() {
        KeycloakProperties properties = new KeycloakProperties();
        properties.setPlatformRealm("platform");
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findAll()).thenReturn(List.of(
                Tenant.builder().tenantId(UUID.randomUUID()).keycloakRealm("tenant-ok").build(),
                Tenant.builder().tenantId(UUID.randomUUID()).keycloakRealm("tenant-fails").build()));
        KeycloakRealmSecurityService keycloak = mock(KeycloakRealmSecurityService.class);
        SecurityPolicyService policy = new SecurityPolicyService(null);
        var settings = RealmSecuritySyncService.settingsOf(policy.current());
        doThrow(new IllegalStateException("client_secret=do-not-leak"))
                .when(keycloak).apply("tenant-fails", settings);
        RealmSecuritySyncService service = new RealmSecuritySyncService(policy, keycloak, properties, tenants, false);

        var preview = service.previewAllRealms();
        var firstRun = service.applyPreviewedRealms(preview.previewId());

        assertThat(firstRun.results()).hasSize(3);
        assertThat(firstRun.results().stream().filter(r -> !r.applied()).findFirst().orElseThrow().error())
                .isEqualTo("REALM_SYNC_FAILED");
        assertThat(firstRun.results().toString()).doesNotContain("do-not-leak");

        var retry = service.retryFailedRealms(firstRun.runId());

        assertThat(retry.results()).extracting(RealmSecuritySyncService.RealmSyncResult::realm)
                .containsExactly("tenant-fails");
        verify(keycloak, times(2)).apply("tenant-fails", settings);
        verify(keycloak, times(1)).apply("platform", settings);
        verify(keycloak, times(1)).apply("tenant-ok", settings);
        assertThatThrownBy(() -> service.retryFailedRealms(firstRun.runId()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
