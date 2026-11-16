package com.edu360.api.platformops.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.config.SupportPrincipal;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

class SupportSessionAuthenticationFilterTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID TICKET_ID = UUID.randomUUID();
    private static final UUID GRANT_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID PLATFORM_USER_ID = UUID.randomUUID();

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private Fixture fixture(Instant expiry) {
        SupportSessionTokenService tokenService = new SupportSessionTokenService("0123456789abcdef0123456789abcdef");
        SupportSession session = mock(SupportSession.class);
        when(session.getSupportSessionId()).thenReturn(SESSION_ID);
        when(session.getGrantId()).thenReturn(GRANT_ID);
        when(session.getPlatformUserId()).thenReturn(PLATFORM_USER_ID);
        when(session.getIssuedAt()).thenReturn(Instant.now().minusSeconds(5));
        when(session.getExpiresAt()).thenReturn(expiry);

        SupportAccessGrant grant = mock(SupportAccessGrant.class);
        when(grant.getSupportAccessGrantId()).thenReturn(GRANT_ID);
        when(grant.getTenantId()).thenReturn(TENANT_ID);
        when(grant.getTicketId()).thenReturn(TICKET_ID);
        when(grant.getInstitutionId()).thenReturn(null);
        when(grant.getExpiresAt()).thenReturn(Instant.now().plusSeconds(7200));
        when(grant.getWithdrawnAt()).thenReturn(null);

        Tenant tenant = Tenant.builder().tenantId(TENANT_ID).state(TenantState.ACTIVE)
                .isolationMode(IsolationMode.SHARED).subdomain("tenant").build();
        SupportSessionRepository sessions = mock(SupportSessionRepository.class);
        SupportAccessGrantRepository grants = mock(SupportAccessGrantRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformAuditWriter platformAudit = mock(PlatformAuditWriter.class);
        AuditLogService tenantAudit = mock(AuditLogService.class);
        when(sessions.findBySupportSessionIdAndEndedAtIsNull(SESSION_ID)).thenReturn(Optional.of(session));
        when(grants.findById(GRANT_ID)).thenReturn(Optional.of(grant));
        when(tenants.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(4);

        PlatformPrincipal actor = new PlatformPrincipal(PLATFORM_USER_ID, null, null, false);
        String token = tokenService.issue(session, grant, actor);
        SupportSessionAuthenticationFilter filter = new SupportSessionAuthenticationFilter(
                tokenService, sessions, grants, tenants, jdbc, platformAudit, tenantAudit);
        return new Fixture(filter, token, sessions, tenantAudit, platformAudit);
    }

    private MockHttpServletRequest request(String method, String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + token);
        request.setServerName("tenant.edu360.local");
        return request;
    }

    @Test
    void supportSessionAuthenticatesOnlyAllowListedTenantReadOperationsAndAuditsBothStreams() throws Exception {
        Fixture fixture = fixture(Instant.now().plusSeconds(1800));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        fixture.filter.doFilter(request("GET", "/api/tenants/" + TENANT_ID + "/classes", fixture.token), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(((SupportPrincipal) org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal()).tenantId()).isEqualTo(TENANT_ID);
        verify(fixture.tenantAudit).recordSupportView(any(SupportPrincipal.class), eq("/api/tenants/" + TENANT_ID + "/classes"));
        verify(fixture.platformAudit).record(any(PlatformPrincipal.class), eq(TENANT_ID),
                eq("SUPPORT_SESSION_VIEW"), eq("SUPPORT_ACCESS_USE"), eq("SUPPORT_SESSION"), eq(SESSION_ID));
    }

    @Test
    void supportSessionRejectsWritesBeforeAuthorization() throws Exception {
        Fixture fixture = fixture(Instant.now().plusSeconds(1800));
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(request("POST", "/api/tenants/" + TENANT_ID + "/classes", fixture.token),
                response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("SUPPORT_READ_ONLY");
    }

    @Test
    void supportSessionRejectsPlatformAndMePaths() throws Exception {
        Fixture fixture = fixture(Instant.now().plusSeconds(1800));
        MockHttpServletResponse platformResponse = new MockHttpServletResponse();
        fixture.filter.doFilter(request("GET", "/api/platform/support-tickets", fixture.token),
                platformResponse, new MockFilterChain());
        assertThat(platformResponse.getStatus()).isEqualTo(403);
        assertThat(platformResponse.getContentAsString()).contains("SUPPORT_SESSION_PATH_FORBIDDEN");

        MockHttpServletResponse meResponse = new MockHttpServletResponse();
        fixture.filter.doFilter(request("GET", "/api/me/profile", fixture.token),
                meResponse, new MockFilterChain());
        assertThat(meResponse.getStatus()).isEqualTo(403);
        assertThat(meResponse.getContentAsString()).contains("SUPPORT_SESSION_PATH_FORBIDDEN");
    }

    @Test
    void expiredSessionEndsWith401() throws Exception {
        Fixture fixture = fixture(Instant.now().minusSeconds(5));
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(request("GET", "/api/tenants/" + TENANT_ID + "/classes", fixture.token),
                response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SUPPORT_SESSION_ENDED");
        verify(fixture.sessions).save(org.mockito.ArgumentMatchers.argThat(s -> "EXPIRED".equals(s.getEndReason())));
    }

    private record Fixture(SupportSessionAuthenticationFilter filter, String token,
                           SupportSessionRepository sessions, AuditLogService tenantAudit,
                           PlatformAuditWriter platformAudit) {}
}
