package com.edu360.api.institution;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.approval.ApprovalInboxService;
import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.approval.ApprovalRequestId;
import com.edu360.api.approval.ApprovalRequestRepository;
import com.edu360.api.approval.ApprovalStep;
import com.edu360.api.approval.ApprovalStepId;
import com.edu360.api.approval.ApprovalStepRepository;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.institution.dto.BlockDecisionRequest;
import com.edu360.api.institution.dto.BlockInstitutionRequest;
import com.edu360.api.institution.dto.CreateInstitutionRequest;
import com.edu360.api.institution.dto.InstitutionBlockResponse;
import com.edu360.api.institution.dto.InstitutionPageResponse;
import com.edu360.api.institution.dto.InstitutionResponse;
import com.edu360.api.platform.Plan;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service managing Institution lifecycle inside a tenant:
 * - INSTITUTION_CREATE_DRAFT (Sprint 1 Story #25 / WF-16, WF-14)
 * - INSTITUTION_ACTIVATE (Sprint 1 Story #26 / WF-16, rule C7)
 */
@Service
public class InstitutionService {

    static final String BLOCK_PROCESS = "INSTITUTION_BLOCK";
    static final String ACTIVATION_PROCESS = "INSTITUTION_ACTIVATION";

    private static final Logger log = LoggerFactory.getLogger(InstitutionService.class);

    private final InstitutionRepository institutionRepository;
    private final LeaveApproverSettingRepository leaveApproverSettingRepository;
    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalStepRepository approvalStepRepository;


    private final ApprovalInboxService approvalInboxService;

    @Autowired(required = false)
    private InstitutionIdFormatService institutionIdFormatService;

    public InstitutionService(
            InstitutionRepository institutionRepository,
            LeaveApproverSettingRepository leaveApproverSettingRepository,
            TenantRepository tenantRepository,
            PlanRepository planRepository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService,
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalStepRepository approvalStepRepository,
            ApprovalInboxService approvalInboxService) {
        // P-03: the approval repositories are required; a missing one used to skip the chain silently.
        this.institutionRepository = institutionRepository;
        this.leaveApproverSettingRepository = leaveApproverSettingRepository;
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.auditLogService = auditLogService;
        this.approvalRequestRepository = Objects.requireNonNull(approvalRequestRepository);
        this.approvalStepRepository = Objects.requireNonNull(approvalStepRepository);
        this.approvalInboxService = Objects.requireNonNull(approvalInboxService);
    }


    /**
     * Creates a new institution in DRAFT state (INSTITUTION_CREATE_DRAFT).
     * Enforces:
     * 1. Authorization: University Admin at tenant/university scope.
     * 2. Plan limits: tenant.max_institutions via platform.plan (WF-14).
     * 3. Uniqueness: institution_code and institution_name unique within tenant.
     * 4. Initial status: DRAFT.
     */
    @Transactional
    public InstitutionResponse createDraft(RbacPrincipal principal, CreateInstitutionRequest request) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(request, "request is required");

        // 1. Authorize action
        permissionEvaluatorService.authorize(
                principal,
                "INSTITUTION_CREATE_DRAFT",
                RbacScope.tenant(),
                RbacTarget.NONE
        );

        UUID tenantId = principal.tenantId();

        // 2. Enforce Plan Limits (WF-14)
        enforcePlanLimits(tenantId);

        // 3. Enforce Uniqueness
        String code = request.institutionCode().trim().toUpperCase();
        if (institutionRepository.existsByIdTenantIdAndInstitutionCode(tenantId, code)) {
            throw new DuplicateResourceException(
                    "An institution with code '" + code + "' already exists in this tenant."
            );
        }

        String name = request.institutionName().trim();
        if (institutionRepository.existsByIdTenantIdAndInstitutionName(tenantId, name)) {
            throw new DuplicateResourceException(
                    "An institution with name '" + name + "' already exists in this tenant."
            );
        }

        // 4. Build and persist Institution entity
        UUID institutionId = UUID.randomUUID();
        Institution institution = Institution.builder()
                .id(new InstitutionId(tenantId, institutionId))
                .institutionType(request.institutionType())
                .institutionCode(code)
                .institutionName(name)
                .status("DRAFT")
                .externalUniversityMode(request.isExternalUniversityMode())
                .underUniversity(false)
                .addressLine(request.addressLine())
                .city(request.city())
                .stateName(request.stateName())
                .postalCode(request.postalCode())
                .principalInviteEmail(request.principalInviteEmail())
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .rowVersion(1)
                .build();

        Institution saved = institutionRepository.save(institution);
        if (institutionIdFormatService == null) {
            throw new IllegalStateException("Institution ID format service is required for institution creation.");
        }
        institutionIdFormatService.set(principal, institutionId,
                new InstitutionIdFormatsRequest(request.studentIdFormat(), request.staffIdFormat()));
        log.info("Created draft institution '{}' ({}) in tenant {}", name, institutionId, tenantId);

        auditLogService.record(
                AuditEventType.INSTITUTION_CREATED_DRAFT,
                null,
                principal.userAccountId().toString(),
                null,
                "Created draft institution " + name + " (" + code + ") in tenant " + tenantId
        );

        return InstitutionResponse.fromEntity(saved);
    }

    /**
     * Approves a pending activation request (INSTITUTION_ACTIVATE, the Vice Chancellor's step).
     * P-03: there is no direct path. A Draft, or a Blocked institution being reactivated, goes
     * live only through its own approval request (requestActivation) decided in the approvals
     * inbox; with no pending request this is 422. The C7 go-live gate (Principal's-leave
     * approver set) is enforced when the approval is applied.
     */
    @Transactional
    public InstitutionResponse activateInstitution(RbacPrincipal principal, UUID institutionId) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(institutionId, "institutionId is required");

        permissionEvaluatorService.authorize(principal, "INSTITUTION_ACTIVATE", RbacScope.tenant(), RbacTarget.NONE);

        UUID tenantId = principal.tenantId();
        Institution institution = institutionRepository.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution " + institutionId + " was not found."));
        ApprovalRequest pending = approvalInboxService
                .pendingRequestFor(tenantId, "INSTITUTION", institutionId, ACTIVATION_PROCESS)
                .orElseThrow(() -> new BusinessRuleViolationException("NO_PENDING_REQUEST",
                        "Institution " + institutionId + " (" + institution.getStatus() + ") has no pending activation request; "
                                + "activation and reactivation need their own request and approval."));
        approvalInboxService.decideApproval(tenantId, pending.getId().getApprovalRequestId(),
                new DecideApprovalRequestDto(pending.getCurrentStepNo(), "APPROVED", null, null), principal.userAccountId());
        return InstitutionResponse.fromEntity(institutionRepository
                .findByIdTenantIdAndIdInstitutionId(tenantId, institutionId).orElse(institution));
    }

    /**
     * Raises the activation request for a Draft or Blocked institution; the Vice Chancellor decides
     * it in the approvals inbox. The requester holds INSTITUTION_CREATE_DRAFT (University Admin).
     */
    @Transactional
    public InstitutionResponse requestActivation(RbacPrincipal principal, UUID institutionId) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(institutionId, "institutionId is required");

        permissionEvaluatorService.authorize(principal, "INSTITUTION_CREATE_DRAFT", RbacScope.tenant(), RbacTarget.NONE);

        UUID tenantId = principal.tenantId();
        Institution institution = institutionRepository.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution " + institutionId + " was not found."));
        if (!"DRAFT".equalsIgnoreCase(institution.getStatus()) && !"BLOCKED".equalsIgnoreCase(institution.getStatus())) {
            throw new BusinessRuleViolationException("INVALID_INSTITUTION_STATUS",
                    "Only a Draft or Blocked institution can be submitted for activation. Current status is "
                            + institution.getStatus() + ".");
        }
        if (approvalInboxService.pendingRequestFor(tenantId, "INSTITUTION", institutionId, ACTIVATION_PROCESS).isPresent()) {
            throw new DuplicateResourceException("An activation request for institution " + institutionId + " is already pending.");
        }
        createRequest(principal, institutionId, ACTIVATION_PROCESS);

        auditLogService.record(AuditEventType.INSTITUTION_ACTIVATION_REQUESTED, null, principal.userAccountId().toString(), null,
                "Activation requested for institution " + institution.getInstitutionName() + " (" + institutionId + ")");
        return InstitutionResponse.fromEntity(institution);
    }

    /**
     * Lists all institutions for the tenant.
     */
    @Transactional(readOnly = true)
    public InstitutionPageResponse listInstitutions(RbacPrincipal principal) {
        Objects.requireNonNull(principal, "principal is required");
        List<Institution> institutions = institutionRepository.findByIdTenantId(principal.tenantId());
        List<InstitutionResponse> responses = institutions.stream()
                .map(InstitutionResponse::fromEntity)
                .toList();
        return new InstitutionPageResponse(responses, responses.size());
    }

    /**
     * Retrieves a single institution by ID.
     */
    @Transactional(readOnly = true)
    public InstitutionResponse getInstitution(RbacPrincipal principal, UUID institutionId) {
        Objects.requireNonNull(principal, "principal is required");
        Institution institution = institutionRepository.findByIdTenantIdAndIdInstitutionId(principal.tenantId(), institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution " + institutionId + " was not found."));
        return InstitutionResponse.fromEntity(institution);
    }

    /**
     * Requests to block an institution's access (INSTITUTION_BLOCK_REQUEST / Rule RL-01).
     * P-03: always goes through the approval chain; a second pending block is 409.
     */
    @Transactional
    public InstitutionBlockResponse requestBlock(
            RbacPrincipal principal,
            UUID institutionId,
            BlockInstitutionRequest request) {

        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(institutionId, "institutionId is required");
        Objects.requireNonNull(request, "request is required");

        permissionEvaluatorService.authorize(principal, "INSTITUTION_BLOCK_REQUEST", RbacScope.tenant(), RbacTarget.of(institutionId));

        UUID tenantId = principal.tenantId();
        Institution institution = institutionRepository.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution " + institutionId + " was not found."));

        if (!"ACTIVE".equalsIgnoreCase(institution.getStatus())) {
            throw new BusinessRuleViolationException(
                    "INVALID_INSTITUTION_STATUS",
                    "Only active institutions can be submitted for blocking. Current status is " + institution.getStatus() + "."
            );
        }
        if (approvalInboxService.pendingRequestFor(tenantId, "INSTITUTION", institutionId, BLOCK_PROCESS).isPresent()) {
            throw new DuplicateResourceException("A block request for institution " + institutionId + " is already pending.");
        }
        createRequest(principal, institutionId, BLOCK_PROCESS);

        auditLogService.record(
                AuditEventType.INSTITUTION_BLOCK_REQUESTED,
                null,
                principal.userAccountId().toString(),
                null,
                "Block requested for institution " + institution.getInstitutionName() + " (" + institutionId + "). Reason: " + request.reason()
        );

        log.info("Institution block requested for '{}' ({}) in tenant {}. Reason: {}",
                institution.getInstitutionName(), institutionId, tenantId, request.reason());

        return new InstitutionBlockResponse(
                institutionId,
                institution.getInstitutionCode(),
                institution.getInstitutionName(),
                institution.getStatus(),
                institution.getBlockedAt(),
                "Block request submitted for executive approval. Reason: " + request.reason()
        );
    }

    /**
     * Approves or rejects the pending block request (INSTITUTION_BLOCK_APPROVE / Rule WF-15).
     * P-03: the decision is the approval step itself - it needs an existing pending request (422),
     * the decider cannot be the requester (403) and must be the step's approver; there is no
     * direct block. Approval blocks the institution (inbox side effect), rejection keeps it ACTIVE.
     */
    @Transactional
    public InstitutionBlockResponse decideBlock(
            RbacPrincipal principal,
            UUID institutionId,
            BlockDecisionRequest decisionRequest) {

        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(institutionId, "institutionId is required");
        Objects.requireNonNull(decisionRequest, "decisionRequest is required");

        permissionEvaluatorService.authorize(principal, "INSTITUTION_BLOCK_APPROVE", RbacScope.tenant(), RbacTarget.NONE);

        UUID tenantId = principal.tenantId();
        Institution institution = institutionRepository.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution " + institutionId + " was not found."));
        ApprovalRequest pending = approvalInboxService.pendingRequestFor(tenantId, "INSTITUTION", institutionId, BLOCK_PROCESS)
                .orElseThrow(() -> new BusinessRuleViolationException("NO_PENDING_REQUEST",
                        "Institution " + institutionId + " has no pending block request to decide."));

        boolean approved = Boolean.TRUE.equals(decisionRequest.approved());
        approvalInboxService.decideApproval(tenantId, pending.getId().getApprovalRequestId(),
                new DecideApprovalRequestDto(pending.getCurrentStepNo(), approved ? "APPROVED" : "REJECTED",
                        decisionRequest.decisionNotes(), null),
                principal.userAccountId());

        if (!approved) {
            auditLogService.record(AuditEventType.INSTITUTION_BLOCK_REJECTED, null, principal.userAccountId().toString(), null,
                    "Block request for institution " + institution.getInstitutionName() + " (" + institutionId
                            + ") was rejected. Notes: " + decisionRequest.decisionNotes());
        }
        Institution after = institutionRepository.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId).orElse(institution);
        return new InstitutionBlockResponse(
                institutionId,
                after.getInstitutionCode(),
                after.getInstitutionName(),
                after.getStatus(),
                after.getBlockedAt(),
                approved ? "Institution blocked successfully by executive approval."
                        : "Institution block request was rejected: "
                                + (decisionRequest.decisionNotes() != null ? decisionRequest.decisionNotes() : "No notes provided.")
        );
    }

    /** The request and its single Vice Chancellor step; the requester role is the caller's active role. */
    private void createRequest(RbacPrincipal principal, UUID institutionId, String processCode) {
        String activeRole = TenantContext.currentActiveRoleCode();
        if (activeRole == null) {
            throw new UnauthorizedException("No active role: choose an active role before raising an approval request.");
        }
        UUID tenantId = principal.tenantId();
        UUID approvalRequestId = UUID.randomUUID();
        Instant now = Instant.now();
        approvalRequestRepository.save(ApprovalRequest.builder()
                .id(new ApprovalRequestId(tenantId, approvalRequestId))
                .institutionId(institutionId)
                .processCode(processCode)
                .status("PENDING")
                .subjectType("INSTITUTION")
                .subjectId(institutionId)
                .requestedByUserId(principal.userAccountId())
                .requesterActiveRole(activeRole)
                .currentStepNo((short) 1)
                .createdAt(now).createdBy(principal.userAccountId())
                .updatedAt(now).updatedBy(principal.userAccountId())
                .rowVersion(1)
                .build());
        approvalStepRepository.save(ApprovalStep.builder()
                .id(new ApprovalStepId(tenantId, UUID.randomUUID()))
                .approvalRequestId(approvalRequestId)
                .stepNo((short) 1)
                .approverRoleCode("VICE_CHANCELLOR")
                .decision("PENDING")
                .createdAt(now).createdBy(principal.userAccountId())
                .updatedAt(now).updatedBy(principal.userAccountId())
                .rowVersion(1)
                .build());
    }

    private void enforcePlanLimits(UUID tenantId) {
        long currentCount = institutionRepository.countByIdTenantIdAndStatusNot(tenantId, "ARCHIVED");

        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            if (tenant.getPlanId() != null) {
                planRepository.findById(tenant.getPlanId()).ifPresent(plan -> {
                    Integer maxInstitutions = plan.getMaxInstitutions();
                    if (maxInstitutions != null && currentCount >= maxInstitutions) {
                        throw new BusinessRuleViolationException(
                                "PLAN_LIMIT_REACHED",
                                "Plan limit reached: maximum " + maxInstitutions
                                        + " institutions allowed under plan '" + plan.getPlanName() + "' [WF-14]."
                        );
                    }
                });
            }
        });
    }
}
