package com.edu360.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.edu360.api.platform.PlatformRole;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

class SupportPrincipalFilterBypassTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PLATFORM_USER_ID = UUID.randomUUID();

    private void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "token", List.of()));
    }

    private MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/tenants/" + TENANT_ID + "/resource");
        request.setServerName("tenant.edu360.local");
        return request;
    }

    @AfterEach
    void clearContexts() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeRoleFilterAllowsSupportPrincipalWithoutWeakeningTenantRule() throws Exception {
        ActiveRoleRequiredFilter filter = new ActiveRoleRequiredFilter();
        TenantContext.set(TENANT_ID, IsolationMode.SHARED, null, "ALL", java.util.Set.of(), null, null, null, true);
        authenticate(new SupportPrincipal(PLATFORM_USER_ID, TENANT_ID, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, null));
        MockHttpServletResponse supportResponse = new MockHttpServletResponse();
        filter.doFilter(request("GET"), supportResponse, new MockFilterChain());
        assertThat(supportResponse.getStatus()).isEqualTo(200);

        authenticate(new TenantPrincipal(TENANT_ID, UUID.randomUUID(), null, "Tenant user", false));
        MockHttpServletResponse tenantResponse = new MockHttpServletResponse();
        filter.doFilter(request("GET"), tenantResponse, new MockFilterChain());
        assertThat(tenantResponse.getStatus()).isEqualTo(409);
    }

    @Test
    void passwordFilterAllowsSupportPrincipalButStillBlocksTenantNeedingPasswordChange() throws Exception {
        MustChangePasswordFilter filter = new MustChangePasswordFilter();
        authenticate(new SupportPrincipal(PLATFORM_USER_ID, TENANT_ID, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, null));
        MockHttpServletResponse supportResponse = new MockHttpServletResponse();
        filter.doFilter(request("POST"), supportResponse, new MockFilterChain());
        assertThat(supportResponse.getStatus()).isEqualTo(200);

        authenticate(new TenantPrincipal(TENANT_ID, UUID.randomUUID(), null, "Tenant user", true));
        MockHttpServletResponse tenantResponse = new MockHttpServletResponse();
        filter.doFilter(request("POST"), tenantResponse, new MockFilterChain());
        assertThat(tenantResponse.getStatus()).isEqualTo(403);
    }

    @Test
    void trialFilterAllowsSupportPrincipalButStillBlocksExpiredTrialTenant() throws Exception {
        TenantRepository tenants = mock(TenantRepository.class);
        TrialReadOnlyFilter filter = new TrialReadOnlyFilter(tenants, java.time.Clock.systemUTC());
        authenticate(new SupportPrincipal(PLATFORM_USER_ID, TENANT_ID, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, null));
        MockHttpServletResponse supportResponse = new MockHttpServletResponse();
        filter.doFilter(request("POST"), supportResponse, new MockFilterChain());
        assertThat(supportResponse.getStatus()).isEqualTo(200);
        verifyNoInteractions(tenants);

        Tenant expiredTrial = Tenant.builder().tenantId(TENANT_ID).state(TenantState.TRIAL)
                .trialEndsAt(Instant.now().minusSeconds(5)).build();
        when(tenants.findById(TENANT_ID)).thenReturn(Optional.of(expiredTrial));
        authenticate(new TenantPrincipal(TENANT_ID, UUID.randomUUID(), null, "Tenant user", false));
        MockHttpServletResponse tenantResponse = new MockHttpServletResponse();
        filter.doFilter(request("POST"), tenantResponse, new MockFilterChain());
        assertThat(tenantResponse.getStatus()).isEqualTo(403);
    }
}
