package com.edu360.api.platformops.role;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
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

    public void record(PlatformPrincipal actor, String operation, String actionCode, String entityType, UUID entityId) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        repository.save(PlatformAuditOutboxEvent.builder()
                .auditOutboxId(id)
                .occurredAt(now)
                .actorKind("PLATFORM_USER")
                .actorUserId(actor.platformUserId())
                .operation(operation)
                .actionCode(actionCode)
                .entityType(entityType)
                .entityId(entityId)
                .outcome("SUCCESS")
                .correlationId(id.toString())
                .payloadHash(sha256(id + "|" + actor.platformUserId() + "|" + operation + "|" + actionCode + "|"
                        + entityType + "|" + entityId + "|" + now))
                .createdBy(actor.platformUserId())
                .updatedBy(actor.platformUserId())
                .build());
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
