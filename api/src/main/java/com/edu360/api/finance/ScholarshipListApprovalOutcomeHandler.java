package com.edu360.api.finance;

import com.edu360.api.approval.ApprovalOutcomeHandler;
import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the approval state transition for a scholarship award list.
 * Positive award application intentionally fails closed until OD-30 defines the fee-head /
 * installment allocation rule; approval must never silently produce a list with no ledger credit.
 */
@Component
@RequiredArgsConstructor
public class ScholarshipListApprovalOutcomeHandler implements ApprovalOutcomeHandler {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogService audit;

    @Override
    public boolean supports(ApprovalRequest request) {
        return "SCHOLARSHIP_LIST".equalsIgnoreCase(request.getProcessCode())
                && "SCHOLARSHIP_AWARD_LIST".equalsIgnoreCase(request.getSubjectType());
    }

    @Override
    @Transactional
    public void apply(UUID tenantId, ApprovalRequest request, boolean approved, UUID actorUserId) {
        UUID listId = request.getSubjectId();
        Map<String, Object> list = jdbc.query("""
            SELECT list_id, institution_id, status
              FROM app.scholarship_award_list
             WHERE tenant_id=:tenantId AND list_id=:listId
             FOR UPDATE
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("listId", listId), rs -> {
                if (!rs.next()) return null;
                return Map.of("institutionId", rs.getObject("institution_id", UUID.class),
                        "status", rs.getString("status"));
            });
        if (list == null || !"PENDING_APPROVAL".equals(list.get("status"))) {
            throw new BusinessRuleViolationException("SCHOLARSHIP_LIST_NOT_PENDING",
                    "The scholarship award list is missing or is not pending approval.");
        }

        if (approved) {
            Integer positiveAwards = jdbc.queryForObject("""
                SELECT COUNT(*) FROM app.scholarship_award
                 WHERE tenant_id=:tenantId AND list_id=:listId AND computed_amount > 0
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("listId", listId),
                    Integer.class);
            if (positiveAwards != null && positiveAwards > 0) {
                throw new BusinessRuleViolationException("SCHOLARSHIP_ALLOCATION_POLICY_UNRESOLVED",
                        "PO-T04 approval authority is enforced, but positive scholarship awards cannot be posted until OD-30 defines fee-head and installment allocation.");
            }
        }

        jdbc.update("""
            UPDATE app.scholarship_award_list
               SET status=:status, updated_at=now(), updated_by=:actorId
             WHERE tenant_id=:tenantId AND list_id=:listId AND status='PENDING_APPROVAL'
            """, new MapSqlParameterSource()
                .addValue("status", approved ? "APPROVED" : "REJECTED")
                .addValue("actorId", actorUserId).addValue("tenantId", tenantId).addValue("listId", listId));

        audit.record(AuditEventType.APPROVAL, null, actorUserId.toString(), tenantId.toString(),
                "Scholarship award list " + listId + (approved ? " approved." : " rejected."));
    }
}
