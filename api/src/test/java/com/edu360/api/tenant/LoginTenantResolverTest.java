package com.edu360.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.edu360.api.config.keycloak.KeycloakProperties;
import com.edu360.api.exception.InvalidCredentialsException;

/** Which tenant (or the platform) a sign-in address means - dot and hyphen address styles. */
class LoginTenantResolverTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private LoginTenantResolver resolver;
    private Tenant abc;

    @BeforeEach
    void setUp() {
        KeycloakProperties props = new KeycloakProperties();
        props.setPlatformRealm("edu360-platform");
        props.setLocalDevHostSuffix(".edu360.local, -edu360.duckdns.org");
        resolver = new LoginTenantResolver(props, tenantRepository);
        abc = Tenant.builder().tenantId(UUID.randomUUID()).subdomain("abc").keycloakRealm("abc-realm")
                .state(TenantState.ACTIVE).build();
        when(tenantRepository.findBySubdomain("abc")).thenReturn(Optional.of(abc));
    }

    private LoginTenantResolver.Resolution resolve(String host) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName(host);
        return resolver.resolve(request);
    }

    @Test
    void dotStyle_findsTheTenant() {
        assertThat(resolve("abc.edu360.local")).isEqualTo(new LoginTenantResolver.TenantLogin(abc));
    }

    @Test
    void hyphenDuckDnsStyle_findsTheSameTenant() {
        assertThat(resolve("abc-edu360.duckdns.org")).isEqualTo(new LoginTenantResolver.TenantLogin(abc));
        assertThat(resolve("ABC-EDU360.DUCKDNS.ORG")).isEqualTo(new LoginTenantResolver.TenantLogin(abc));
    }

    @Test
    void platformAddress_inBothStyles() {
        assertThat(resolve("platform.edu360.local")).isEqualTo(new LoginTenantResolver.PlatformLogin("edu360-platform"));
        assertThat(resolve("platform-edu360.duckdns.org")).isEqualTo(new LoginTenantResolver.PlatformLogin("edu360-platform"));
    }

    @Test
    void lookAlikeAndNestedAddresses_areRefused() {
        when(tenantRepository.findBySubdomain(anyString())).thenReturn(Optional.empty());
        when(tenantRepository.findBySubdomain("abc")).thenReturn(Optional.of(abc));

        // Ends with something else entirely: looked up as the whole name, which is no tenant.
        assertThatThrownBy(() -> resolve("abc-edu360.duckdns.org.evil.com")).isInstanceOf(InvalidCredentialsException.class);
        // Extra label in front: "x.abc" is never a tenant subdomain.
        assertThatThrownBy(() -> resolve("x.abc-edu360.duckdns.org")).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> resolve("x.abc.edu360.local")).isInstanceOf(InvalidCredentialsException.class);
        // The bare ending is not a tenant either.
        assertThatThrownBy(() -> resolve("edu360.duckdns.org")).isInstanceOf(InvalidCredentialsException.class);
        verify(tenantRepository, never()).findBySubdomain("x");
    }

    @Test
    void suspendedTenant_isRefused() {
        Tenant suspended = Tenant.builder().tenantId(UUID.randomUUID()).subdomain("old").keycloakRealm("old-realm")
                .state(TenantState.SUSPENDED).build();
        when(tenantRepository.findBySubdomain("old")).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> resolve("old-edu360.duckdns.org")).isInstanceOf(InvalidCredentialsException.class);
    }
}
