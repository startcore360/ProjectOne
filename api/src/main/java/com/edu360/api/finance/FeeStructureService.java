package com.edu360.api.finance;

import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.approval.ApprovalRequestId;
import com.edu360.api.approval.ApprovalRequestRepository;
import com.edu360.api.approval.ApprovalStep;
import com.edu360.api.approval.ApprovalStepId;
import com.edu360.api.approval.ApprovalStepRepository;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
public class FeeStructureService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final ApprovalRequestRepository approvalRequests;
    private final ApprovalStepRepository approvalSteps;
    private final AuditLogService audit;

    @Transactional
    public Map<String, Object> create(UUID tenantId, TenantPrincipal actor, FeeStructureRequest request) {
        permissions.authorize(new RbacPrincipal(tenantId, actor.userAccountId()),
                "FEE_STRUCTURE_CREATE", RbacScope.institution(request.institutionId()), RbacTarget.NONE);
        validateSchedule(request);

        String institutionType = jdbc.query("""
            SELECT institution_type FROM app.institution
             WHERE tenant_id=:tenantId AND institution_id=:institutionId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("institutionId", request.institutionId()),
                rs -> rs.next() ? rs.getString(1) : null);
        if (institutionType == null) throw new ResourceNotFoundException("Institution not found.");

        int versionNo = jdbc.queryForObject("""
            SELECT COALESCE(MAX(version_no),0)+1
              FROM app.fee_structure
             WHERE tenant_id=:tenantId AND institution_id=:institutionId
               AND academic_session_id=:sessionId
               AND program_year_id IS NOT DISTINCT FROM :programYearId
               AND class_id IS NOT DISTINCT FROM :classId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("institutionId", request.institutionId())
                    .addValue("sessionId", request.academicSessionId())
                    .addValue("programYearId", request.programYearId())
                    .addValue("classId", request.classId()), Integer.class);

        UUID structureId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app.fee_structure
              (tenant_id, fee_structure_id, institution_id, academic_session_id,
               program_year_id, class_id, fee_policy_id, version_no, status,
               applies_from_date, created_by, updated_by)
            VALUES
              (:tenantId, :structureId, :institutionId, :sessionId,
               :programYearId, :classId, :feePolicyId, :versionNo, 'DRAFT',
               :appliesFrom, :actorId, :actorId)
            """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("structureId", structureId)
                .addValue("institutionId", request.institutionId()).addValue("sessionId", request.academicSessionId())
                .addValue("programYearId", request.programYearId()).addValue("classId", request.classId())
                .addValue("feePolicyId", request.feePolicyId()).addValue("versionNo", versionNo)
                .addValue("appliesFrom", request.appliesFromDate()).addValue("actorId", actor.userAccountId()));

        for (FeeStructureLine line : request.lines()) {
            jdbc.update("""
                INSERT INTO app.fee_structure_line
                  (tenant_id, fee_structure_line_id, institution_id, fee_structure_id,
                   fee_head_id, amount, created_by, updated_by)
                VALUES
                  (:tenantId, :lineId, :institutionId, :structureId,
                   :feeHeadId, :amount, :actorId, :actorId)
                """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId).addValue("lineId", UUID.randomUUID())
                    .addValue("institutionId", request.institutionId()).addValue("structureId", structureId)
                    .addValue("feeHeadId", line.feeHeadId()).addValue("amount", line.amount())
                    .addValue("actorId", actor.userAccountId()));
        }
        for (FeeStructureInstallment installment : request.installments()) {
            jdbc.update("""
                INSERT INTO app.fee_structure_installment
                  (tenant_id, installment_template_id, institution_id, fee_structure_id,
                   installment_no, due_date, amount, created_by, updated_by)
                VALUES
                  (:tenantId, :installmentId, :institutionId, :structureId,
                   :installmentNo, :dueDate, :amount, :actorId, :actorId)
                """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId).addValue("installmentId", UUID.randomUUID())
                    .addValue("institutionId", request.institutionId()).addValue("structureId", structureId)
                    .addValue("installmentNo", installment.installmentNo()).addValue("dueDate", installment.dueDate())
                    .addValue("amount", installment.amount()).addValue("actorId", actor.userAccountId()));
        }

        audit.record(AuditEventType.CREATE, null, actor.userAccountId().toString(), tenantId.toString(),
                "Fee structure " + structureId + " created in DRAFT version " + versionNo + ".");
        return Map.of("feeStructureId", structureId, "versionNo", versionNo, "status", "DRAFT");
    }

    @Transactional
    public Map<String, Object> submit(UUID tenantId, TenantPrincipal actor, UUID structureId) {
        Map<String, Object> structure = jdbc.query("""
            SELECT fs.institution_id, fs.academic_session_id, fs.program_year_id, fs.class_id,
                   fs.version_no, fs.status, fs.applies_from_date, i.institution_type
              FROM app.fee_structure fs
              JOIN app.institution i
                ON i.tenant_id=fs.tenant_id AND i.institution_id=fs.institution_id
             WHERE fs.tenant_id=:tenantId AND fs.fee_structure_id=:structureId
             FOR UPDATE OF fs
            """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("structureId", structureId),
                rs -> {
                    if (!rs.next()) return null;
                    return Map.of(
                            "institutionId", rs.getObject("institution_id", UUID.class),
                            "institutionType", rs.getString("institution_type"),
                            "academicSessionId", rs.getObject("academic_session_id", UUID.class),
                            "versionNo", rs.getInt("version_no"),
                            "status", rs.getString("status"),
                            "appliesFromDate", rs.getDate("applies_from_date") == null
                                    ? LocalDate.MIN : rs.getDate("applies_from_date").toLocalDate());
                });
        if (structure == null) throw new ResourceNotFoundException("Fee structure not found.");
        UUID institutionId = (UUID) structure.get("institutionId");
        permissions.authorize(new RbacPrincipal(tenantId, actor.userAccountId()),
                "FEE_STRUCTURE_CREATE", RbacScope.institution(institutionId), RbacTarget.NONE);
        if (!"DRAFT".equals(structure.get("status"))) {
            throw new BusinessRuleViolationException("FEE_STRUCTURE_NOT_DRAFT",
                    "Only a DRAFT fee structure can be submitted.");
        }
        if ((Integer) structure.get("versionNo") > 1 && LocalDate.MIN.equals(structure.get("appliesFromDate"))) {
            throw new BusinessRuleViolationException("FEE_STRUCTURE_EFFECTIVE_DATE_REQUIRED",
                    "A revised fee structure requires an effective-from date.");
        }

        String principalRole = "SCHOOL".equalsIgnoreCase((String) structure.get("institutionType"))
                ? "SCHOOL_PRINCIPAL" : "PRINCIPAL_COLLEGE";
        String activeRole = TenantContext.currentActiveRoleCode();
        if (activeRole == null || activeRole.isBlank()) {
            throw new BusinessRuleViolationException("ACTIVE_ROLE_REQUIRED", "Select the active Accountant role.");
        }
        UUID approvalRequestId = UUID.randomUUID();
        Instant now = Instant.now();
        ApprovalRequest approval = ApprovalRequest.builder()
                .id(new ApprovalRequestId(tenantId, approvalRequestId))
                .institutionId(institutionId)
                .processCode("FEE_STRUCTURE")
                .subjectType("FEE_STRUCTURE")
                .subjectId(structureId)
                .requestedByUserId(actor.userAccountId())
                .requesterActiveRole(activeRole)
                .status("PENDING")
                .currentStepNo((short) 1)
                .createdAt(now).createdBy(actor.userAccountId())
                .updatedAt(now).updatedBy(actor.userAccountId())
                .rowVersion(1)
                .build();
        approvalRequests.save(approval);

        jdbc.update("""
            UPDATE app.fee_structure
               SET status='PENDING_APPROVAL', approval_request_id=:approvalRequestId,
                   updated_at=now(), updated_by=:actorId
             WHERE tenant_id=:tenantId AND fee_structure_id=:structureId AND status='DRAFT'
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("structureId", structureId).addValue("approvalRequestId", approvalRequestId)
                    .addValue("actorId", actor.userAccountId()));

        approvalSteps.save(ApprovalStep.builder()
                .id(new ApprovalStepId(tenantId, UUID.randomUUID()))
                .approvalRequestId(approvalRequestId).stepNo((short) 1)
                .approverRoleCode(principalRole).decision("PENDING")
                .createdAt(now).createdBy(actor.userAccountId())
                .updatedAt(now).updatedBy(actor.userAccountId()).rowVersion(1).build());

        audit.record(AuditEventType.CREATE, null, actor.userAccountId().toString(), tenantId.toString(),
                "Fee structure " + structureId + " submitted for Principal approval.");
        return Map.of("feeStructureId", structureId, "approvalRequestId", approvalRequestId,
                "status", "PENDING_APPROVAL", "approverRole", principalRole);
    }

    private static void validateSchedule(FeeStructureRequest request) {
        if (request.institutionId() == null || request.academicSessionId() == null || request.feePolicyId() == null) {
            throw new IllegalArgumentException("Institution, academic session and fee policy are required.");
        }
        if ((request.programYearId() == null) == (request.classId() == null)) {
            throw new IllegalArgumentException("Exactly one of programYearId or classId is required.");
        }
        if (request.lines() == null || request.lines().isEmpty()
                || request.installments() == null || request.installments().isEmpty()) {
            throw new IllegalArgumentException("At least one fee line and installment are required.");
        }
        BigDecimal lineTotal = request.lines().stream().map(FeeStructureLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal installmentTotal = request.installments().stream().map(FeeStructureInstallment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lineTotal.compareTo(installmentTotal) != 0) {
            throw new BusinessRuleViolationException("FEE_STRUCTURE_TOTAL_MISMATCH",
                    "The sum of fee lines must equal the sum of installment amounts.");
        }
        int previousNo = 0;
        LocalDate previousDate = null;
        for (FeeStructureInstallment installment : request.installments()) {
            if (installment.installmentNo() <= previousNo || installment.amount() == null
                    || installment.amount().signum() <= 0 || installment.dueDate() == null
                    || (previousDate != null && !installment.dueDate().isAfter(previousDate))) {
                throw new BusinessRuleViolationException("FEE_STRUCTURE_INSTALLMENT_ORDER_INVALID",
                        "Installments must have increasing numbers, dates and positive full amounts.");
            }
            previousNo = installment.installmentNo();
            previousDate = installment.dueDate();
        }
        for (FeeStructureLine line : request.lines()) {
            if (line.feeHeadId() == null || line.amount() == null || line.amount().signum() < 0) {
                throw new IllegalArgumentException("Each fee line needs a fee head and a non-negative amount.");
            }
        }
    }

    public record FeeStructureLine(UUID feeHeadId, BigDecimal amount) {}
    public record FeeStructureInstallment(int installmentNo, LocalDate dueDate, BigDecimal amount) {}
    public record FeeStructureRequest(UUID institutionId, UUID academicSessionId, UUID programYearId,
            UUID classId, UUID feePolicyId, LocalDate appliesFromDate,
            List<FeeStructureLine> lines, List<FeeStructureInstallment> installments) {}
}
