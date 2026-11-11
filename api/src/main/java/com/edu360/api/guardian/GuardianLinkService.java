package com.edu360.api.guardian;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.approval.ApprovalInboxService;
import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.approval.ApprovalRequestId;
import com.edu360.api.approval.ApprovalRequestRepository;
import com.edu360.api.approval.ApprovalStep;
import com.edu360.api.approval.ApprovalStepId;
import com.edu360.api.approval.ApprovalStepRepository;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.guardian.dto.GuardianChangeDecisionRequest;
import com.edu360.api.guardian.dto.GuardianChangeRequestDto;
import com.edu360.api.guardian.dto.GuardianLinkResponse;
import com.edu360.api.guardian.dto.LinkGuardianRequest;
import com.edu360.api.guardian.dto.GuardianChildResponse;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserType;
import com.edu360.api.identity.StudentProfile;
import com.edu360.api.identity.StudentProfileRepository;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.TenantContext;

/**
 * WHAT: guardian links and the guardian change/block approval (stories 19-20, P-02).
 *
 * HOW: the institution is ALWAYS taken from the student record (or from the link's student) -
 * never from a request parameter and never invented. A caller whose active assignment is at
 * another institution gets 404 (foreign records look missing, Part 9 2.1); then the action is
 * checked at the target's institution. A change request opens an approval request whose approver
 * follows the institution type (SCHOOL_PRINCIPAL / PRINCIPAL_COLLEGE); the decision goes through
 * the shared approvals engine, so it needs a pending request, a non-requester, and the step's role.
 *
 * OPEN (question for the Product Owner): D2 has no "view guardian links" action code; the list
 * uses GUARDIAN_LINK, held by the College Admin and the School Admin.
 */
@Service
public class GuardianLinkService {

    private static final Logger log = LoggerFactory.getLogger(GuardianLinkService.class);

    private final StudentGuardianLinkRepository linkRepository;
    private final GuardianProfileRepository profileRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstitutionRepository institutionRepository;
    private final ApprovalInboxService approvalInboxService;
    private final UserAccountRepository userAccountRepository;

