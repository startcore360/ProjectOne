package com.edu360.api.platformops.role;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformAuditOutboxEvent;
import com.edu360.api.platformops.PlatformAuditOutboxEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * Writes to platform.audit_outbox, the table the Platform audit screen reads (Addendum rule 18).
 * Unlike the legacy audit service, a failure here fails the change: a role change that can't be
 * recorded must not happen. Other platform actions still use the legacy table for now.
 */
@Component
@RequiredArgsConstructor
public class PlatformAuditWriter {

    private final PlatformAuditOutboxEventRepository repository;

    private static final Set<String> VALID_OPERATIONS = Set.of(
            "CREATE", "UPDATE", "DELETE", "APPROVE", "REJECT", "LOGIN", "EXPORT", "DOWNLOAD", "DENIED", "VIEW_SENSITIVE", "CONFIG_CHANGE"
    );

    public void record(PlatformPrincipal actor, String operation, String actionCode, String entityType, UUID entityId) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        String validOp = normalizeOperation(operation);
        repository.save(PlatformAuditOutboxEvent.builder()
                .auditOutboxId(id)
                .occurredAt(now)
                .actorKind("PLATFORM_USER")
                .actorUserId(actor.platformUserId())
                .operation(validOp)
                .actionCode(actionCode)
                .entityType(entityType)
                .entityId(entityId)
                .outcome("SUCCESS")
                .correlationId(id.toString())
                .payloadHash(sha256(id + "|" + actor.platformUserId() + "|" + validOp + "|" + actionCode + "|"
                        + entityType + "|" + entityId + "|" + now))
                .createdBy(actor.platformUserId())
                .updatedBy(actor.platformUserId())
                .build());
    }

    private static String normalizeOperation(String op) {
        if (op != null && VALID_OPERATIONS.contains(op)) {
            return op;
        }
        if (op != null) {
            String upper = op.toUpperCase();
            if (upper.contains("CREATE") || upper.contains("REPORT") || upper.contains("ASSUME")) {
                return "CREATE";
            }
            if (upper.contains("DELETE")) {
                return "DELETE";
            }
            if (upper.contains("APPROVE")) {
                return "APPROVE";
            }
            if (upper.contains("REJECT")) {
                return "REJECT";
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
