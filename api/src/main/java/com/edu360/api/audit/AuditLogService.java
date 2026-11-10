package com.edu360.api.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.edu360.api.config.CorrelationContext;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.tenant.TenantContext;

/**
 * OD-09 Audit Service:
 * Writes append-only audit records containing event type, actor, active role,
 * institution scope, and correlation ID.
 *
 * WHERE each event goes (Q-01, following D4 §5.23):
 * - A request with a tenant (TenantContext) AND a signed-in user of that same tenant
 *   (TenantPrincipal) AND an active role -> app.audit_outbox, the D4 tenant audit table:
 *   RLS by tenant and institution, append-only by trigger. A tenant can only ever read its own.
 * - Anything else (platform requests that still call this service, sign-in attempts before the
 *   user is known, a tenant user with no active role chosen) -> the legacy public.audit_logs sink,
 *   because app.audit_outbox requires an actor id and an active role for USER rows (D4 r1, r2) and
 *   this service never invents either. Platform events belong in platform.audit_outbox (B4).
 *
 * Audit is REQUIRED (L-07, OD-09): there is no optional writer and no swallowed failure. If the event cannot be
 * stored, an AuditFailureException is thrown, the request fails with a 500 and a correlation id, and the
 * surrounding transaction rolls the business change back. A change that cannot be audited does not happen.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final TenantAuditStore tenantAuditStore;

    @Autowired
    public AuditLogService(AuditLogRepository auditLogRepository, TenantAuditStore tenantAuditStore) {
        this.auditLogRepository = auditLogRepository;
        this.tenantAuditStore = tenantAuditStore;
    }

    /** Legacy sink only (no tenant store): every event is still written, to public.audit_logs. Used by unit tests. */
    public AuditLogService(AuditLogRepository auditLogRepository) {
        this(auditLogRepository, null);
    }

    public void record(AuditEventType eventType, Long userId, String email, String ipAddress, String details) {
        String roleCode = TenantContext.currentActiveRoleCode();
        String scopeType = TenantContext.currentActiveScopeType() != null ? TenantContext.currentActiveScopeType().name() : null;
        Set<UUID> instIds = TenantContext.currentInstitutionIds();
        UUID instId = instIds.isEmpty() ? null : instIds.iterator().next();

        record(eventType, userId, email, ipAddress, details, roleCode, scopeType, instId);
    }

    public void record(AuditEventType eventType, Long userId, String email, String ipAddress, String details,
                       String actorRoleCode, String scopeType, UUID institutionId) {
        try {
            String correlationId = CorrelationContext.get();
            TenantPrincipal tenantActor = tenantActor(actorRoleCode);
            if (tenantActor != null) {
                tenantAuditStore.append(toTenantEvent(tenantActor, eventType, email, ipAddress, details,
                        actorRoleCode, scopeType, institutionId, correlationId));
                return;
            }
            auditLogRepository.save(AuditLog.builder()
                    .eventType(eventType)
                    .userId(userId)
                    .email(email)
                    .ipAddress(ipAddress)
                    .details(details)
                    .actorRoleCode(actorRoleCode)
                    .scopeType(scopeType)
                    .institutionId(institutionId)
                    .correlationId(correlationId)
                    .build());
        } catch (Exception ex) {
            log.error("AuditLogService: failed to persist audit event {} for '{}' - {}", eventType, email, ex.getMessage(), ex);
            throw new AuditFailureException("Audit event " + eventType + " could not be recorded; the request was not completed.", ex);
        }
    }

    /** The signed-in tenant user, only when the row can satisfy app.audit_outbox's rules. */
    private TenantPrincipal tenantActor(String actorRoleCode) {
        UUID tenantId = TenantContext.currentTenantId();
        if (tenantAuditStore == null || tenantId == null || actorRoleCode == null || actorRoleCode.isBlank()) {
            return null;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TenantPrincipal p
                && tenantId.equals(p.tenantId()) && p.userAccountId() != null) {
            return p;
        }
        return null;
    }

    private static TenantAuditEvent toTenantEvent(TenantPrincipal actor, AuditEventType eventType, String email,
                                                  String ipAddress, String details, String actorRoleCode,
                                                  String scopeType, UUID institutionId, String correlationId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String operation = operationOf(eventType);
        String outcome = outcomeOf(eventType);
        String corr = correlationId.length() > 100 ? correlationId.substring(0, 100) : correlationId;
        String newValues = "{"
                + json("event_type", eventType.name()) + ","
                + json("actor", email) + ","
                + json("ip_address", ipAddress) + ","
                + json("scope_type", scopeType) + ","
                + json("details", details)
                + "}";
        String hash = sha256(String.join("|", actor.tenantId().toString(), id.toString(), now.toString(),
                actor.userAccountId().toString(), actorRoleCode, operation, eventType.name(), outcome, corr, newValues));
        return new TenantAuditEvent(actor.tenantId(), id, institutionId, now, actor.userAccountId(), actorRoleCode,
                operation, eventType.name(), outcome, newValues, corr, hash);
    }

    /** Maps the event type onto D4's fixed operation list (ck_audit_outbox_operation). */
    static String operationOf(AuditEventType type) {
        String n = type.name();
        return switch (type) {
            case LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT -> "LOGIN";
            case DENIED_ATTEMPT -> "DENIED";
            case EXPORT -> "EXPORT";
            case SENSITIVE_VIEW -> "VIEW_SENSITIVE";
            case DELETE -> "DELETE";
            case APPROVAL -> "APPROVE";
            case TERM_LOCKED, TERM_UNLOCKED, TENANT_SETTINGS_UPDATED, PRINCIPAL_LEAVE_APPROVER_SET,
                 VC_LEAVE_APPROVER_SET -> "CONFIG_CHANGE";
            default -> n.endsWith("_APPROVED") ? "APPROVE"
                    : n.endsWith("_REJECTED") ? "REJECT"
                    : (n.endsWith("_CREATED") || n.endsWith("_CREATED_DRAFT") || n.endsWith("_LINKED")
                            || n.endsWith("_ASSIGNED") || n.endsWith("_UPLOADED") || n.endsWith("_REQUESTED")
                            || n.equals("CREATE")) ? "CREATE"
                    : "UPDATE";
        };
    }

    /** D4 ck_audit_outbox_outcome: SUCCESS, DENIED or FAILED. */
    static String outcomeOf(AuditEventType type) {
        return switch (type) {
            case LOGIN_FAILURE -> "FAILED";
            case DENIED_ATTEMPT -> "DENIED";
            default -> "SUCCESS";
        };
    }

    private static String json(String key, String value) {
        return "\"" + key + "\":" + (value == null ? "null" : "\"" + escape(value) + "\"");
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.toString();
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
