package com.edu360.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.edu360.api.identity.UserType;
import com.edu360.api.tenant.LoginTenantResolver;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantState;

class TokenHostMatchingFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsTenantTokenPresentedOnAnotherTenantHost() throws Exception {
        UUID tokenTenantId = UUID.randomUUID();
        Tenant hostTenant = tenant(UUID.randomUUID(), "host-tenant");
        LoginTenantResolver resolver = mock(LoginTenantResolver.class);
        when(resolver.resolve(any())).thenReturn(new LoginTenantResolver.TenantLogin(hostTenant));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantPrincipal(tokenTenantId, UUID.randomUUID(), UserType.STAFF, "Test", false),
                        "n/a", List.of()));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Host", "host-tenant.edu360.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new TokenHostMatchingFilter(resolver).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("TENANT_HOST_MISMATCH");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void allowsTenantTokenOnItsOwnHost() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Tenant hostTenant = tenant(tenantId, "same-tenant");
        LoginTenantResolver resolver = mock(LoginTenantResolver.class);
        when(resolver.resolve(any())).thenReturn(new LoginTenantResolver.TenantLogin(hostTenant));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantPrincipal(tenantId, UUID.randomUUID(), UserType.STAFF, "Test", false),
                        "n/a", List.of()));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Host", "same-tenant.edu360.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new TokenHostMatchingFilter(resolver).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static Tenant tenant(UUID id, String subdomain) {
        return Tenant.builder()
                .tenantId(id)
                .subdomain(subdomain)
                .keycloakRealm(subdomain + "-realm")
                .state(TenantState.ACTIVE)
                .build();
    }
}
