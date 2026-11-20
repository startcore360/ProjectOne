package com.edu360.api.live;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.UserType;

import com.edu360.api.config.TenantIssuerAuthenticationManagerResolver;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class LiveSocketHandlerTest {

    @Test
    void oversizedClientMessageClosesConnectionWithMessageTooBigStatus() throws Exception {
        TenantIssuerAuthenticationManagerResolver managers = mock(TenantIssuerAuthenticationManagerResolver.class);
        LiveSocketHandler handler = new LiveSocketHandler(managers);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("oversized-message-session");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("x".repeat(8_193)));

        verify(session).close(argThat(status -> status.getCode() == 1009));
    }

    @Test
    void malformedAuthenticationMessageClosesUnauthenticatedConnection() throws Exception {
        TenantIssuerAuthenticationManagerResolver managers = mock(TenantIssuerAuthenticationManagerResolver.class);
        LiveSocketHandler handler = new LiveSocketHandler(managers);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("malformed-auth-session");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("{\"type\":\"auth\",\"token\":\"not-a-jwt\"}"));

        verify(session).close(argThat(status -> status.getCode() == 4401));
    }
    @Test
    void malformedJsonClosesUnauthenticatedConnection() throws Exception {
        TenantIssuerAuthenticationManagerResolver managers = mock(TenantIssuerAuthenticationManagerResolver.class);
        LiveSocketHandler handler = new LiveSocketHandler(managers);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("malformed-json-session");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("{not-json"));

        verify(session).close(argThat(status -> status.getCode() == 4401));
    }

    @Test
    void unknownClientMessageIsIgnored() throws Exception {
        TenantIssuerAuthenticationManagerResolver managers = mock(TenantIssuerAuthenticationManagerResolver.class);
        LiveSocketHandler handler = new LiveSocketHandler(managers);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("unknown-message-session");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("{\"type\":\"client_unknown\"}"));

        org.mockito.Mockito.verify(session, org.mockito.Mockito.never()).close(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(session, org.mockito.Mockito.never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authenticatedSocketReceivesOnlyTenantScopedEvents() throws Exception {
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
        when(manager.authenticate(org.mockito.ArgumentMatchers.any(Authentication.class)))
                .thenReturn(authenticated);

        LiveSocketHandler handler = new LiveSocketHandler(managers);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("tenant-routed-session");
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage(
                "{\"type\":\"auth\",\"token\":\"" + token + "\"}"));

        handler.publishToUser(tenantId, userId, "TEST_EVENT", Map.of("value", "visible"));
        handler.publishToUser(UUID.randomUUID(), userId, "TEST_EVENT", Map.of("value", "hidden"));

        verify(session).sendMessage(org.mockito.ArgumentMatchers.argThat(message ->
                message instanceof TextMessage text && text.getPayload().contains("TEST_EVENT")
                        && text.getPayload().contains("visible")));
        org.mockito.Mockito.verify(session, org.mockito.Mockito.times(1))
                .sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
    }

}
