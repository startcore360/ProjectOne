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
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConcessionService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final ApprovalRequestRepository approvalRequests;
    private final ApprovalStepRepository approvalSteps;
    private final AuditLogService audit;

    @Transactional
    public Map<String, Object> request(UUID tenantId, TenantPrincipal actor, ConcessionRequest request) {
        Map<String, Object> target = jdbc.query("""
            SELECT sfa.institution_id, si.student_installment_id, si.status AS installment_status,
                   nd.net_due, fs.fee_structure_id
              FROM app.student_fee_account sfa
              JOIN app.student_installment si
                ON si.tenant_id=sfa.tenant_id
               AND si.student_fee_account_id=sfa.student_fee_account_id
              JOIN app.fee_assignment fa
                ON fa.tenant_id=si.tenant_id AND fa.fee_assignment_id=si.fee_assignment_id
              JOIN app.fee_structure fs
                ON fs.tenant_id=fa.tenant_id AND fs.fee_structure_id=fa.fee_structure_id
              JOIN app.v_installment_net_due nd
                ON nd.tenant_id=si.tenant_id AND nd.student_installment_id=si.student_installment_id
             WHERE sfa.tenant_id=:tenantId
               AND sfa.student_fee_account_id=:accountId
               AND si.student_installment_id=:installmentId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("accountId", request.studentFeeAccountId())
                    .addValue("installmentId", request.studentInstallmentId()), rs -> {
                if (!rs.next()) return null;
                return Map.of("institutionId", rs.getObject("institution_id", UUID.class),
                        "installmentStatus", rs.getString("installment_status"),
                        "netDue", rs.getBigDecimal("net_due"),
                        "feeStructureId", rs.getObject("fee_structure_id", UUID.class));
            });
        if (target == null) throw new ResourceNotFoundException("Student installment not found for the fee account.");

        UUID institutionId = (UUID) target.get("institutionId");
        permissions.authorize(new RbacPrincipal(tenantId, actor.userAccountId()),
                "CONCESSION_REQUEST", RbacScope.institution(institutionId), RbacTarget.NONE);

        if (!"UNPAID".equals(target.get("installmentStatus"))) {
            throw new BusinessRuleViolationException("CONCESSION_INSTALLMENT_NOT_OPEN",
                    "A concession can only be requested against an unpaid installment.");
        }
        BigDecimal netDue = (BigDecimal) target.get("netDue");
        if (request.amount().compareTo(netDue) > 0) {
            throw new BusinessRuleViolationException("CONCESSION_EXCEEDS_OUTSTANDING",
                    "The concession cannot exceed the installment's current net due.");
        }
        Integer matchingHead = jdbc.queryForObject("""
            SELECT COUNT(*) FROM app.fee_structure_line
             WHERE tenant_id=:tenantId AND fee_structure_id=:feeStructureId AND fee_head_id=:feeHeadId
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                    .addValue("feeStructureId", target.get("feeStructureId"))
                    .addValue("feeHeadId", request.feeHeadId()), Integer.class);
        if (matchingHead == null || matchingHead == 0) {
            throw new BusinessRuleViolationException("CONCESSION_FEE_HEAD_MISMATCH",
                    "The selected fee head is not part of the installment's fee structure.");
        }

        UUID concessionId = UUID.randomUUID();
        UUID approvalRequestId = UUID.randomUUID();
        Instant now = Instant.now();
        String activeRole = TenantContext.currentActiveRoleCode();
        if (activeRole == null || activeRole.isBlank()) {
            throw new BusinessRuleViolationException("ACTIVE_ROLE_REQUIRED",
                    "Select the active Accountant role before requesting a concession.");
        }

        jdbc.update("""
            INSERT INTO app.concession
              (tenant_id, concession_id, institution_id, student_fee_account_id,
               student_installment_id, fee_head_id, amount, reason, requested_by,
               approval_request_id, status, created_by, updated_by)
            VALUES
              (:tenantId, :concessionId, :institutionId, :accountId,
               :installmentId, :feeHeadId, :amount, :reason, :actorId,
               :approvalRequestId, 'REQUESTED', :actorId, :actorId)
            """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("concessionId", concessionId)
                .addValue("institutionId", institutionId).addValue("accountId", request.studentFeeAccountId())
                .addValue("installmentId", request.studentInstallmentId()).addValue("feeHeadId", request.feeHeadId())
                .addValue("amount", request.amount()).addValue("reason", request.reason())
                .addValue("actorId", actor.userAccountId()).addValue("approvalRequestId", approvalRequestId));

        ApprovalRequest approval = ApprovalRequest.builder()
                .id(new ApprovalRequestId(tenantId, approvalRequestId))
                .institutionId(institutionId)
                .processCode("CONCESSION")
                .subjectType("CONCESSION")
                .subjectId(concessionId)
                .requestedByUserId(actor.userAccountId())
                .requesterActiveRole(activeRole)
                .status("PENDING")
                .currentStepNo((short) 1)
                .createdAt(now).createdBy(actor.userAccountId())
                .updatedAt(now).updatedBy(actor.userAccountId())
                .rowVersion(1)
                .build();
        approvalRequests.save(approval);

        // Management is always the actual step role. ApprovalRouting resolves it to the
        // Principal by default when no Management assignment exists, or to an explicitly
        // configured designated financial approver through the existing roll-down override.
        ApprovalStep step = ApprovalStep.builder()
                .id(new ApprovalStepId(tenantId, UUID.randomUUID()))
                .approvalRequestId(approvalRequestId)
                .stepNo((short) 1)
                .approverRoleCode("MANAGEMENT")
                .decision("PENDING")
                .createdAt(now).createdBy(actor.userAccountId())
                .updatedAt(now).updatedBy(actor.userAccountId())
                .rowVersion(1)
                .build();
        approvalSteps.save(step);

        audit.record(AuditEventType.CREATE, null, actor.userAccountId().toString(),
                tenantId.toString(), "Fee concession " + concessionId + " submitted for PO-T04 approval.");
        return Map.of("concessionId", concessionId, "approvalRequestId", approvalRequestId,
                "status", "REQUESTED", "approverRole", "MANAGEMENT");
    }

    public record ConcessionRequest(UUID studentFeeAccountId, UUID studentInstallmentId,
            UUID feeHeadId, BigDecimal amount, String reason) {
        public ConcessionRequest {
            if (studentFeeAccountId == null || studentInstallmentId == null || feeHeadId == null) {
                throw new IllegalArgumentException("Fee account, installment and fee head are required.");
            }
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Concession amount must be positive.");
            }
            if (reason == null || reason.isBlank() || reason.length() > 1000) {
                throw new IllegalArgumentException("Concession reason must contain 1–1000 characters.");
            }
        }
    }
}
