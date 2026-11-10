package com.edu360.api.platformops.role;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.edu360.api.config.CorrelationContext;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformAuditOutboxEvent;
import com.edu360.api.platformops.PlatformAuditOutboxEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * Writes to platform.audit_outbox, the table the Platform audit screen reads (Addendum rule 18, L-07).
 *
 * Audit is REQUIRED: this writer is never optional and never swallows a failure. A change that cannot be
 * recorded must not happen, so an error here propagates, the request fails with a 500 and its correlation
 * id, and the surrounding transaction rolls the business change back. Every row carries the actor, the
 * D2 action, the entity, the named event type (column event_type, sql/22), the outcome and the correlation id.
 */
@Component
@RequiredArgsConstructor
public class PlatformAuditWriter {

    public static final String SUCCESS = "SUCCESS";
    public static final String DENIED = "DENIED";
    public static final String FAILED = "FAILED";

    private final PlatformAuditOutboxEventRepository repository;

    private static final Set<String> VALID_OPERATIONS = Set.of(
            "CREATE", "UPDATE", "DELETE", "APPROVE", "REJECT", "LOGIN", "EXPORT", "DOWNLOAD", "DENIED", "VIEW_SENSITIVE", "CONFIG_CHANGE"
    );

    /** Short form: {@code operationOrEvent} is either a D4 operation or a named event (its operation is derived). */
    public void record(PlatformPrincipal actor, String operationOrEvent, String actionCode, String entityType, UUID entityId) {
        record(actor, null, operationOrEvent, null, actionCode, entityType, entityId, SUCCESS);
    }

    /** Short form for an event about one tenant. */
    public void record(PlatformPrincipal actor, UUID tenantId, String operationOrEvent, String actionCode,
                       String entityType, UUID entityId) {
        record(actor, tenantId, operationOrEvent, null, actionCode, entityType, entityId, SUCCESS);
    }

    /**
     * Full form. {@code eventType} may be null when {@code operationOrEvent} already is a plain operation.
     * When {@code operation} is null it is derived from the event name.
     */
    public void record(PlatformPrincipal actor, UUID tenantId, String eventType, String operation, String actionCode,
                       String entityType, UUID entityId, String outcome) {
        if (actor == null || actor.platformUserId() == null) {
            throw new IllegalStateException("A platform audit event needs an actor.");
        }
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        String op = operation != null ? normalizeOperation(operation) : normalizeOperation(eventType);
        String event = eventType == null ? null : (eventType.length() > 60 ? eventType.substring(0, 60) : eventType);
        String corr = CorrelationContext.get();
        if (corr == null || corr.isBlank()) {
            corr = id.toString();
        }
        if (corr.length() > 100) {
            corr = corr.substring(0, 100);
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
                .correlationId(corr)
                .payloadHash(sha256(id + "|" + actor.platformUserId() + "|" + op + "|" + event + "|" + actionCode + "|"
                        + entityType + "|" + entityId + "|" + outcome + "|" + now))
                .createdBy(actor.platformUserId())
                .updatedBy(actor.platformUserId())
                .build());
    }

    /** A refused attempt (L-07): written before the refusal is raised. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordDenied(PlatformPrincipal actor, String actionCode) {
        record(actor, null, "ACTION_DENIED", "DENIED", actionCode, "PLATFORM_ACTION", actor.platformUserId(), DENIED);
    }

    private static String normalizeOperation(String op) {
        if (op != null && VALID_OPERATIONS.contains(op)) {
            return op;
        }
        if (op != null) {
            String upper = op.toUpperCase();
            if (upper.contains("REJECT")) {
                return "REJECT";
            }
            if (upper.contains("APPROVE") || upper.endsWith("_APPROVED") || upper.endsWith("_CONFIRMED")) {
                return "APPROVE";
            }
            if (upper.contains("CREATE") || upper.contains("REPORT") || upper.contains("ASSUME") || upper.endsWith("_REQUESTED")) {
                return "CREATE";
            }
            if (upper.contains("DELETE")) {
                return "DELETE";
            }
            if (upper.contains("CONFIG")) {
                return "CONFIG_CHANGE";
            }
            if (upper.contains("EXPORT")) {
                return "EXPORT";
            }
            if (upper.contains("DOWNLOAD")) {
                return "DOWNLOAD";
            }
            if (upper.contains("VIEW")) {
                return "VIEW_SENSITIVE";
            }
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
