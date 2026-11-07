package com.edu360.api.institution;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.approval.ApprovalRequest;
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

    private static final Logger log = LoggerFactory.getLogger(InstitutionService.class);

    private final InstitutionRepository institutionRepository;
    private final LeaveApproverSettingRepository leaveApproverSettingRepository;
    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalStepRepository approvalStepRepository;

    @Autowired
    public InstitutionService(
            InstitutionRepository institutionRepository,
            LeaveApproverSettingRepository leaveApproverSettingRepository,
            TenantRepository tenantRepository,
            PlanRepository planRepository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService,
            @Autowired(required = false) ApprovalRequestRepository approvalRequestRepository,
            @Autowired(required = false) ApprovalStepRepository approvalStepRepository) {
        this.institutionRepository = institutionRepository;
        this.leaveApproverSettingRepository = leaveApproverSettingRepository;
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.auditLogService = auditLogService;
        this.approvalRequestRepository = approvalRequestRepository;
        this.approvalStepRepository = approvalStepRepository;
    }

    public InstitutionService(
            InstitutionRepository institutionRepository,
            LeaveApproverSettingRepository leaveApproverSettingRepository,
            TenantRepository tenantRepository,
            PlanRepository planRepository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService) {
        this(institutionRepository, leaveApproverSettingRepository, tenantRepository, planRepository,
                permissionEvaluatorService, auditLogService, null, null);
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
     * Activates a DRAFT institution (INSTITUTION_ACTIVATE).
     * Enforces:
     * 1. Authorization: Vice Chancellor (or authorized role) at tenant/university scope.
     * 2. Rule C7 / fn_institution_activation_gate: Refused until Principal's-leave approver
     *    is set in app.leave_approver_setting (status = 'ACTIVE').
     * 3. Transitions status from DRAFT -> ACTIVE, stamps activated_at.
     */
    @Transactional
    public InstitutionResponse activateInstitution(RbacPrincipal principal, UUID institutionId) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(institutionId, "institutionId is required");

        // 1. Authorize action
        permissionEvaluatorService.authorize(
                principal,
                "INSTITUTION_ACTIVATE",
                RbacScope.tenant(),
                RbacTarget.NONE
        );

        UUID tenantId = principal.tenantId();
        Institution institution = institutionRepository.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution " + institutionId + " was not found."));

        if ("ACTIVE".equalsIgnoreCase(institution.getStatus())) {
            return InstitutionResponse.fromEntity(institution);
        }

        // 2. Enforce Rule C7: Principal's-leave approver must be configured and active
        boolean hasPrincipalLeaveApprover = leaveApproverSettingRepository
                .existsByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(
                        tenantId, "PRINCIPAL", institutionId, "ACTIVE"
                );

        if (!hasPrincipalLeaveApprover) {
            log.warn("Activation refused for institution {} in tenant {}: missing Principal's-leave approver [C7]",
                    institutionId, tenantId);
            throw new BusinessRuleViolationException(
                    "GO_LIVE_BLOCKED",
                    "Principal's-leave approver not set for institution " + institutionId + " [C7]"
            );
        }

        // 3. Transition to ACTIVE
        institution.setStatus("ACTIVE");
        institution.setActivatedAt(Instant.now());
        institution.setUpdatedAt(Instant.now());
        institution.setUpdatedBy(principal.userAccountId());
        institution.setRowVersion(institution.getRowVersion() + 1);

        Institution saved = institutionRepository.save(institution);
        log.info("Activated institution '{}' ({}) in tenant {}", institution.getInstitutionName(), institutionId, tenantId);

        auditLogService.record(
                AuditEventType.INSTITUTION_ACTIVATED,
                null,
                principal.userAccountId().toString(),
                null,
                "Activated institution " + institution.getInstitutionName() + " (" + institutionId + ") in tenant " + tenantId
        );

        return InstitutionResponse.fromEntity(saved);
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
     * Enforces:
     * 1. Authorization: University Admin at tenant/university scope.
     * 2. Institution must exist and currently be ACTIVE.
     * 3. Reason for block is required.
     */
    @Transactional
    public InstitutionBlockResponse requestBlock(
            RbacPrincipal principal,
            UUID institutionId,
            BlockInstitutionRequest request) {

        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(institutionId, "institutionId is required");
        Objects.requireNonNull(request, "request is required");

        permissionEvaluatorService.authorize(
                principal,
                "INSTITUTION_BLOCK_REQUEST",
                RbacScope.tenant(),
                RbacTarget.of(institutionId)
        );

        UUID tenantId = principal.tenantId();
        Institution institution = institutionRepository.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution " + institutionId + " was not found."));

        if (!"ACTIVE".equalsIgnoreCase(institution.getStatus())) {
            throw new BusinessRuleViolationException(
                    "INVALID_INSTITUTION_STATUS",
                    "Only active institutions can be submitted for blocking. Current status is " + institution.getStatus() + "."
            );
        }

        if (approvalRequestRepository != null && approvalStepRepository != null) {
            UUID approvalRequestId = UUID.randomUUID();
            String activeRole = TenantContext.currentActiveRoleCode() != null ? TenantContext.currentActiveRoleCode() : "UNIVERSITY_ADMIN";
            ApprovalRequest approvalReq = ApprovalRequest.builder()
                    .id(new ApprovalRequestId(tenantId, approvalRequestId))
                    .institutionId(institutionId)
                    .processCode("INSTITUTION_BLOCK")
                    .status("PENDING")
                    .subjectType("INSTITUTION")
                    .subjectId(institutionId)
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
                    .approverRoleCode("VICE_CHANCELLOR")
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
     * Approves or rejects an institution block request (INSTITUTION_BLOCK_APPROVE / Rule WF-15).
     * Enforces:
     * 1. Authorization: Vice Chancellor at tenant/university scope.
     * 2. When approved: promotes status to BLOCKED, stamps blocked_at, logs executive audit.
     * 3. When rejected: keeps institution ACTIVE with decision notes.
     */
    @Transactional
    public InstitutionBlockResponse decideBlock(
            RbacPrincipal principal,
            UUID institutionId,
            BlockDecisionRequest decisionRequest) {

        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(institutionId, "institutionId is required");
        Objects.requireNonNull(decisionRequest, "decisionRequest is required");

        permissionEvaluatorService.authorize(
                principal,
                "INSTITUTION_BLOCK_APPROVE",
                RbacScope.tenant(),
                RbacTarget.of(institutionId)
        );

        UUID tenantId = principal.tenantId();
        Institution institution = institutionRepository.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution " + institutionId + " was not found."));

        if (Boolean.TRUE.equals(decisionRequest.approved())) {
            if ("BLOCKED".equalsIgnoreCase(institution.getStatus())) {
                return new InstitutionBlockResponse(
                        institutionId,
                        institution.getInstitutionCode(),
                        institution.getInstitutionName(),
                        "BLOCKED",
                        institution.getBlockedAt(),
                        "Institution is already blocked."
                );
            }

            Instant now = Instant.now();
            institution.setStatus("BLOCKED");
            institution.setBlockedAt(now);
            institution.setUpdatedAt(now);
            institution.setUpdatedBy(principal.userAccountId());
            institution.setRowVersion(institution.getRowVersion() + 1);

            institutionRepository.save(institution);

            auditLogService.record(
                    AuditEventType.INSTITUTION_BLOCKED,
                    null,
                    principal.userAccountId().toString(),
                    null,
                    "Institution " + institution.getInstitutionName() + " (" + institutionId + ") blocked by Vice Chancellor. Notes: " + decisionRequest.decisionNotes()
            );

            log.info("Institution '{}' ({}) BLOCKED by VC in tenant {}",
                    institution.getInstitutionName(), institutionId, tenantId);

            return new InstitutionBlockResponse(
                    institutionId,
                    institution.getInstitutionCode(),
                    institution.getInstitutionName(),
                    "BLOCKED",
                    now,
                    "Institution blocked successfully by executive approval."
            );
        } else {
            auditLogService.record(
                    AuditEventType.INSTITUTION_BLOCK_REJECTED,
                    null,
                    principal.userAccountId().toString(),
                    null,
                    "Block request for institution " + institution.getInstitutionName() + " (" + institutionId + ") was rejected by Vice Chancellor. Notes: " + decisionRequest.decisionNotes()
            );

            log.info("Institution '{}' ({}) block request REJECTED by VC in tenant {}",
                    institution.getInstitutionName(), institutionId, tenantId);

            return new InstitutionBlockResponse(
                    institutionId,
                    institution.getInstitutionCode(),
                    institution.getInstitutionName(),
                    institution.getStatus(),
                    institution.getBlockedAt(),
                    "Institution block request was rejected by Vice Chancellor: " +
                            (decisionRequest.decisionNotes() != null ? decisionRequest.decisionNotes() : "No notes provided.")
            );
        }
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
