package com.edu360.api.approval;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.edu360.api.live.LiveEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Publishes the live pending-approval badge count to affected approvers. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalCountPublisher {
    private static final String RECIPIENTS = """
            SELECT DISTINCT ura.user_account_id
              FROM app.user_role_assignment ura
              JOIN app.role_definition rd
                ON rd.tenant_id = ura.tenant_id
               AND rd.role_definition_id = ura.role_definition_id
             WHERE ura.tenant_id = ?
               AND rd.role_code = ?
               AND ura.status = 'ACTIVE'
               AND ura.effective_from <= now()
               AND (ura.effective_to IS NULL OR ura.effective_to > now())
               AND (ura.scope_type = 'TENANT' OR ura.institution_id IS NOT DISTINCT FROM ?)
            UNION
            SELECT DISTINCT s.approver_user_id
              FROM app.approval_request r
              JOIN app.approval_step s
                ON s.tenant_id = r.tenant_id
               AND s.approval_request_id = r.approval_request_id
               AND s.step_no = r.current_step_no
             WHERE r.tenant_id = ?
               AND r.institution_id IS NOT DISTINCT FROM ?
               AND r.status = 'PENDING'
               AND s.decision = 'PENDING'
               AND s.approver_role_code = ?
               AND s.approver_user_id IS NOT NULL
            """;

    private static final String PENDING_COUNT = """
            SELECT COUNT(DISTINCT r.approval_request_id)
              FROM app.approval_request r
              JOIN app.approval_step s
                ON s.tenant_id = r.tenant_id
               AND s.approval_request_id = r.approval_request_id
               AND s.step_no = r.current_step_no
             WHERE r.tenant_id = ?
               AND r.status = 'PENDING'
               AND s.decision = 'PENDING'
               AND (
                    s.approver_user_id = ?
                    OR EXISTS (
                        SELECT 1
                          FROM app.user_role_assignment ura
                          JOIN app.role_definition rd
                            ON rd.tenant_id = ura.tenant_id
                           AND rd.role_definition_id = ura.role_definition_id
                         WHERE ura.tenant_id = r.tenant_id
                           AND ura.user_account_id = ?
                           AND ura.status = 'ACTIVE'
                           AND ura.effective_from <= now()
                           AND (ura.effective_to IS NULL OR ura.effective_to > now())
                           AND rd.role_code = s.approver_role_code
                           AND (ura.scope_type = 'TENANT' OR ura.institution_id IS NOT DISTINCT FROM r.institution_id)
                    )
               )
            """;

    private final JdbcTemplate jdbc;
    private final LiveEventPublisher liveEvents;

    public void publishForRole(UUID tenantId, UUID institutionId, String roleCode) {
        if (tenantId == null || roleCode == null || roleCode.isBlank()) return;
        try {
            List<UUID> recipients = jdbc.queryForList(RECIPIENTS, UUID.class,
                    tenantId, roleCode.trim().toUpperCase(java.util.Locale.ROOT), institutionId,
                    tenantId, institutionId, roleCode.trim().toUpperCase(java.util.Locale.ROOT));
            recipients.forEach(userId -> publishForUser(tenantId, userId));
        } catch (RuntimeException ex) {
            // Live badge delivery is best-effort and must never roll back an approval decision.
            log.warn("Could not resolve approval-count recipients for tenant {} role {}: {}",
                    tenantId, roleCode, ex.getMessage());
        }
    }

    public void publishForUser(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) return;
        try {
            Long count = jdbc.queryForObject(PENDING_COUNT, Long.class, tenantId, userId, userId);
            liveEvents.publishToUser(tenantId, userId, "APPROVAL_COUNT",
                    Map.of("pendingCount", count == null ? 0L : count));
        } catch (RuntimeException ex) {
            log.warn("Could not publish pending approval count for user {} in tenant {}: {}",
                    userId, tenantId, ex.getMessage());
        }
    }
}
