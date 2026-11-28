package com.edu360.api.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.edu360.api.config.TenantIssuerAuthenticationManagerResolver;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.UserType;

class LiveSocketConnectionLimitTest {
    @Test
    void sixthAuthenticatedConnectionClosesOldestSessionWithLimitStatus() throws Exception {
        TenantIssuerAuthenticationManagerResolver managers = mock(TenantIssuerAuthenticationManagerResolver.class);
        AuthenticationManager manager = mock(AuthenticationManager.class);
        String issuer = "https://issuer.example/tenant";
        when(managers.resolve(issuer)).thenReturn(manager);
        String claims = "{\"iss\":\"" + issuer + "\"}";
        String token = "e30." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + ".signature";
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "none").claim("iss", issuer)
                .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantPrincipal principal = new TenantPrincipal(tenantId, userId, UserType.STAFF, "Staff", false);
        Authentication authenticated = mock(Authentication.class);
        when(authenticated.getPrincipal()).thenReturn(principal);
        when(authenticated.getCredentials()).thenReturn(jwt);
        when(manager.authenticate(any(Authentication.class))).thenReturn(authenticated);

        LiveSocketHandler handler = new LiveSocketHandler(managers);
        List<WebSocketSession> sessions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn("connection-" + i);
            when(session.isOpen()).thenReturn(true);
            sessions.add(session);
            handler.afterConnectionEstablished(session);
            handler.handleMessage(session, new TextMessage(
                    "{\"type\":\"auth\",\"token\":\"" + token + "\"}"));
            Thread.sleep(3);
        }

        int closedForLimit = 0;
        for (WebSocketSession session : sessions) {
            try {
                verify(session).close(argThat(status -> status.getCode() == 4408));
                closedForLimit++;
            } catch (AssertionError ignored) {
                // Only the oldest connection is evicted when the sixth connection is added.
            }
        }
        assertThat(closedForLimit).isEqualTo(1);
    }
}
