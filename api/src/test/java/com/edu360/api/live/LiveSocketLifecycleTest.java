package com.edu360.api.live;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.edu360.api.config.TenantIssuerAuthenticationManagerResolver;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.UserType;

class LiveSocketLifecycleTest {
    private record Authenticated(LiveSocketHandler handler, WebSocketSession session) {}

    private Authenticated authenticatedConnection() throws Exception {
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
        TenantPrincipal principal = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(),
                UserType.STAFF, "Staff", false);
        Authentication authenticated = mock(Authentication.class);
        when(authenticated.getPrincipal()).thenReturn(principal);
        when(authenticated.getCredentials()).thenReturn(jwt);
        when(manager.authenticate(any(Authentication.class))).thenReturn(authenticated);

        LiveSocketHandler handler = new LiveSocketHandler(managers);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("lifecycle-session");
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage(
                "{\"type\":\"auth\",\"token\":\"" + token + "\"}"));
        return new Authenticated(handler, session);
    }

    @Test
    void heartbeatTimeoutClosesAuthenticatedConnection() throws Exception {
        Authenticated connection = authenticatedConnection();
        @SuppressWarnings("unchecked")
        Map<String, Object> connections = (Map<String, Object>)
                ReflectionTestUtils.getField(connection.handler(), "connections");
        Object state = connections.get(connection.session().getId());
        ReflectionTestUtils.setField(state, "lastPong", Instant.now().minusSeconds(61));

        connection.handler().maintainConnections();

        verify(connection.session()).close(argThat(status -> status.getCode() == 4401));
    }

    @Test
    void expiredTokenRequestsReauthenticationThenClosesAfterGracePeriod() throws Exception {
        Authenticated connection = authenticatedConnection();
        @SuppressWarnings("unchecked")
        Map<String, Object> connections = (Map<String, Object>)
                ReflectionTestUtils.getField(connection.handler(), "connections");
        Object state = connections.get(connection.session().getId());
        ReflectionTestUtils.setField(state, "expiresAt", Instant.now().minusSeconds(1));

        connection.handler().maintainConnections();

        verify(connection.session()).sendMessage(argThat(message ->
                message instanceof TextMessage text && text.getPayload().contains("reauth_required")));
        ReflectionTestUtils.setField(state, "reauthDeadline", Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(state, "lastPong", Instant.now());

        connection.handler().maintainConnections();

        verify(connection.session()).close(argThat(status -> status.getCode() == 4401));
    }
}
