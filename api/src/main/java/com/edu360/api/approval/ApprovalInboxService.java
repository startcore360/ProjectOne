package com.edu360.api.approval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.academic.PromotionRun;
import com.edu360.api.academic.PromotionRunRepository;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.guardian.StudentGuardianLink;
import com.edu360.api.guardian.StudentGuardianLinkRepository;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.approval.dto.ApprovalInboxResponseDto;
import com.edu360.api.approval.dto.ApprovalStepDto;
import com.edu360.api.approval.dto.ApprovalSummaryDto;
import com.edu360.api.approval.dto.BulkApproveRequestDto;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.approval.dto.DecideApprovalResponseDto;

import lombok.RequiredArgsConstructor;

@Service
public class ApprovalInboxService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalInboxService.class);

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final AuditLogService auditLogService;
    private final StudentGuardianLinkRepository studentGuardianLinkRepository;
    private final InstitutionRepository institutionRepository;
    private final PromotionRunRepository promotionRunRepository;

    @Autowired
    public ApprovalInboxService(
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalStepRepository approvalStepRepository,
            AuditLogService auditLogService,
            @Autowired(required = false) StudentGuardianLinkRepository studentGuardianLinkRepository,
            @Autowired(required = false) InstitutionRepository institutionRepository,
            @Autowired(required = false) PromotionRunRepository promotionRunRepository) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.approvalStepRepository = approvalStepRepository;
        this.auditLogService = auditLogService;
        this.studentGuardianLinkRepository = studentGuardianLinkRepository;
        this.institutionRepository = institutionRepository;
        this.promotionRunRepository = promotionRunRepository;
    }

    public ApprovalInboxService(
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalStepRepository approvalStepRepository,
            AuditLogService auditLogService) {
        this(approvalRequestRepository, approvalStepRepository, auditLogService, null, null, null);
    }

    @Transactional(readOnly = true)
    public ApprovalInboxResponseDto listPendingApprovals(UUID tenantId, TenantPrincipal principal) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(principal, "principal is required");

        String activeRole = TenantContext.currentActiveRoleCode();
        UUID userId = principal.userAccountId();

        List<ApprovalStep> pendingSteps = new ArrayList<>();
        if (activeRole != null) {
            pendingSteps.addAll(approvalStepRepository.findByIdTenantIdAndApproverRoleCodeAndDecision(tenantId, activeRole, "PENDING"));
        }
        pendingSteps.addAll(approvalStepRepository.findByIdTenantIdAndApproverUserIdAndDecision(tenantId, userId, "PENDING"));

        // Filter out self-requests and ensure request is PENDING
        List<ApprovalStepDto> items = new ArrayList<>();
        for (ApprovalStep step : pendingSteps) {
            ApprovalRequest req = approvalRequestRepository
                    .findByIdTenantIdAndIdApprovalRequestId(tenantId, step.getApprovalRequestId())
                    .orElse(null);

            if (req != null && "PENDING".equals(req.getStatus())) {
                // Rule 11: Prohibit self-approval
                if (req.getRequestedByUserId().equals(userId)) {
                    continue;
                }
                items.add(toStepDto(step));
            }
        }

        return new ApprovalInboxResponseDto(items, new ApprovalInboxResponseDto.PageInfoDto(items.size(), 1, 1, items.size()));
    }

    @Transactional(readOnly = true)
    public ApprovalSummaryDto getApprovalRequest(UUID tenantId, UUID approvalRequestId, TenantPrincipal principal) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(approvalRequestId, "approvalRequestId is required");

        ApprovalRequest request = approvalRequestRepository
                .findByIdTenantIdAndIdApprovalRequestId(tenantId, approvalRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request " + approvalRequestId + " not found"));

        List<ApprovalStep> steps = approvalStepRepository
                .findByIdTenantIdAndApprovalRequestIdOrderByStepNoAsc(tenantId, approvalRequestId);

        return toSummaryDto(request, steps);
    }

    @Transactional
    public DecideApprovalResponseDto decideApproval(
            UUID tenantId,
            UUID approvalRequestId,
            DecideApprovalRequestDto requestDto,
            TenantPrincipal principal) {

        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(approvalRequestId, "approvalRequestId is required");
        Objects.requireNonNull(requestDto, "requestDto is required");
        Objects.requireNonNull(principal, "principal is required");

        ApprovalRequest request = approvalRequestRepository
                .findByIdTenantIdAndIdApprovalRequestId(tenantId, approvalRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request " + approvalRequestId + " not found"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessRuleViolationException(
                    "REQUEST_NOT_PENDING",
                    "Approval request is not in PENDING state (status=" + request.getStatus() + ")"
            );
        }

        // Rule 11: Self-approval prohibited
        if (request.getRequestedByUserId().equals(principal.userAccountId())) {
            log.warn("Security violation: user {} attempted to decide their own approval request {}",
                    principal.userAccountId(), approvalRequestId);
            throw new BusinessRuleViolationException(
                    "SELF_APPROVAL_FORBIDDEN",
                    "A principal may not approve or decide their own request (Addendum rule 11)."
            );
        }

        short stepNo = requestDto.stepNo() != null ? requestDto.stepNo() : request.getCurrentStepNo();
        ApprovalStep step = approvalStepRepository
                .findByIdTenantIdAndApprovalRequestIdAndStepNo(tenantId, approvalRequestId, stepNo)
                .orElseThrow(() -> new ResourceNotFoundException("Approval step " + stepNo + " not found for request " + approvalRequestId));

        if (!"PENDING".equals(step.getDecision())) {
            throw new BusinessRuleViolationException(
                    "STEP_ALREADY_DECIDED",
                    "Step " + stepNo + " has already been decided (decision=" + step.getDecision() + ")"
            );
        }

        String decision = requestDto.decision().trim().toUpperCase();
        if ("REJECTED".equals(decision) && (requestDto.remark() == null || requestDto.remark().isBlank())) {
            throw new BusinessRuleViolationException(
                    "REJECTION_REMARK_REQUIRED",
                    "A remark is mandatory when rejecting an approval request."
            );
        }

        Instant now = Instant.now();
        String activeRole = TenantContext.currentActiveRoleCode();
        if (activeRole == null) {
            activeRole = step.getApproverRoleCode();
        }

        step.setDecision(decision);
        step.setActedByUserId(principal.userAccountId());
        step.setActedActiveRole(activeRole);
        step.setRemark(requestDto.remark());
        step.setDecidedAt(now);
        step.setUpdatedAt(now);
        step.setUpdatedBy(principal.userAccountId());
        approvalStepRepository.save(step);

        String subjectNewStatus;
        if ("APPROVED".equals(decision)) {
            // Check if there are further steps
            short nextStepNo = (short) (stepNo + 1);
            boolean hasNext = approvalStepRepository
                    .findByIdTenantIdAndApprovalRequestIdAndStepNo(tenantId, approvalRequestId, nextStepNo)
                    .isPresent();

            if (hasNext) {
                request.setCurrentStepNo(nextStepNo);
                request.setUpdatedAt(now);
                request.setUpdatedBy(principal.userAccountId());
                approvalRequestRepository.save(request);
                subjectNewStatus = "IN_PROGRESS";
            } else {
                request.setStatus("APPROVED");
                request.setFinalDecidedAt(now);
                request.setUpdatedAt(now);
                request.setUpdatedBy(principal.userAccountId());
                approvalRequestRepository.save(request);
                subjectNewStatus = "APPROVED";
                applyProcessSideEffect(tenantId, request, true, requestDto.remark(), principal);
            }
        } else if ("REJECTED".equals(decision)) {
            request.setStatus("REJECTED");
            request.setFinalDecidedAt(now);
            request.setUpdatedAt(now);
            request.setUpdatedBy(principal.userAccountId());
            approvalRequestRepository.save(request);
            subjectNewStatus = "REJECTED";
            applyProcessSideEffect(tenantId, request, false, requestDto.remark(), principal);
        } else {
            subjectNewStatus = "FORWARDED";
        }

        // Record OD-09 audit
        auditLogService.record(
                AuditEventType.APPROVAL,
                null,
                request.getSubjectId().toString(),
                null,
                "Approval request " + approvalRequestId + " step " + stepNo + " decided as " + decision + " by " + activeRole
        );

        List<ApprovalStep> allSteps = approvalStepRepository
                .findByIdTenantIdAndApprovalRequestIdOrderByStepNoAsc(tenantId, approvalRequestId);

        return new DecideApprovalResponseDto(toSummaryDto(request, allSteps), subjectNewStatus, List.of());
    }

    @Transactional
    public void bulkApprove(UUID tenantId, BulkApproveRequestDto bulkRequest, TenantPrincipal principal) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(bulkRequest, "bulkRequest is required");

        for (BulkApproveRequestDto.BulkApproveItem item : bulkRequest.items()) {
            decideApproval(tenantId, item.approvalRequestId(),
                    new DecideApprovalRequestDto(item.stepNo(), "APPROVED", bulkRequest.comment(), item.expectedVersion()),
                    principal);
        }
    }

    private void applyProcessSideEffect(UUID tenantId, ApprovalRequest request, boolean approved, String remark, TenantPrincipal principal) {
        if ("STUDENT_GUARDIAN_LINK".equals(request.getSubjectType()) && studentGuardianLinkRepository != null) {
            studentGuardianLinkRepository.findByIdTenantIdAndIdStudentGuardianLinkId(tenantId, request.getSubjectId())
                    .ifPresent(link -> {
                        if (approved) {
                            link.setStatus("BLOCKED");
                            link.setBlockedAt(Instant.now());
                        } else {
                            link.setChangeApprovalRequestId(null);
                            link.setBlockReason(null);
                        }
                        link.setUpdatedAt(Instant.now());
                        link.setUpdatedBy(principal.userAccountId());
                        studentGuardianLinkRepository.save(link);
                    });
        } else if ("INSTITUTION".equals(request.getSubjectType()) && institutionRepository != null) {
            institutionRepository.findByIdTenantIdAndIdInstitutionId(tenantId, request.getSubjectId())
                    .ifPresent(inst -> {
                        if (approved) {
                            inst.setStatus("BLOCKED");
                            inst.setBlockedAt(Instant.now());
                            inst.setUpdatedAt(Instant.now());
                            inst.setUpdatedBy(principal.userAccountId());
                            institutionRepository.save(inst);
                        }
                    });
        } else if ("PROMOTION_RUN".equals(request.getSubjectType()) && promotionRunRepository != null) {
            promotionRunRepository.findByIdTenantIdAndIdPromotionRunId(tenantId, request.getSubjectId())
                    .ifPresent(run -> {
                        run.setStatus(approved ? "APPROVED" : "REJECTED");
                        run.setUpdatedAt(Instant.now());
                        run.setUpdatedBy(principal.userAccountId());
                        promotionRunRepository.save(run);
                    });
        }
    }

    private ApprovalStepDto toStepDto(ApprovalStep step) {
        return new ApprovalStepDto(
                step.getStepNo(),
                step.getApproverRoleCode(),
                step.getDecision(),
                step.getActedByUserId(),
                step.getActedActiveRole(),
                step.getRemark(),
                step.getSkipReason(),
                step.getDecidedAt()
        );
    }

    private ApprovalSummaryDto toSummaryDto(ApprovalRequest request, List<ApprovalStep> steps) {
        return new ApprovalSummaryDto(
                request.getId().getApprovalRequestId(),
                request.getProcessCode(),
                request.getStatus(),
                request.getCurrentStepNo(),
                new ApprovalSummaryDto.SubjectRef(request.getSubjectType(), request.getSubjectId()),
                steps.stream().map(this::toStepDto).toList()
        );
    }
}
