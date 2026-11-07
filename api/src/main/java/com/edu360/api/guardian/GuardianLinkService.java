package com.edu360.api.guardian;

import java.time.Instant;
import java.util.List;
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
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.guardian.dto.GuardianChangeDecisionRequest;
import com.edu360.api.guardian.dto.GuardianChangeRequestDto;
import com.edu360.api.guardian.dto.GuardianLinkResponse;
import com.edu360.api.guardian.dto.LinkGuardianRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import lombok.RequiredArgsConstructor;

@Service
public class GuardianLinkService {

    private static final Logger log = LoggerFactory.getLogger(GuardianLinkService.class);

    private final StudentGuardianLinkRepository linkRepository;
    private final GuardianProfileRepository profileRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalStepRepository approvalStepRepository;

    @Autowired
    public GuardianLinkService(
            StudentGuardianLinkRepository linkRepository,
            GuardianProfileRepository profileRepository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService,
            @Autowired(required = false) ApprovalRequestRepository approvalRequestRepository,
            @Autowired(required = false) ApprovalStepRepository approvalStepRepository) {
        this.linkRepository = linkRepository;
        this.profileRepository = profileRepository;
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.auditLogService = auditLogService;
        this.approvalRequestRepository = approvalRequestRepository;
        this.approvalStepRepository = approvalStepRepository;
    }

    public GuardianLinkService(
            StudentGuardianLinkRepository linkRepository,
            GuardianProfileRepository profileRepository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService) {
        this(linkRepository, profileRepository, permissionEvaluatorService, auditLogService, null, null);
    }

    @Transactional(readOnly = true)
    public List<GuardianLinkResponse> listGuardianLinks(UUID tenantId, UUID studentProfileId) {
        List<StudentGuardianLink> links = linkRepository.findByIdTenantIdAndStudentProfileId(tenantId, studentProfileId);
        return links.stream().map(link -> {
            GuardianProfile profile = profileRepository.findByIdTenantIdAndIdGuardianProfileId(
                    tenantId, link.getGuardianProfileId()).orElse(null);
            return GuardianLinkResponse.from(link, profile);
        }).toList();
    }

    @Transactional
    public GuardianLinkResponse linkGuardian(
            UUID tenantId,
            UUID institutionId,
            UUID studentProfileId,
            TenantPrincipal principal,
            LinkGuardianRequest request
    ) {
        // Fail-closed authorization check: GUARDIAN_LINK on own institution
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "GUARDIAN_LINK",
                RbacScope.institution(institutionId),
                RbacTarget.NONE
        );

        // Rule WF-21: Up to 3 guardians per student
        long currentCount = linkRepository.countByIdTenantIdAndStudentProfileId(tenantId, studentProfileId);
        if (currentCount >= 3) {
            throw new BusinessRuleViolationException("MAX_GUARDIANS_EXCEEDED",
                    "A student can have at most 3 guardians (Rule WF-21).");
        }

        // Rule WF-24: Exactly one primary guardian
        List<StudentGuardianLink> existingLinks = linkRepository.findByIdTenantIdAndStudentProfileId(tenantId, studentProfileId);
        boolean shouldBePrimary = request.isPrimary() || existingLinks.isEmpty();

        if (shouldBePrimary) {
            for (StudentGuardianLink existing : existingLinks) {
                if (Boolean.TRUE.equals(existing.getIsPrimary())) {
                    existing.setIsPrimary(false);
                    existing.setUpdatedAt(Instant.now());
                    existing.setUpdatedBy(principal.userAccountId());
                    linkRepository.save(existing);
                }
            }
        }

        // Matched by mobile to avoid duplicates (Rule WF-22)
        GuardianProfile profile = profileRepository.findByIdTenantIdAndMobile(tenantId, request.mobile())
                .orElseGet(() -> {
                    UUID newProfileId = UUID.randomUUID();
                    GuardianProfile newProfile = GuardianProfile.builder()
                            .id(new GuardianProfileId(tenantId, newProfileId))
                            .userAccountId(UUID.randomUUID())
                            .fullName(request.fullName())
                            .mobile(request.mobile())
                            .email(request.email())
                            .createdBy(principal.userAccountId())
                            .updatedBy(principal.userAccountId())
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .rowVersion(1)
                            .build();
                    return profileRepository.save(newProfile);
                });

        UUID linkId = UUID.randomUUID();
        StudentGuardianLink link = StudentGuardianLink.builder()
                .id(new StudentGuardianLinkId(tenantId, linkId))
                .studentProfileId(studentProfileId)
                .guardianProfileId(profile.getGuardianProfileId())
                .relationship(request.relationship())
                .isPrimary(shouldBePrimary)
                .status("PENDING_ACTIVATION")
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .rowVersion(1)
                .build();

        StudentGuardianLink saved = linkRepository.save(link);

        auditLogService.record(
                AuditEventType.GUARDIAN_LINKED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Linked guardian " + profile.getGuardianProfileId() + " to student " + studentProfileId
        );

        log.info("StudentGuardianLink {} created for student {} in tenant {}", linkId, studentProfileId, tenantId);

