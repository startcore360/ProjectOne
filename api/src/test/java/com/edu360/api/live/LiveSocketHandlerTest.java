package com.edu360.api.live;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
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
}
