package com.edu360.api.live;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
public class LiveWebSocketConfig implements WebSocketConfigurer {
    private final LiveSocketHandler handler;
    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;
    @Value("${app.cors.allowed-origin-patterns:}")
    private String allowedOriginPatterns;

    public LiveWebSocketConfig(LiveSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(handler, "/v1/ws", "/api/ws");
        if (allowedOriginPatterns == null || allowedOriginPatterns.isBlank()) {
            registration.setAllowedOrigins(allowedOrigin);
        } else {
            registration.setAllowedOriginPatterns(List.of(allowedOriginPatterns.split(",")));
        }
    }
}
