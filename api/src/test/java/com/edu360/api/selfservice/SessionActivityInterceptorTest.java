package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.UserType;

class SessionActivityInterceptorTest {

    private final SessionService sessionService = mock(SessionService.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final TenantPrincipal principal =
            new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), UserType.STAFF, "User", false);
    private final MutableClock clock = new MutableClock();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(String sid) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("sid")).thenReturn(sid);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, jwt, List.of()));
    }

    private boolean call(SessionActivityInterceptor interceptor, MockHttpServletResponse response) throws Exception {
        return interceptor.preHandle(request, response, null);
    }

    // ------------------------------------------------------------------------- signed-out check

    @Test
    void signedOutSession_isRefusedWith401AndNoActivityIsRecorded() throws Exception {
        signIn("sid-gone");
        when(sessionService.isSessionSignedOut(principal, "sid-gone")).thenReturn(true);
        SessionActivityInterceptor interceptor = new SessionActivityInterceptor(sessionService, clock);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(call(interceptor, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("signed out");
        verify(sessionService, never()).recordActivity(any(), any());
    }

    @Test
    void signedOutAnswer_isRememberedWithoutAskingAgain() throws Exception {
        signIn("sid-gone");
        when(sessionService.isSessionSignedOut(principal, "sid-gone")).thenReturn(true);
        SessionActivityInterceptor interceptor = new SessionActivityInterceptor(sessionService, clock);

        call(interceptor, new MockHttpServletResponse());
        assertThat(call(interceptor, new MockHttpServletResponse())).isFalse();

        verify(sessionService, times(1)).isSessionSignedOut(principal, "sid-gone");
    }

    @Test
    void signOutTakesEffectOnTheVeryNextRequest_noCacheWindow() throws Exception {
        signIn("sid-1");
        when(sessionService.isSessionSignedOut(principal, "sid-1")).thenReturn(false, true);
        SessionActivityInterceptor interceptor = new SessionActivityInterceptor(sessionService, clock);

        assertThat(call(interceptor, new MockHttpServletResponse())).isTrue();
        // Signed out from another device; the next call - same millisecond - is already refused.
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(call(interceptor, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);

        verify(sessionService, times(2)).isSessionSignedOut(principal, "sid-1");
    }

    // -------------------------------------------------------------------------------- activity

    @Test
    void recordsActivityAtMostOncePerMinutePerSession() throws Exception {
        signIn("sid-1");
        SessionActivityInterceptor interceptor = new SessionActivityInterceptor(sessionService, clock);

        call(interceptor, new MockHttpServletResponse());
        call(interceptor, new MockHttpServletResponse());
        verify(sessionService, times(1)).recordActivity(principal, "sid-1");

        clock.now += SessionActivityInterceptor.THROTTLE_MS;
        call(interceptor, new MockHttpServletResponse());
        verify(sessionService, times(2)).recordActivity(principal, "sid-1");
    }

    @Test
    void skipsUnauthenticatedRequestsAndTokensWithoutSessionId() throws Exception {
        SessionActivityInterceptor interceptor = new SessionActivityInterceptor(sessionService, clock);
        assertThat(call(interceptor, new MockHttpServletResponse())).isTrue();

        signIn(null);
        assertThat(call(interceptor, new MockHttpServletResponse())).isTrue();

        verify(sessionService, never()).recordActivity(any(), any());
        verify(sessionService, never()).isSessionSignedOut(any(), any());
    }

    private static final class MutableClock extends Clock {
        long now = 1_000_000;

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now);
        }
    }
}
