package com.edu360.api.platformops.role;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.edu360.api.config.CorrelationContext;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformAuditOutboxEvent;
import com.edu360.api.platformops.PlatformAuditOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import lombok.RequiredArgsConstructor;

/** Writes platform audit events, including masked old/new field values, to platform.audit_outbox. */
@Component
@RequiredArgsConstructor
public class PlatformAuditWriter {
    public static final String SUCCESS = "SUCCESS";
    public static final String DENIED = "DENIED";
    public static final String FAILED = "FAILED";

    private static final Set<String> VALID_OPERATIONS = Set.of(
            "CREATE", "UPDATE", "DELETE", "APPROVE", "REJECT", "LOGIN", "EXPORT", "DOWNLOAD", "DENIED",
            "VIEW_SENSITIVE", "CONFIG_CHANGE");
    private static final Set<String> SENSITIVE_FIELDS = Set.of("password", "secret", "token", "nationalid", "salary");
    private static final ObjectMapper PAYLOAD_MAPPER = new ObjectMapper();

    private final PlatformAuditOutboxEventRepository repository;

    public void record(PlatformPrincipal actor, String operationOrEvent, String actionCode, String entityType, UUID entityId) {
        record(actor, null, operationOrEvent, null, actionCode, entityType, entityId, SUCCESS, Map.of());
    }

    public void record(PlatformPrincipal actor, UUID tenantId, String operationOrEvent, String actionCode,
                       String entityType, UUID entityId) {
        record(actor, tenantId, operationOrEvent, null, actionCode, entityType, entityId, SUCCESS, Map.of());
    }

    public void record(PlatformPrincipal actor, UUID tenantId, String eventType, String operation, String actionCode,
                       String entityType, UUID entityId, String outcome) {
        record(actor, tenantId, eventType, operation, actionCode, entityType, entityId, outcome, Map.of());
    }

    /**
     * Writes a payload with one old/new pair per changed field. Sensitive keys are masked recursively,
     * including when they appear inside nested objects or arrays.
     */
    public void record(PlatformPrincipal actor, UUID tenantId, String eventType, String operation, String actionCode,
                       String entityType, UUID entityId, String outcome, Map<String, Map<String, Object>> changes) {
        if (actor == null || actor.platformUserId() == null) {
            throw new IllegalStateException("A platform audit event needs an actor.");
        }
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        String op = operation != null ? normalizeOperation(operation) : normalizeOperation(eventType);
        String event = eventType == null ? null : (eventType.length() > 60 ? eventType.substring(0, 60) : eventType);
        String correlationId = CorrelationContext.get();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = id.toString();
        }
        if (correlationId.length() > 100) {
            correlationId = correlationId.substring(0, 100);
        }

        JsonNode payload = maskedPayload(changes);
        String payloadJson;
        try {
            payloadJson = PAYLOAD_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Platform audit payload could not be serialized.", ex);
        }

        repository.save(PlatformAuditOutboxEvent.builder()
                .auditOutboxId(id)
                .tenantId(tenantId)
                .occurredAt(now)
                .actorKind("PLATFORM_USER")
                .actorUserId(actor.platformUserId())
                .operation(op)
                .eventType(event)
                .actionCode(actionCode)
                .entityType(entityType)
                .entityId(entityId)
                .outcome(outcome)
                .payload(payload)
                .correlationId(correlationId)
                .payloadHash(sha256(id + "|" + actor.platformUserId() + "|" + op + "|" + event + "|" + actionCode
                        + "|" + entityType + "|" + entityId + "|" + outcome + "|" + payloadJson + "|" + now))
                .createdBy(actor.platformUserId())
                .updatedBy(actor.platformUserId())
                .build());
    }

    /** A refused attempt (L-07): written before the refusal is raised. */
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordDenied(PlatformPrincipal actor, String actionCode) {
        record(actor, null, "ACTION_DENIED", "DENIED", actionCode, "PLATFORM_ACTION", actor.platformUserId(), DENIED);
    }

    /** A denied attempt against a specific platform resource. */
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordDenied(PlatformPrincipal actor, String actionCode, String entityType, UUID entityId) {
        record(actor, null, "ACTION_DENIED", "DENIED", actionCode, entityType, entityId, DENIED);
    }

    private static JsonNode maskedPayload(Map<String, Map<String, Object>> changes) {
        ObjectNode oldValues = PAYLOAD_MAPPER.createObjectNode();
        ObjectNode newValues = PAYLOAD_MAPPER.createObjectNode();
        if (changes != null) {
            changes.forEach((field, pair) -> {
                Object oldValue = pair == null ? null : pair.get("old");
                Object newValue = pair == null ? null : pair.get("new");
                oldValues.set(field, maskedValue(oldValue, isSensitive(field)));
                newValues.set(field, maskedValue(newValue, isSensitive(field)));
            });
        }
        ObjectNode payload = PAYLOAD_MAPPER.createObjectNode();
        payload.set("old_values", oldValues);
        payload.set("new_values", newValues);
        return payload;
    }

    private static JsonNode maskedValue(Object value, boolean forceMask) {
        return forceMask ? TextNode.valueOf("***") : maskNested(PAYLOAD_MAPPER.valueToTree(value));
    }

    private static JsonNode maskNested(JsonNode value) {
        if (value == null || value.isNull() || value.isValueNode()) {
            return value == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : value.deepCopy();
        }
        if (value.isObject()) {
            ObjectNode masked = PAYLOAD_MAPPER.createObjectNode();
            value.fields().forEachRemaining(entry -> masked.set(entry.getKey(),
                    isSensitive(entry.getKey()) ? TextNode.valueOf("***") : maskNested(entry.getValue())));
            return masked;
        }
        if (value.isArray()) {
            ArrayNode masked = PAYLOAD_MAPPER.createArrayNode();
            value.forEach(item -> masked.add(maskNested(item)));
            return masked;
        }
        return value.deepCopy();
    }

    private static boolean isSensitive(String field) {
        return field != null && SENSITIVE_FIELDS.contains(field.replace("_", "").toLowerCase(java.util.Locale.ROOT));
    }

    private static String normalizeOperation(String operation) {
        if (operation != null && VALID_OPERATIONS.contains(operation)) {
            return operation;
        }
        if (operation != null) {
            String upper = operation.toUpperCase(java.util.Locale.ROOT);
            if (upper.contains("REJECT")) return "REJECT";
            if (upper.contains("APPROVE") || upper.endsWith("_APPROVED") || upper.endsWith("_CONFIRMED")) return "APPROVE";
            if (upper.contains("CREATE") || upper.contains("REPORT") || upper.contains("ASSUME") || upper.endsWith("_REQUESTED")) return "CREATE";
            if (upper.contains("DELETE")) return "DELETE";
            if (upper.contains("CONFIG")) return "CONFIG_CHANGE";
            if (upper.contains("EXPORT")) return "EXPORT";
            if (upper.contains("DOWNLOAD")) return "DOWNLOAD";
            if (upper.contains("VIEW")) return "VIEW_SENSITIVE";
        }
        return "UPDATE";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
