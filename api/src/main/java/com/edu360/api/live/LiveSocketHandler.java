package com.edu360.api.live;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.config.TenantIssuerAuthenticationManagerResolver;
import com.edu360.api.tenant.TenantContext;

@Component
public class LiveSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(LiveSocketHandler.class);
    private static final CloseStatus UNAUTHENTICATED = new CloseStatus(4401, "Authentication required");
    private static final CloseStatus FORBIDDEN = new CloseStatus(4403, "Tenant user required");
    private static final CloseStatus TOO_MANY = new CloseStatus(4408, "Connection limit exceeded");
    private final TenantIssuerAuthenticationManagerResolver managers;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UserKey, CopyOnWriteArrayList<Connection>> byUser = new ConcurrentHashMap<>();

    public LiveSocketHandler(TenantIssuerAuthenticationManagerResolver managers, ObjectMapper mapper) {
        this.managers = managers;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(raw, 10_000, 65_536);
        connections.put(raw.getId(), new Connection(safe, Instant.now()));
        log.info("Live socket connected session={}", raw.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage message) {
        Connection connection = connections.get(raw.getId());
        if (connection == null) return;
        if (message.getPayloadLength() > 8192) {
            close(connection, new CloseStatus(1009, "Message too large"));
            return;
        }
        try {
            JsonNode node = mapper.readTree(message.getPayload());
            String type = node.path("type").asText("");
            if ("pong".equals(type)) {
                if (connection.userKey != null) connection.lastPong = Instant.now();
                return;
            }
            if ("auth".equals(type)) {
                authenticate(connection, node.path("token").asText(null));
                return;
            }
            // The protocol intentionally ignores all other client message types.
        } catch (Exception ex) {
            log.warn("Live socket message rejected session={} reason={}", raw.getId(), ex.getClass().getSimpleName());
            if (connection.userKey == null) close(connection, UNAUTHENTICATED);
        }
    }

    private void authenticate(Connection connection, String token) {
        if (token == null || token.isBlank()) {
            close(connection, UNAUTHENTICATED);
            return;
        }
        try {
            String issuer = unverifiedIssuer(token);
            AuthenticationManager manager = managers.resolve(issuer);
            Authentication authentication = manager.authenticate(new BearerTokenAuthenticationToken(token));
            if (!(authentication.getPrincipal() instanceof TenantPrincipal principal)
                    || principal.mustChangePassword()) {
                close(connection, FORBIDDEN);
                return;
            }
            Instant expiry = authentication.getCredentials() instanceof Jwt jwt
                    ? jwt.getExpiresAt() : null;
            if (expiry == null || !expiry.isAfter(Instant.now())) {
                close(connection, UNAUTHENTICATED);
                return;
            }
            UserKey key = new UserKey(principal.tenantId(), principal.userAccountId());
            if (connection.userKey != null && !connection.userKey.equals(key)) {
                close(connection, FORBIDDEN);
                return;
            }
            if (connection.userKey == null) {
                connection.userKey = key;
                byUser.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(connection);
                enforceConnectionLimit(key);
            }
            connection.expiresAt = expiry;
            connection.reauthDeadline = null;
            connection.lastPong = Instant.now();
            connection.lastPing = Instant.now();
            log.info("Live socket authenticated session={} tenant={} user={}",
                    connection.session.getId(), key.tenantId(), key.userId());
        } catch (Exception ex) {
            log.warn("Live socket authentication failed session={} reason={}",
                    connection.session.getId(), ex.getClass().getSimpleName());
            close(connection, UNAUTHENTICATED);
        } finally {
            // The REST token converter also sets a thread-local tenant context. A socket worker
            // is reused, so do not let that request-scoped state survive this authentication call.
            TenantContext.clear();
        }
    }

    private String unverifiedIssuer(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("Malformed bearer token");
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = mapper.readTree(new String(payload, StandardCharsets.UTF_8));
            String issuer = claims.path("iss").asText(null);
            if (issuer == null || issuer.isBlank()) throw new IllegalArgumentException("Missing issuer");
            return issuer; // resolver uses this only to select its trusted validator
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed bearer token", ex);
        }
    }

    private void enforceConnectionLimit(UserKey key) {
        CopyOnWriteArrayList<Connection> rows = byUser.get(key);
        while (rows != null && rows.size() > 5) {
            Connection oldest = rows.stream().min((a, b) -> a.connectedAt.compareTo(b.connectedAt)).orElse(null);
            if (oldest == null) return;
            close(oldest, TOO_MANY);
        }
    }

    public void publishToUser(UUID tenantId, UUID userId, String type, Map<String, Object> payload) {
        CopyOnWriteArrayList<Connection> rows = byUser.get(new UserKey(tenantId, userId));
        if (rows == null) return;
        String json = envelope(type, payload);
        for (Connection connection : rows) send(connection, json);
    }

    public void publishToUsers(UUID tenantId, List<UUID> userIds, String type, Map<String, Object> payload) {
        for (UUID userId : userIds) publishToUser(tenantId, userId, type, payload);
    }

    public void publishEntityChanged(UUID tenantId, List<UUID> authorizedUserIds, String entityType,
            UUID entityId, UUID institutionId, String changeType) {
        publishToUsers(tenantId, authorizedUserIds, "ENTITY_CHANGED",
                Map.of("entityType", entityType, "entityId", entityId.toString(),
                        "institutionId", institutionId == null ? "" : institutionId.toString(),
                        "changeType", changeType));
    }

    private String envelope(String type, Map<String, Object> payload) {
        try {
            return mapper.writeValueAsString(Map.of("type", type, "id", UUID.randomUUID().toString(),
                    "at", Instant.now().toString(), "payload", payload));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encode live event", ex);
        }
    }

    private void send(Connection connection, String json) {
        try {
            if (connection.session.isOpen()) connection.session.sendMessage(new TextMessage(json));
        } catch (Exception ex) {
            log.warn("Live socket send failed session={} reason={}",
                    connection.session.getId(), ex.getClass().getSimpleName());
            close(connection, CloseStatus.SERVER_ERROR);
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void maintainConnections() {
        Instant now = Instant.now();
        for (Connection connection : connections.values()) {
            if (connection.userKey == null) {
                if (Duration.between(connection.connectedAt, now).toSeconds() >= 5) close(connection, UNAUTHENTICATED);
                continue;
            }
            if (connection.reauthDeadline != null) {
                if (!now.isBefore(connection.reauthDeadline)) close(connection, UNAUTHENTICATED);
                continue;
            }
            if (connection.expiresAt != null && !now.isBefore(connection.expiresAt)) {
                send(connection, envelope("reauth_required", Map.of()));
                connection.reauthDeadline = now.plusSeconds(30);
                continue;
            }
            if (Duration.between(connection.lastPong, now).toSeconds() >= 60) {
                close(connection, new CloseStatus(4401, "Heartbeat timeout"));
                continue;
            }
            if (Duration.between(connection.lastPing, now).toSeconds() >= 30) {
                send(connection, "{\"type\":\"ping\"}");
                connection.lastPing = now;
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Connection connection = connections.get(session.getId());
        if (connection != null) remove(connection);
        log.info("Live socket closed session={} code={}", session.getId(), status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Connection connection = connections.get(session.getId());
        if (connection != null) close(connection, CloseStatus.SERVER_ERROR);
    }

    private void close(Connection connection, CloseStatus status) {
        remove(connection);
        try {
            if (connection.session.isOpen()) connection.session.close(status);
        } catch (Exception ex) {
            log.debug("Live socket close failed session={}", connection.session.getId());
        }
    }

    private void remove(Connection connection) {
        connections.remove(connection.session.getId(), connection);
        UserKey key = connection.userKey;
        if (key != null) {
            CopyOnWriteArrayList<Connection> rows = byUser.get(key);
            if (rows != null) {
                rows.remove(connection);
                if (rows.isEmpty()) byUser.remove(key, rows);
            }
        }
    }

    private static final class Connection {
        final WebSocketSession session;
        final Instant connectedAt;
        volatile UserKey userKey;
        volatile Instant expiresAt;
        volatile Instant lastPong;
        volatile Instant lastPing;
        volatile Instant reauthDeadline;
        Connection(WebSocketSession session, Instant connectedAt) {
            this.session = session;
            this.connectedAt = connectedAt;
            this.lastPong = connectedAt;
            this.lastPing = connectedAt;
        }
    }

    private record UserKey(UUID tenantId, UUID userId) {}
}