    public GuardianLinkService(
            StudentGuardianLinkRepository linkRepository,
            GuardianProfileRepository profileRepository,
            PermissionEvaluatorService permissionEvaluatorService,
            AuditLogService auditLogService,
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalStepRepository approvalStepRepository,
            StudentProfileRepository studentProfileRepository,
            InstitutionRepository institutionRepository,
            ApprovalInboxService approvalInboxService,
            UserAccountRepository userAccountRepository) {
        this.linkRepository = linkRepository;
        this.profileRepository = profileRepository;
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.auditLogService = auditLogService;
        this.approvalRequestRepository = Objects.requireNonNull(approvalRequestRepository);
        this.approvalStepRepository = Objects.requireNonNull(approvalStepRepository);
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository);
        this.institutionRepository = Objects.requireNonNull(institutionRepository);
        this.approvalInboxService = Objects.requireNonNull(approvalInboxService);
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
    }

    @Transactional(readOnly = true)
    public List<GuardianLinkResponse> listGuardianLinks(UUID tenantId, UUID studentProfileId, TenantPrincipal principal) {
        StudentProfile student = student(tenantId, studentProfileId);
        authorizeAtStudent(tenantId, principal, "GUARDIAN_LINK", student);
        return linkRepository.findByIdTenantIdAndStudentProfileId(tenantId, studentProfileId).stream().map(link -> {
            GuardianProfile profile = profileRepository.findByIdTenantIdAndIdGuardianProfileId(
                    tenantId, link.getGuardianProfileId()).orElse(null);
            return GuardianLinkResponse.from(link, profile);
        }).toList();
    }

    /** Return only children connected to the authenticated guardian by an ACTIVE link. */
    @Transactional(readOnly = true)
    public List<GuardianChildResponse> listActiveChildren(UUID tenantId, TenantPrincipal principal) {
        if (principal.userType() != UserType.GUARDIAN) {
            throw new UnauthorizedException("Guardian account required to list linked children.");
        }
        return studentProfileRepository.findActiveChildrenByGuardianUser(tenantId, principal.userAccountId())
                .stream()
                .map(s -> new GuardianChildResponse(s.getStudentProfileId(), s.getInstitutionId(),
                        s.getStudentCode(), s.getStatus()))
                .toList();
    }

    @Transactional
    public GuardianLinkResponse linkGuardian(
            UUID tenantId,
            UUID studentProfileId,
            TenantPrincipal principal,
            LinkGuardianRequest request
    ) {
        StudentProfile student = student(tenantId, studentProfileId);
        authorizeAtStudent(tenantId, principal, "GUARDIAN_LINK", student);

        // Rule WF-21: Up to 3 guardians per student
        long currentCount = linkRepository.countByIdTenantIdAndStudentProfileIdAndStatusNot(tenantId, studentProfileId, "BLOCKED");
        if (currentCount >= 3) {
            throw new BusinessRuleViolationException("MAX_GUARDIANS_EXCEEDED",
                    "A student can have at most 3 guardians (Rule WF-21).");
        }

        // Rule WF-24: Exactly one primary guardian
        List<StudentGuardianLink> existingLinks = linkRepository.findByIdTenantIdAndStudentProfileId(tenantId, studentProfileId);
        boolean hasUsableLink = existingLinks.stream().anyMatch(l -> !"BLOCKED".equals(l.getStatus()));
        boolean shouldBePrimary = request.isPrimary() || !hasUsableLink;

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

        // Parent login is provisioned by the admission/user-creation flow. Never invent a
        // user_account_id: guardian_profile has a composite FK to app.user_account.
        UserAccount guardianAccount = userAccountRepository
                .findByIdTenantIdAndMobileAndUserType(tenantId, request.mobile().trim(), UserType.GUARDIAN)
                .filter(a -> a.getStatus() != UserAccountStatus.DEACTIVATED)
                .orElseThrow(() -> new BusinessRuleViolationException("GUARDIAN_ACCOUNT_NOT_FOUND",
                        "Create the guardian login for this mobile number before linking the student."));

        // Matched by mobile to avoid duplicates (Rule WF-22)
        GuardianProfile profile = profileRepository.findByIdTenantIdAndMobile(tenantId, request.mobile().trim())
                .orElseGet(() -> {
                    UUID newProfileId = UUID.randomUUID();
                    GuardianProfile newProfile = GuardianProfile.builder()
                            .id(new GuardianProfileId(tenantId, newProfileId))
                            .userAccountId(guardianAccount.getId().getUserAccountId())
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
            UUID linkId,
            TenantPrincipal principal,
            GuardianChangeRequestDto request
    ) {
        StudentGuardianLink link = link(tenantId, linkId);
        StudentProfile student = student(tenantId, link.getStudentProfileId());
        UUID institutionId = student.getInstitutionId();
        authorizeAtStudent(tenantId, principal, "GUARDIAN_CHANGE_REQUEST", student);

        // Rule WF-23: Reason required
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessRuleViolationException("REASON_REQUIRED",
                    "Reason is required to request guardian change (Rule WF-23).");
        }
        String activeRole = TenantContext.currentActiveRoleCode();
        if (activeRole == null) {
            throw new UnauthorizedException("No active role: choose an active role before raising a guardian change.");
        }
        if (approvalInboxService.pendingRequestFor(tenantId, "STUDENT_GUARDIAN_LINK", linkId, "GUARDIAN_CHANGE").isPresent()) {
            throw new DuplicateResourceException("A change request for guardian link " + linkId + " is already pending.");
        }

        // The approver follows the institution type: School Principal for a school, Principal for a college.
        String approverRole = institutionId != null && institutionRepository
                .findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                .map(i -> "SCHOOL".equalsIgnoreCase(i.getInstitutionType())).orElse(false)
                ? "SCHOOL_PRINCIPAL" : "PRINCIPAL_COLLEGE";

        UUID approvalRequestId = UUID.randomUUID();
        Instant now = Instant.now();
        link.setChangeApprovalRequestId(approvalRequestId);
        link.setBlockReason(request.reason());
        link.setUpdatedAt(now);
        link.setUpdatedBy(principal.userAccountId());
        link.setRowVersion(link.getRowVersion() != null ? link.getRowVersion() + 1 : 1);
        StudentGuardianLink saved = linkRepository.save(link);

        approvalRequestRepository.save(ApprovalRequest.builder()
                .id(new ApprovalRequestId(tenantId, approvalRequestId))
                .institutionId(institutionId)
                .processCode("GUARDIAN_CHANGE")
                .status("PENDING")
                .subjectType("STUDENT_GUARDIAN_LINK")
                .subjectId(linkId)
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
                .approverRoleCode(approverRole)
                .decision("PENDING")
                .createdAt(now).createdBy(principal.userAccountId())
                .updatedAt(now).updatedBy(principal.userAccountId())
                .rowVersion(1)
                .build());

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

    /**
     * Decides the pending change. P-02: needs an existing pending request (422), the decider is
     * not the requester (403), and the decision is the approval step itself, so it also needs the
     * step's approver role. Approval blocks the link (inbox side effect), rejection clears the request.
     */
    @Transactional
    public GuardianLinkResponse decideChange(
            UUID tenantId,
            UUID linkId,
            TenantPrincipal principal,
            GuardianChangeDecisionRequest request
    ) {
        StudentGuardianLink link = link(tenantId, linkId);
        StudentProfile student = student(tenantId, link.getStudentProfileId());
        authorizeAtStudent(tenantId, principal, "GUARDIAN_CHANGE_APPROVE", student);

        ApprovalRequest pending = approvalInboxService
                .pendingRequestFor(tenantId, "STUDENT_GUARDIAN_LINK", linkId, "GUARDIAN_CHANGE")
                .orElseThrow(() -> new BusinessRuleViolationException("NO_PENDING_REQUEST",
                        "Guardian link " + linkId + " has no pending change request to decide."));

        approvalInboxService.decideApproval(tenantId, pending.getId().getApprovalRequestId(),
                new DecideApprovalRequestDto(pending.getCurrentStepNo(), request.approved() ? "APPROVED" : "REJECTED",
                        request.decisionNotes(), null),
                principal.userAccountId());

        StudentGuardianLink saved = link(tenantId, linkId);
        GuardianProfile profile = profileRepository.findByIdTenantIdAndIdGuardianProfileId(
                tenantId, saved.getGuardianProfileId()).orElse(null);

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

    // ------------------------------------------------------------------------------------------

    private StudentProfile student(UUID tenantId, UUID studentProfileId) {
        return studentProfileRepository.findById(studentProfileId)
                .filter(s -> tenantId.equals(s.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Student " + studentProfileId + " not found"));
    }

    private StudentGuardianLink link(UUID tenantId, UUID linkId) {
        return linkRepository.findByIdTenantIdAndIdStudentGuardianLinkId(tenantId, linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Guardian link not found: " + linkId));
    }

    /**
     * 404 when the caller's own active assignment is at another institution (the record looks
     * missing), then the action at the student's institution (A-05).
     */
    private void authorizeAtStudent(UUID tenantId, TenantPrincipal principal, String actionCode, StudentProfile student) {
        RbacPrincipal rbac = new RbacPrincipal(tenantId, principal.userAccountId());
        UUID institutionId = student.getInstitutionId();
        boolean elsewhere = permissionEvaluatorService.activeAssignment(rbac)
                .map(a -> a.getScopeType() != ScopeType.TENANT && !Objects.equals(a.getInstitutionId(), institutionId))
                .orElse(false);
        if (elsewhere) {
            throw new ResourceNotFoundException("Student " + student.getStudentProfileId() + " not found");
        }
        RbacScope scope = permissionEvaluatorService.targetScope(rbac, institutionId, null);
        permissionEvaluatorService.authorize(rbac, actionCode, scope, RbacTarget.NONE);
    }
}