        return GuardianLinkResponse.from(saved, profile);
    }

    @Transactional
    public GuardianLinkResponse requestChange(
            UUID tenantId,
            UUID institutionId,
            UUID linkId,
            TenantPrincipal principal,
            GuardianChangeRequestDto request
    ) {
        // Fail-closed authorization check: GUARDIAN_CHANGE_REQUEST on own institution
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "GUARDIAN_CHANGE_REQUEST",
                RbacScope.institution(institutionId),
                RbacTarget.NONE
        );

        // Rule WF-23: Reason required
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessRuleViolationException("REASON_REQUIRED",
                    "Reason is required to request guardian change (Rule WF-23).");
        }

        StudentGuardianLink link = linkRepository.findByIdTenantIdAndIdStudentGuardianLinkId(tenantId, linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Guardian link not found: " + linkId));

        UUID approvalRequestId = UUID.randomUUID();
        link.setChangeApprovalRequestId(approvalRequestId);
        link.setBlockReason(request.reason());
        link.setUpdatedAt(Instant.now());
        link.setUpdatedBy(principal.userAccountId());
        link.setRowVersion(link.getRowVersion() != null ? link.getRowVersion() + 1 : 1);

        StudentGuardianLink saved = linkRepository.save(link);

        if (approvalRequestRepository != null && approvalStepRepository != null) {
            String activeRole = TenantContext.currentActiveRoleCode() != null ? TenantContext.currentActiveRoleCode() : "STAFF";
            ApprovalRequest approvalReq = ApprovalRequest.builder()
                    .id(new ApprovalRequestId(tenantId, approvalRequestId))
                    .institutionId(institutionId)
                    .processCode("GUARDIAN_CHANGE")
                    .status("PENDING")
                    .subjectType("STUDENT_GUARDIAN_LINK")
                    .subjectId(linkId)
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
                    .approverRoleCode("PRINCIPAL_COLLEGE")
                    .decision("PENDING")
                    .createdAt(Instant.now())
                    .createdBy(principal.userAccountId())
                    .updatedAt(Instant.now())
                    .updatedBy(principal.userAccountId())
                    .rowVersion(1)
                    .build();
            approvalStepRepository.save(step);
        }

        GuardianProfile profile = profileRepository.findByIdTenantIdAndIdGuardianProfileId(
                tenantId, link.getGuardianProfileId()).orElse(null);

        auditLogService.record(
                AuditEventType.GUARDIAN_CHANGE_REQUESTED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Requested guardian change for link " + linkId + " reason: " + request.reason()
        );

        log.info("Guardian change requested for link {} by {} in tenant {}", linkId, principal.userAccountId(), tenantId);

        return GuardianLinkResponse.from(saved, profile);
    }

    @Transactional
    public GuardianLinkResponse decideChange(
            UUID tenantId,
            UUID institutionId,
            UUID linkId,
            TenantPrincipal principal,
            GuardianChangeDecisionRequest request
    ) {
        // Fail-closed authorization check: GUARDIAN_CHANGE_APPROVE on own institution
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "GUARDIAN_CHANGE_APPROVE",
                RbacScope.institution(institutionId),
                RbacTarget.NONE
        );

        StudentGuardianLink link = linkRepository.findByIdTenantIdAndIdStudentGuardianLinkId(tenantId, linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Guardian link not found: " + linkId));

        if (approvalRequestRepository != null && link.getChangeApprovalRequestId() != null) {
            approvalRequestRepository.findByIdTenantIdAndIdApprovalRequestId(tenantId, link.getChangeApprovalRequestId())
                    .ifPresent(req -> {
                        req.setStatus(request.approved() ? "APPROVED" : "REJECTED");
                        req.setFinalDecidedAt(Instant.now());
                        req.setUpdatedAt(Instant.now());
                        req.setUpdatedBy(principal.userAccountId());
                        approvalRequestRepository.save(req);

                        if (approvalStepRepository != null) {
                            approvalStepRepository.findByIdTenantIdAndApprovalRequestIdAndStepNo(
                                    tenantId, req.getId().getApprovalRequestId(), req.getCurrentStepNo())
                                    .ifPresent(st -> {
                                        st.setDecision(request.approved() ? "APPROVED" : "REJECTED");
                                        st.setActedByUserId(principal.userAccountId());
                                        st.setDecidedAt(Instant.now());
                                        st.setRemark(request.decisionNotes());
                                        approvalStepRepository.save(st);
                                    });
                        }
                    });
        }

        if (request.approved()) {
            // Rule WF-23, WF-25: Old guardian loses access at once; blocked
            link.setStatus("BLOCKED");
            link.setBlockedAt(Instant.now());
        } else {
            // Rejected
            link.setChangeApprovalRequestId(null);
            link.setBlockReason(null);
        }

        link.setUpdatedAt(Instant.now());
        link.setUpdatedBy(principal.userAccountId());
        link.setRowVersion(link.getRowVersion() != null ? link.getRowVersion() + 1 : 1);

        StudentGuardianLink saved = linkRepository.save(link);

        GuardianProfile profile = profileRepository.findByIdTenantIdAndIdGuardianProfileId(
                tenantId, link.getGuardianProfileId()).orElse(null);

        auditLogService.record(
                AuditEventType.GUARDIAN_CHANGE_APPROVED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Guardian change decision for link " + linkId + ": " + (request.approved() ? "APPROVED" : "REJECTED")
        );

        log.info("Guardian change decision {} for link {} by {} in tenant {}",
                request.approved() ? "APPROVED" : "REJECTED", linkId, principal.userAccountId(), tenantId);

        return GuardianLinkResponse.from(saved, profile);
    }
}
