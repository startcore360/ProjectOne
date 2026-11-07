package com.edu360.api.audit;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.edu360.api.config.CorrelationContext;
import com.edu360.api.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * OD-09 Audit Service:
 * Writes append-only audit records containing event type, actor, active role,
 * institution scope, and correlation ID.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

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
            log.error("AuditLogService: failed to persist audit event {} for '{}' — {}", eventType, email, ex.getMessage(), ex);
        }
    }
}
