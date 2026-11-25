package com.edu360.api.finance;

import com.edu360.api.approval.ApprovalOutcomeHandler;
import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FeeStructureApprovalOutcomeHandler implements ApprovalOutcomeHandler {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogService audit;

    @Override
    public boolean supports(ApprovalRequest request) {
        return "FEE_STRUCTURE".equalsIgnoreCase(request.getProcessCode())
                && "FEE_STRUCTURE".equalsIgnoreCase(request.getSubjectType());
    }

    @Override
    @Transactional
    public void apply(UUID tenantId, ApprovalRequest request, boolean approved, UUID actorUserId) {
        UUID structureId = request.getSubjectId();
        Map<String, Object> structure = jdbc.query("""
            SELECT institution_id, academic_session_id, program_year_id, class_id,
                   version_no, applies_from_date, status
              FROM app.fee_structure
             WHERE tenant_id=:tenantId AND fee_structure_id=:structureId
             FOR UPDATE
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("structureId", structureId), rs -> {
                if (!rs.next()) return null;
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("institutionId", rs.getObject("institution_id", UUID.class));
                row.put("sessionId", rs.getObject("academic_session_id", UUID.class));
                row.put("programYearId", rs.getObject("program_year_id", UUID.class));
                row.put("classId", rs.getObject("class_id", UUID.class));
                row.put("versionNo", rs.getInt("version_no"));
                row.put("appliesFrom", rs.getDate("applies_from_date") == null
                        ? null : rs.getDate("applies_from_date").toLocalDate());
                row.put("status", rs.getString("status"));
                return row;
            });
        if (structure == null || !"PENDING_APPROVAL".equals(structure.get("status"))) {
            throw new BusinessRuleViolationException("FEE_STRUCTURE_NOT_PENDING",
                    "The fee structure is missing or is not pending approval.");
        }

        if (!approved) {
            jdbc.update("""
                UPDATE app.fee_structure SET status='REJECTED', updated_at=now(), updated_by=:actorId
                 WHERE tenant_id=:tenantId AND fee_structure_id=:structureId AND status='PENDING_APPROVAL'
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("structureId", structureId).addValue("actorId", actorUserId));
        } else {
            if ((Integer) structure.get("versionNo") > 1 && structure.get("appliesFrom") == null) {
                throw new BusinessRuleViolationException("FEE_STRUCTURE_EFFECTIVE_DATE_REQUIRED",
                        "A revised fee structure requires an effective-from date before it can go LIVE.");
            }
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("institutionId", structure.get("institutionId"))
                    .addValue("sessionId", structure.get("sessionId"))
                    .addValue("programYearId", structure.get("programYearId"))
                    .addValue("classId", structure.get("classId"))
                    .addValue("actorId", actorUserId);
            jdbc.update("""
                UPDATE app.fee_structure
                   SET status='SUPERSEDED', updated_at=now(), updated_by=:actorId
                 WHERE tenant_id=:tenantId AND institution_id=:institutionId
                   AND academic_session_id=:sessionId
                   AND program_year_id IS NOT DISTINCT FROM :programYearId
                   AND class_id IS NOT DISTINCT FROM :classId
                   AND status='LIVE'
                """, params);
            jdbc.update("""
                UPDATE app.fee_structure
                   SET status='LIVE', updated_at=now(), updated_by=:actorId
                 WHERE tenant_id=:tenantId AND fee_structure_id=:structureId
                   AND status='PENDING_APPROVAL'
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("structureId", structureId).addValue("actorId", actorUserId));
        }
        audit.record(AuditEventType.APPROVAL, null, actorUserId.toString(), tenantId.toString(),
                "Fee structure " + structureId + (approved ? " approved and activated." : " rejected."));
    }
}
