package com.edu360.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.edu360.api.identity.UserType;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

/** L-05 / R2-24 / E1-S7: an ended trial is read-only, and the refusal says when it ended. */
class TrialReadOnlyFilterTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TrialReadOnlyFilter filter = new TrialReadOnlyFilter(tenants, Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(String method, Instant trialEndsAt) throws Exception {
        when(tenants.findById(TENANT)).thenReturn(Optional.of(
                Tenant.builder().tenantId(TENANT).state(TenantState.TRIAL).trialEndsAt(trialEndsAt).build()));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new TenantPrincipal(TENANT, UUID.randomUUID(), UserType.STAFF, "Asha", false), null, java.util.List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/tenants/" + TENANT + "/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void aWriteAfterTheTrialEndedIs403WithTheEndDate() throws Exception {
        MockHttpServletResponse response = run("POST", Instant.parse("2026-11-10T00:00:00Z"));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"TRIAL_EXPIRED\"")
                .contains("\"trialEndsAt\":\"2026-11-10T00:00:00Z\"");
    }

    @Test
    void readingStaysAllowedAfterTheTrialEnded() throws Exception {
        assertThat(run("GET", Instant.parse("2026-11-10T00:00:00Z")).getStatus()).isEqualTo(200);
    }

    @Test
    void aRunningTrialIsNotBlocked() throws Exception {
        assertThat(run("POST", Instant.parse("2026-12-01T00:00:00Z")).getStatus()).isEqualTo(200);
    }
}
