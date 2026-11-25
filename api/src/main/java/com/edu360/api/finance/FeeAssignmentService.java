package com.edu360.api.finance;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeeAssignmentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final AuditLogService audit;

    @Transactional
    public Map<String, Object> assign(UUID tenantId, TenantPrincipal actor, UUID feeStructureId,
            FeeAssignmentRequest request) {
        if (request.enrollmentIds() == null || request.enrollmentIds().isEmpty()
                || request.enrollmentIds().size() > 500
                || new HashSet<>(request.enrollmentIds()).size() != request.enrollmentIds().size()) {
            throw new BusinessRuleViolationException("FEE_ASSIGNMENT_ENROLLMENTS_INVALID",
                    "Provide 1–500 distinct enrollment IDs.");
        }
        Map<String, Object> structure = jdbc.query("""
            SELECT institution_id, academic_session_id, program_year_id, class_id,
                   applies_from_date, status
              FROM app.fee_structure
             WHERE tenant_id=:tenantId AND fee_structure_id=:structureId
             FOR UPDATE
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("structureId", feeStructureId), rs -> {
                if (!rs.next()) return null;
                Map<String, Object> row = new java.util.HashMap<>();
                row.put("institutionId", rs.getObject("institution_id", UUID.class));
                row.put("sessionId", rs.getObject("academic_session_id", UUID.class));
                row.put("programYearId", rs.getObject("program_year_id", UUID.class));
                row.put("classId", rs.getObject("class_id", UUID.class));
                row.put("appliesFrom", rs.getDate("applies_from_date") == null
                        ? LocalDate.now() : rs.getDate("applies_from_date").toLocalDate());
                row.put("status", rs.getString("status"));
                return row;
            });
        if (structure == null) throw new ResourceNotFoundException("Fee structure not found.");
        if (!"LIVE".equals(structure.get("status"))) {
            throw new BusinessRuleViolationException("FEE_STRUCTURE_NOT_LIVE",
                    "Only an approved LIVE fee structure can be assigned to students.");
        }
        UUID institutionId = (UUID) structure.get("institutionId");
        permissions.authorize(new RbacPrincipal(tenantId, actor.userAccountId()),
                "FEE_STRUCTURE_CREATE", RbacScope.institution(institutionId), RbacTarget.NONE);

        int assigned = 0;
        int invoices = 0;
        for (UUID enrollmentId : request.enrollmentIds()) {
            UUID studentProfileId = jdbc.query("""
                SELECT e.student_profile_id
                  FROM app.student_enrollment e
                  JOIN app.section s
                    ON s.tenant_id=e.tenant_id AND s.section_id=e.section_id
                  LEFT JOIN app.semester sem
                    ON sem.tenant_id=s.tenant_id AND sem.semester_id=s.semester_id
                 WHERE e.tenant_id=:tenantId
                   AND e.enrollment_id=:enrollmentId
                   AND e.institution_id=:institutionId
                   AND e.academic_session_id=:sessionId
                   AND e.outcome='IN_PROGRESS'
                   AND (e.ended_on IS NULL OR e.ended_on >= :effectiveFrom)
                   AND s.status='ACTIVE'
                   AND (
                       (:programYearId IS NOT NULL AND sem.program_year_id=:programYearId)
                       OR (:classId IS NOT NULL AND s.class_id=:classId)
                   )
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("enrollmentId", enrollmentId).addValue("institutionId", institutionId)
                        .addValue("sessionId", structure.get("sessionId"))
                        .addValue("effectiveFrom", structure.get("appliesFrom"))
                        .addValue("programYearId", structure.get("programYearId"))
                        .addValue("classId", structure.get("classId")),
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
            if (studentProfileId == null) {
                throw new BusinessRuleViolationException("FEE_ASSIGNMENT_ENROLLMENT_MISMATCH",
                        "Enrollment " + enrollmentId + " is not active in the fee structure's institution, session and class/program year.");
            }

            jdbc.update("""
                INSERT INTO app.student_fee_account
                    (tenant_id, student_fee_account_id, student_profile_id, status, currency_code, created_by, updated_by)
                VALUES (:tenantId, :accountId, :studentProfileId, 'ACTIVE', 'INR', :actorId, :actorId)
                ON CONFLICT (tenant_id, student_profile_id) DO NOTHING
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("accountId", UUID.randomUUID()).addValue("studentProfileId", studentProfileId)
                        .addValue("actorId", actor.userAccountId()));

            UUID accountId = jdbc.query("""
                SELECT student_fee_account_id
                  FROM app.student_fee_account
                 WHERE tenant_id=:tenantId AND student_profile_id=:studentProfileId
                 FOR UPDATE
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("studentProfileId", studentProfileId),
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
            if (accountId == null) throw new IllegalStateException("Student fee account could not be created.");

            int assignmentInsert = jdbc.update("""
                INSERT INTO app.fee_assignment
                    (tenant_id, fee_assignment_id, institution_id, student_fee_account_id,
                     enrollment_id, fee_structure_id, proration_factor, effective_from, status,
                     created_by, updated_by)
                VALUES
                    (:tenantId, :assignmentId, :institutionId, :accountId,
                     :enrollmentId, :structureId, 1.0000, :effectiveFrom, 'ACTIVE',
                     :actorId, :actorId)
                ON CONFLICT (tenant_id, enrollment_id, fee_structure_id) WHERE status='ACTIVE' DO NOTHING
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("assignmentId", UUID.randomUUID()).addValue("institutionId", institutionId)
                        .addValue("accountId", accountId).addValue("enrollmentId", enrollmentId)
                        .addValue("structureId", feeStructureId).addValue("effectiveFrom", structure.get("appliesFrom"))
                        .addValue("actorId", actor.userAccountId()));
            if (assignmentInsert == 1) assigned++;

            UUID assignmentId = jdbc.query("""
                SELECT fee_assignment_id
                  FROM app.fee_assignment
                 WHERE tenant_id=:tenantId AND enrollment_id=:enrollmentId
                   AND fee_structure_id=:structureId AND status='ACTIVE'
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("enrollmentId", enrollmentId).addValue("structureId", feeStructureId),
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
            if (assignmentId == null) throw new IllegalStateException("Fee assignment could not be resolved.");

            invoices += jdbc.update("""
                INSERT INTO app.student_installment
                    (tenant_id, student_installment_id, institution_id, student_fee_account_id,
                     fee_assignment_id, installment_no, due_date, billed_amount, status, created_by, updated_by)
                SELECT :tenantId, gen_random_uuid(), :institutionId, :accountId,
                       :assignmentId, t.installment_no, t.due_date, t.amount, 'UNPAID', :actorId, :actorId
                  FROM app.fee_structure_installment t
                 WHERE t.tenant_id=:tenantId AND t.fee_structure_id=:structureId
                ON CONFLICT (tenant_id, fee_assignment_id, installment_no) DO NOTHING
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                        .addValue("institutionId", institutionId).addValue("accountId", accountId)
                        .addValue("assignmentId", assignmentId).addValue("actorId", actor.userAccountId())
                        .addValue("structureId", feeStructureId));
        }

        audit.record(AuditEventType.CREATE, null, actor.userAccountId().toString(), tenantId.toString(),
                "Fee structure " + feeStructureId + " assigned to " + assigned
                        + " enrollments; " + invoices + " installment invoices created.");
        return Map.of("feeStructureId", feeStructureId, "enrollmentsAssigned", assigned,
                "invoicesCreated", invoices, "status", "ASSIGNED");
    }

    public record FeeAssignmentRequest(List<UUID> enrollmentIds) {}
}
