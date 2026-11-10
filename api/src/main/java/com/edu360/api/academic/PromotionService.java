package com.edu360.api.academic;

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
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionId;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.tenant.TenantContext;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class PromotionService {

    private final PromotionRunRepository repository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final InstitutionRepository institutionRepository;

    @Autowired
    public PromotionService(
            PromotionRunRepository repository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService,
            @Autowired(required = false) ApprovalRequestRepository approvalRequestRepository,
            @Autowired(required = false) ApprovalStepRepository approvalStepRepository,
            @Autowired(required = false) InstitutionRepository institutionRepository) {
        this.repository = repository;
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.auditLogService = auditLogService;
        this.approvalRequestRepository = approvalRequestRepository;
        this.approvalStepRepository = approvalStepRepository;
        this.institutionRepository = institutionRepository;
    }

    public PromotionService(
            PromotionRunRepository repository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService,
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalStepRepository approvalStepRepository) {
        this(repository, permissionEvaluatorService, auditLogService, approvalRequestRepository, approvalStepRepository, null);
    }

    public PromotionService(
            PromotionRunRepository repository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService) {
        this(repository, permissionEvaluatorService, auditLogService, null, null, null);
    }

    @Transactional
    public PromotionRunResponse createPromotionRun(UUID tenantId, TenantPrincipal principal, CreatePromotionRunRequest request) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "PROMOTION_RUN",
                RbacScope.institution(request.institutionId()),
                RbacTarget.NONE
        );

        if (request.fromSessionId().equals(request.toSessionId())) {
            throw new BusinessRuleViolationException("INVALID_SESSION_RANGE",
                    "From session and To session must be distinct [Rule R1].");
        }

        UUID runId = UUID.randomUUID();
        PromotionRun run = PromotionRun.builder()
                .id(new PromotionRunId(tenantId, runId))
                .institutionId(request.institutionId())
                .fromSessionId(request.fromSessionId())
                .toSessionId(request.toSessionId())
                .status("DRAFT")
                .createdAt(Instant.now())
                .createdBy(principal.userAccountId())
                .updatedAt(Instant.now())
                .updatedBy(principal.userAccountId())
                .rowVersion(1)
                .build();

        run = repository.save(run);

        auditLogService.record(
                AuditEventType.ACADEMIC_SESSION_CREATED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Promotion run " + runId + " created in draft"
        );

        return PromotionRunResponse.from(run);
    }

    @Transactional(readOnly = true)
    public PromotionRunResponse getPromotionRun(UUID tenantId, TenantPrincipal principal, UUID runId) {
        PromotionRun run = repository.findByIdTenantIdAndIdPromotionRunId(tenantId, runId)
                .orElseThrow(() -> new BusinessRuleViolationException("PROMOTION_RUN_NOT_FOUND", "Promotion run not found"));

        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "PROMOTION_RUN",
                RbacScope.institution(run.getInstitutionId()),
                RbacTarget.NONE
        );

        return PromotionRunResponse.from(run);
    }

    @Transactional
    public PromotionRunResponse submitPromotionRun(UUID tenantId, TenantPrincipal principal, UUID runId) {
        PromotionRun run = repository.findByIdTenantIdAndIdPromotionRunId(tenantId, runId)
                .orElseThrow(() -> new BusinessRuleViolationException("PROMOTION_RUN_NOT_FOUND", "Promotion run not found"));

        if (!"DRAFT".equalsIgnoreCase(run.getStatus())) {
            throw new BusinessRuleViolationException("INVALID_STATUS", "Only DRAFT promotion runs can be submitted.");
        }

        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "PROMOTION_RUN",
                RbacScope.institution(run.getInstitutionId()),
                RbacTarget.NONE
        );

        run.setStatus("PENDING_APPROVAL");
        UUID approvalRequestId = UUID.randomUUID();
        run.setApprovalRequestId(approvalRequestId);
        run.setUpdatedAt(Instant.now());
        run.setUpdatedBy(principal.userAccountId());
        run = repository.save(run);

        if (approvalRequestRepository != null && approvalStepRepository != null) {
            String activeRole = TenantContext.currentActiveRoleCode() != null ? TenantContext.currentActiveRoleCode() : "EXAM_REGISTRAR";
            String approverRole = "PRINCIPAL_COLLEGE";
            if (institutionRepository != null && run.getInstitutionId() != null) {
                approverRole = institutionRepository.findById(new InstitutionId(tenantId, run.getInstitutionId()))
                        .map(inst -> "SCHOOL".equalsIgnoreCase(inst.getInstitutionType()) ? "SCHOOL_PRINCIPAL" : "PRINCIPAL_COLLEGE")
                        .orElse("PRINCIPAL_COLLEGE");
            }

            ApprovalRequest approvalReq = ApprovalRequest.builder()
                    .id(new ApprovalRequestId(tenantId, approvalRequestId))
                    .institutionId(run.getInstitutionId())
                    .processCode("PROMOTION")
                    .status("PENDING")
                    .subjectType("PROMOTION_RUN")
                    .subjectId(runId)
                    .requestedByUserId(principal.userAccountId())
                    .requesterActiveRole(activeRole)
                    .currentStepNo((short) 1)
                    .createdAt(Instant.now())
                    .createdBy(principal.userAccountId())
                    .updatedAt(Instant.now())
                    .updatedBy(principal.userAccountId())
                    .rowVersion(1)
                    .build();
            approvalRequestRepository.save(approvalReq);

            ApprovalStep step = ApprovalStep.builder()
                    .id(new ApprovalStepId(tenantId, UUID.randomUUID()))
                    .approvalRequestId(approvalRequestId)
                    .stepNo((short) 1)
                    .approverRoleCode(approverRole)
                    .decision("PENDING")
                    .createdAt(Instant.now())
                    .createdBy(principal.userAccountId())
                    .updatedAt(Instant.now())
                    .updatedBy(principal.userAccountId())
                    .rowVersion(1)
                    .build();
            approvalStepRepository.save(step);
        }

        auditLogService.record(
                AuditEventType.ACADEMIC_SESSION_CREATED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Promotion run " + runId + " submitted for Principal approval"
        );

        return PromotionRunResponse.from(run);
    }

    @Transactional
    public PromotionRunResponse decidePromotionRun(UUID tenantId, TenantPrincipal principal, UUID runId, PromotionDecisionRequest request) {
        PromotionRun run = repository.findByIdTenantIdAndIdPromotionRunId(tenantId, runId)
                .orElseThrow(() -> new BusinessRuleViolationException("PROMOTION_RUN_NOT_FOUND", "Promotion run not found"));

        if (!"PENDING_APPROVAL".equalsIgnoreCase(run.getStatus()) && !"SUBMITTED".equalsIgnoreCase(run.getStatus())) {
            throw new BusinessRuleViolationException("INVALID_STATUS", "Only pending promotion runs can be decided.");
        }

        if (run.getCreatedBy() != null && principal.userAccountId().equals(run.getCreatedBy())) {
            throw new UnauthorizedException("Self-approval forbidden: the requester cannot approve their own promotion run.");
        }

        // P-04: the step and the audit record the real active role; with none the decision is denied.
        final String decidingRole = TenantContext.currentActiveRoleCode();
        if (decidingRole == null) {
            throw new UnauthorizedException("No active role: choose an active role before deciding a promotion run.");
        }

        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "PROMOTION_APPROVE",
                RbacScope.institution(run.getInstitutionId()),
                RbacTarget.of(run.getCreatedBy())
        );

        if (!"APPROVED".equalsIgnoreCase(request.decision()) && (request.comment() == null || request.comment().isBlank())) {
            throw new BusinessRuleViolationException("REMARK_REQUIRED", "Rejection remark is mandatory");
        }

        boolean approved = "APPROVED".equalsIgnoreCase(request.decision());
        if (approved) {
            run.setStatus("APPROVED");
        } else {
            run.setStatus("REJECTED");
        }
        run.setUpdatedAt(Instant.now());
        run.setUpdatedBy(principal.userAccountId());
        run = repository.save(run);

        if (approvalRequestRepository != null && run.getApprovalRequestId() != null) {
            approvalRequestRepository.findByIdTenantIdAndIdApprovalRequestId(tenantId, run.getApprovalRequestId())
                    .ifPresent(req -> {
                        req.setStatus(approved ? "APPROVED" : "REJECTED");
                        req.setFinalDecidedAt(Instant.now());
                        req.setUpdatedAt(Instant.now());
                        req.setUpdatedBy(principal.userAccountId());
                        approvalRequestRepository.save(req);

                        if (approvalStepRepository != null) {
                            approvalStepRepository.findByIdTenantIdAndApprovalRequestIdAndStepNo(
                                    tenantId, req.getId().getApprovalRequestId(), req.getCurrentStepNo())
                                    .ifPresent(st -> {
                                        st.setDecision(approved ? "APPROVED" : "REJECTED");
                                        st.setActedByUserId(principal.userAccountId());
                                        String activeRole = decidingRole;
                                        st.setActedActiveRole(activeRole);
                                        st.setDecidedAt(Instant.now());
                                        st.setRemark(request.comment());
                                        approvalStepRepository.save(st);
                                    });
                        }
                    });
        }

        auditLogService.record(
                AuditEventType.APPROVAL_BULK_DECIDED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Promotion run " + runId + " decided: " + run.getStatus()
        );

        return PromotionRunResponse.from(run);
    }

    @Transactional
    public PromotionRunResponse executePromotionRun(UUID tenantId, TenantPrincipal principal, UUID runId) {
        PromotionRun run = repository.findByIdTenantIdAndIdPromotionRunId(tenantId, runId)
                .orElseThrow(() -> new BusinessRuleViolationException("PROMOTION_RUN_NOT_FOUND", "Promotion run not found"));

        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "PROMOTION_RUN",
                RbacScope.institution(run.getInstitutionId()),
                RbacTarget.NONE
        );

        if (!"APPROVED".equals(run.getStatus())) {
            throw new BusinessRuleViolationException("RUN_NOT_APPROVED",
                    "Promotion run must be approved by Principal before execution.");
        }

        run.setStatus("EXECUTED");
        run.setExecutedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        run.setUpdatedBy(principal.userAccountId());
        run = repository.save(run);

        auditLogService.record(
                AuditEventType.ACADEMIC_SESSION_CREATED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Promotion run " + runId + " executed"
        );

        return PromotionRunResponse.from(run);
    }
}
