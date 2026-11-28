package com.edu360.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.edu360.api.tenant.LoginTenantResolver;
import com.edu360.api.tenant.Tenant;

/** Host-binding regression tests, including API-signed support-session principals. */
class TokenHostMismatchIT {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportTokenTenantMustMatchHostResolvedTenant() throws Exception {
        UUID hostTenantId = UUID.randomUUID();
        UUID tokenTenantId = UUID.randomUUID();
        Tenant hostTenant = Tenant.builder().tenantId(hostTenantId).build();
        LoginTenantResolver resolver = mock(LoginTenantResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LoginTenantResolver.TenantLogin(hostTenant));
        TokenHostMatchingFilter filter = new TokenHostMatchingFilter(resolver);
        SupportPrincipal support = new SupportPrincipal(UUID.randomUUID(), tokenTenantId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(support, "signed-support-token", List.of()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenants/" + tokenTenantId + "/classes");
        request.setServerName("other-tenant.edu360.local");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("TENANT_HOST_MISMATCH");
    }

    @Test
    void supportTokenPassesWhenHostTenantMatches() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Tenant hostTenant = Tenant.builder().tenantId(tenantId).build();
        LoginTenantResolver resolver = mock(LoginTenantResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LoginTenantResolver.TenantLogin(hostTenant));
        TokenHostMatchingFilter filter = new TokenHostMatchingFilter(resolver);
        SupportPrincipal support = new SupportPrincipal(UUID.randomUUID(), tenantId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(support, "signed-support-token", List.of()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenants/" + tenantId + "/classes");
        request.setServerName("tenant.edu360.local");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void platformTokenMustResolveToPlatformHost() throws Exception {
        LoginTenantResolver resolver = mock(LoginTenantResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LoginTenantResolver.TenantLogin(Tenant.builder().tenantId(UUID.randomUUID()).build()));
        TokenHostMatchingFilter filter = new TokenHostMatchingFilter(resolver);
        PlatformPrincipal platform = new PlatformPrincipal(UUID.randomUUID(),
                com.edu360.api.platform.PlatformRole.PLATFORM_ADMIN, "platform@example.test", false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(platform, "platform-token", List.of()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/platform/tenants");
        request.setServerName("tenant.edu360.local");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("TENANT_HOST_MISMATCH");
    }
}
