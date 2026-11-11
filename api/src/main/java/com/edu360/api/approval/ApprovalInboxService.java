package com.edu360.api.approval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.academic.PromotionRunRepository;
import com.edu360.api.approval.dto.ApprovalInboxResponseDto;
import com.edu360.api.approval.dto.ApprovalStepDto;
import com.edu360.api.approval.dto.ApprovalSummaryDto;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.approval.dto.DecideApprovalResponseDto;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.SelfApprovalForbiddenException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.guardian.StudentGuardianLinkRepository;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.LeaveApproverSettingRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.tenant.TenantContext;

/**
 * WHAT: the shared approvals inbox, the request read and the one place a step is decided.
 *
 * HOW (P-01): a decision needs, in order: the request exists in this tenant and in the caller's
 * institution scope (else 404); the request is PENDING; the caller is not the requester (403);
 * the step is the current one and the caller did not act at an earlier step (rule 12); the caller
 * has an active role; the process resolves to an approval action (else deny); the caller is the
 * step's approver by role, named user, delegation or roll-down ({@link ApprovalRouting}); and the
 * evaluator allows the action at the target's scope - for the caller, or for the delegator when
 * acting by delegation. "Acting for" (delegation or roll-down) is written into the audit detail.
 *
 * WHY every repository is required: a missing repository used to skip a rule silently (P-03).
 */
@Service
public class ApprovalInboxService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalInboxService.class);

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final AuditLogService auditLogService;
    private final StudentGuardianLinkRepository studentGuardianLinkRepository;
    private final InstitutionRepository institutionRepository;
    private final PromotionRunRepository promotionRunRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final ApprovalRouting approvalRouting;
    private final LeaveApproverSettingRepository leaveApproverSettingRepository;
    private List<ApprovalOutcomeHandler> outcomeHandlers = List.of();
    private JdbcTemplate jdbcTemplate;

    public ApprovalInboxService(
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalStepRepository approvalStepRepository,
            AuditLogService auditLogService,
            StudentGuardianLinkRepository studentGuardianLinkRepository,
            InstitutionRepository institutionRepository,
            PromotionRunRepository promotionRunRepository,
            PermissionEvaluatorService permissionEvaluatorService,
            ApprovalRouting approvalRouting,
            LeaveApproverSettingRepository leaveApproverSettingRepository) {
        this.approvalRequestRepository = Objects.requireNonNull(approvalRequestRepository);
        this.approvalStepRepository = Objects.requireNonNull(approvalStepRepository);
        this.auditLogService = Objects.requireNonNull(auditLogService);
        this.studentGuardianLinkRepository = Objects.requireNonNull(studentGuardianLinkRepository);
        this.institutionRepository = Objects.requireNonNull(institutionRepository);
        this.promotionRunRepository = Objects.requireNonNull(promotionRunRepository);
        this.permissionEvaluatorService = Objects.requireNonNull(permissionEvaluatorService);
        this.approvalRouting = Objects.requireNonNull(approvalRouting);
        this.leaveApproverSettingRepository = Objects.requireNonNull(leaveApproverSettingRepository);
    }

    @Autowired(required = false)
    public void setOutcomeHandlers(List<ApprovalOutcomeHandler> handlers) {
        this.outcomeHandlers = handlers == null ? List.of() : List.copyOf(handlers);
    }

    @Autowired(required = false)
    public void setDataSource(DataSource dataSource) { this.jdbcTemplate = new JdbcTemplate(dataSource); }

    /** P-05: tenant-wide callers see every institution, others only their own, no context sees nothing. */
    public static boolean inCallerScope(UUID institutionId) {
        String scope = TenantContext.isSet() ? TenantContext.currentInstitutionScope() : null;
        if ("ALL".equalsIgnoreCase(scope)) {
            return true;
        }
        if ("LIST".equalsIgnoreCase(scope)) {
            Set<UUID> ids = TenantContext.currentInstitutionIds();
            return institutionId != null && ids != null && ids.contains(institutionId);
        }
        return false;
    }

    @Transactional(readOnly = true)
    public ApprovalInboxResponseDto listPendingApprovals(UUID tenantId, TenantPrincipal principal) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(principal, "principal is required");

        String activeRole = TenantContext.isSet() ? TenantContext.currentActiveRoleCode() : null;
        UUID userId = principal.userAccountId();
        Map<UUID, ApprovalRequest> requests = new HashMap<>();
        List<ApprovalStepDto> items = new ArrayList<>();

        for (ApprovalStep step : approvalStepRepository.findByIdTenantIdAndDecision(tenantId, "PENDING")) {
            ApprovalRequest req = requests.computeIfAbsent(step.getApprovalRequestId(), id ->
                    approvalRequestRepository.findByIdTenantIdAndIdApprovalRequestId(tenantId, id).orElse(null));
            if (req == null || !"PENDING".equals(req.getStatus()) || step.getStepNo() != req.getCurrentStepNo()) {
                continue;
            }
            if (req.getRequestedByUserId().equals(userId) || !inCallerScope(req.getInstitutionId())) {
                continue;
            }
            Optional<ApprovalRouting.Route> route = approvalRouting.route(tenantId, userId, activeRole, req, step);
            if (route.isEmpty()) {
                continue;
            }
            // P-07: while a delegation is running the items sit with the delegate, and come back
            // to the delegator when it ends or is cancelled.
            if (!route.get().actingFor() && approvalRouting.handedOver(tenantId, userId, req)) {
                continue;
            }
            items.add(toStepDto(step));
        }
        return new ApprovalInboxResponseDto(items, new ApprovalInboxResponseDto.PageInfoDto(items.size(), 1, 1, items.size()));
    }

    @Transactional(readOnly = true)
    public ApprovalSummaryDto getApprovalRequest(UUID tenantId, UUID approvalRequestId, TenantPrincipal principal) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(approvalRequestId, "approvalRequestId is required");

        ApprovalRequest request = approvalRequestRepository
                .findByIdTenantIdAndIdApprovalRequestId(tenantId, approvalRequestId)
                .orElseThrow(() -> notFound(approvalRequestId));
        List<ApprovalStep> steps = approvalStepRepository
                .findByIdTenantIdAndApprovalRequestIdOrderByStepNoAsc(tenantId, approvalRequestId);

        // BS-13: the requester, an approver of one of the steps, or an in-scope holder of the action.
        UUID userId = principal.userAccountId();
        String activeRole = TenantContext.isSet() ? TenantContext.currentActiveRoleCode() : null;
        boolean allowed = request.getRequestedByUserId().equals(userId)
                || steps.stream().anyMatch(s -> approvalRouting.route(tenantId, userId, activeRole, request, s).isPresent());
        if (!allowed && inCallerScope(request.getInstitutionId())) {
            String action = resolveApprovalAction(request);
            if (action != null) {
                RbacPrincipal rbac = new RbacPrincipal(tenantId, userId);
                RbacScope scope = permissionEvaluatorService.targetScope(rbac, request.getInstitutionId(), null);
                allowed = permissionEvaluatorService.can(rbac, action, scope, RbacTarget.of(request.getRequestedByUserId())).allowed();
            }
        }
        if (!allowed) {
            throw notFound(approvalRequestId);
        }
        return toSummaryDto(request, steps);
    }

    /** The one pending request of a process for a subject, if any. */
    @Transactional(readOnly = true)
    public Optional<ApprovalRequest> pendingRequestFor(UUID tenantId, String subjectType, UUID subjectId, String processCode) {
        return approvalRequestRepository
                .findByIdTenantIdAndSubjectTypeAndSubjectIdAndStatus(tenantId, subjectType, subjectId, "PENDING").stream()
                .filter(r -> processCode == null || processCode.equalsIgnoreCase(r.getProcessCode()))
                .findFirst();
    }

    @Transactional
    public DecideApprovalResponseDto decideApproval(UUID tenantId, UUID approvalRequestId,
            DecideApprovalRequestDto requestDto, TenantPrincipal principal) {
        Objects.requireNonNull(principal, "principal is required");
        return decideApproval(tenantId, approvalRequestId, requestDto, principal.userAccountId());
    }

    @Transactional
    public DecideApprovalResponseDto decideApproval(
            UUID tenantId,
            UUID approvalRequestId,
            DecideApprovalRequestDto requestDto,
            UUID userId) {

        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(approvalRequestId, "approvalRequestId is required");
        Objects.requireNonNull(requestDto, "requestDto is required");


        ApprovalRequest request = approvalRequestRepository
                .findByIdTenantIdAndIdApprovalRequestId(tenantId, approvalRequestId)
                .orElseThrow(() -> notFound(approvalRequestId));
        // P-01 test 8: another institution's request looks missing to this caller.
        if (!inCallerScope(request.getInstitutionId())) {
            throw notFound(approvalRequestId);
        }

        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessRuleViolationException("REQUEST_NOT_PENDING",
                    "Approval request is not in PENDING state (status=" + request.getStatus() + ")");
        }

        // Rule 11: self-approval is prohibited except an APPROVED DEC-10 HOD correction for the session the HOD marked.
        String activeRole = TenantContext.isSet() ? TenantContext.currentActiveRoleCode() : null;
        String decision = requestDto.decision().trim().toUpperCase();
        if (request.getRequestedByUserId().equals(userId)
                && !("APPROVED".equals(decision)
                    && isHodSelfApprovalForOwnMarkedSession(tenantId, request, activeRole, userId))) {
            log.warn("Security violation: user {} attempted to decide their own approval request {}", userId, approvalRequestId);
            throw new SelfApprovalForbiddenException("A principal may not approve or decide their own request (Addendum rule 11).");
        }

        // C2 / BS-02: strictly sequential steps
        short stepNo = request.getCurrentStepNo();
        if (requestDto.stepNo() != null && requestDto.stepNo() != stepNo) {
            throw new BusinessRuleViolationException("OUT_OF_SEQUENCE_STEP",
                    "Cannot decide step " + requestDto.stepNo() + " while step " + stepNo + " is pending.");
        }
        ApprovalStep step = approvalStepRepository
                .findByIdTenantIdAndApprovalRequestIdAndStepNo(tenantId, approvalRequestId, stepNo)
                .orElseThrow(() -> new ResourceNotFoundException("Approval step " + stepNo + " not found for request " + approvalRequestId));
        if (!"PENDING".equals(step.getDecision())) {
            throw new BusinessRuleViolationException("STEP_ALREADY_DECIDED",
                    "Step " + stepNo + " has already been decided (decision=" + step.getDecision() + ")");
        }

        // Addendum rule 12: nobody acts at two steps of one request
        boolean actedEarlier = approvalStepRepository
                .findByIdTenantIdAndApprovalRequestIdOrderByStepNoAsc(tenantId, approvalRequestId).stream()
                .filter(s -> s.getStepNo() < stepNo)
                .anyMatch(s -> userId.equals(s.getActedByUserId()));
        if (actedEarlier) {
            throw new UnauthorizedException("Separation of duties: A user cannot act at multiple steps of the same approval request (Addendum rule 12).");
        }

        if ("REJECTED".equals(decision) && (requestDto.remark() == null || requestDto.remark().isBlank())) {
            throw new BusinessRuleViolationException("REJECTION_REMARK_REQUIRED",
                    "A remark is mandatory when rejecting an approval request.");
        }

        // P-01 / P-04: the audit and the step record the real active role, never a fallback.
        if (activeRole == null) {
            throw new UnauthorizedException("No active role: choose an active role before deciding an approval.");
        }
        String action = resolveApprovalAction(request);
        if (action == null) {
            throw new UnauthorizedException("Approval process " + request.getProcessCode() + " cannot be resolved to an action; denied.");
        }
        ApprovalRouting.Route route = approvalRouting.route(tenantId, userId, activeRole, request, step)
                .orElseThrow(() -> new UnauthorizedException(
                        "NOT_CURRENT_APPROVER: your active role is not the approver of step " + stepNo + " ("
                                + step.getApproverRoleCode() + ")."));

        // The action and scope check: the delegator's rights when acting by delegation.
        UUID checked = route.via() == ApprovalRouting.Via.DELEGATION ? route.delegatorUserId() : userId;
        RbacPrincipal rbac = new RbacPrincipal(tenantId, checked);
        permissionEvaluatorService.authorize(rbac, action,
                permissionEvaluatorService.targetScope(rbac, request.getInstitutionId(), null),
                RbacTarget.of(request.getRequestedByUserId()));

        Instant now = Instant.now();
        step.setDecision(decision);
        step.setActedByUserId(userId);
        step.setActedActiveRole(activeRole);
        if (route.via() == ApprovalRouting.Via.DELEGATION) {
            step.setActingViaDelegationId(route.delegationId());
        }
        step.setRemark(requestDto.remark());
        step.setDecidedAt(now);
        step.setUpdatedAt(now);
        step.setUpdatedBy(userId);
        approvalStepRepository.save(step);

        String subjectNewStatus;
        if ("APPROVED".equals(decision)) {
            short nextStepNo = (short) (stepNo + 1);
            boolean hasNext = approvalStepRepository
                    .findByIdTenantIdAndApprovalRequestIdAndStepNo(tenantId, approvalRequestId, nextStepNo).isPresent();
            if (hasNext) {
                request.setCurrentStepNo(nextStepNo);
                subjectNewStatus = "IN_PROGRESS";
            } else {
                request.setStatus("APPROVED");
                request.setFinalDecidedAt(now);
                subjectNewStatus = "APPROVED";
            }
        } else if ("REJECTED".equals(decision)) {
            request.setStatus("REJECTED");
            request.setFinalDecidedAt(now);
            subjectNewStatus = "REJECTED";
        } else {
            subjectNewStatus = "FORWARDED";
        }
        request.setUpdatedAt(now);
        request.setUpdatedBy(userId);
        approvalRequestRepository.save(request);
        if ("APPROVED".equals(subjectNewStatus) || "REJECTED".equals(subjectNewStatus)) {
            applyProcessSideEffect(tenantId, request, "APPROVED".equals(subjectNewStatus), userId);
        }

        // OD-09 / P-09: who acted, in which role and - when it applies - for whom.
        String actingFor = switch (route.via()) {
            case DELEGATION -> " (acting for " + route.actingForRole() + " by delegation " + route.delegationId() + ")";
            case ROLLDOWN -> " (acting for " + route.actingForRole() + " by roll-down, no holder)";
            default -> "";
        };
        if (isAttendanceCorrectionRequest(request) && request.getRequestedByUserId().equals(userId)
                && "APPROVED".equals(decision)) {
            auditLogService.record(new AuditEntry(AuditEventType.ATTENDANCE_CORRECTION_SELF_APPROVED,
                    "ATTENDANCE_CORRECTION_APPROVE", "ATTENDANCE_CORRECTION_REQUEST", request.getSubjectId(),
                    request.getInstitutionId(), Map.of("approval_status", "PENDING"), Map.of("approval_status", "APPROVED"),
                    1, "P", "SUCCESS"));
        } else {
            auditLogService.record(AuditEventType.APPROVAL, null, request.getSubjectId().toString(), null,
                    "Approval request " + approvalRequestId + " step " + stepNo + " decided as " + decision
                            + " by " + activeRole + actingFor);
        }

        List<ApprovalStep> allSteps = approvalStepRepository
                .findByIdTenantIdAndApprovalRequestIdOrderByStepNoAsc(tenantId, approvalRequestId);
        return new DecideApprovalResponseDto(toSummaryDto(request, allSteps), subjectNewStatus, List.of());
    }

    /** DEC-10: only a HOD requester correcting the attendance session they personally marked may self-approve. */
    private boolean isHodSelfApprovalForOwnMarkedSession(UUID tenantId, ApprovalRequest request, String activeRole, UUID userId) {
        if (!"HEAD_OF_DEPARTMENT".equals(activeRole) || jdbcTemplate == null || request.getInstitutionId() == null) return false;
        String process = request.getProcessCode();
        String subject = request.getSubjectType();
        boolean attendanceCorrection =
                "ATTENDANCE_CORRECTION_COLLEGE".equals(process)
                || "ATTENDANCE_CORRECTION_SCHOOL".equals(process)
                || "ATTENDANCE_CORRECTION_REQUEST".equals(subject)
                || "ATTENDANCE_CORRECTION".equals(subject);
        if (!attendanceCorrection) return false;
        try {
            UUID markedBy = jdbcTemplate.queryForObject("""
                    SELECT s.marked_by_user_id
                    FROM app.attendance_correction_request cr
                    JOIN app.attendance_session s
                      ON s.tenant_id = cr.tenant_id
                     AND s.institution_id = cr.institution_id
                     AND s.attendance_session_id = cr.attendance_session_id
                    WHERE cr.tenant_id = ?
                      AND cr.institution_id = ?
                      AND cr.attendance_correction_request_id = ?
                    """, UUID.class, tenantId, request.getInstitutionId(), request.getSubjectId());
            return userId.equals(markedBy);
        } catch (EmptyResultDataAccessException ex) {
            return false;
        }
    }

    /** Which action an approval process needs; null when the process is unknown (deny). */
    public static String resolveApprovalAction(ApprovalRequest request) {
        String subjectType = request.getSubjectType();
        String processCode = request.getProcessCode();
        if ("INSTITUTION".equalsIgnoreCase(subjectType)) {
            if ("INSTITUTION_ACTIVATION".equalsIgnoreCase(processCode) || "ACTIVATION".equalsIgnoreCase(processCode)) {
                return "INSTITUTION_ACTIVATE";
            }
            if ("INSTITUTION_BLOCK".equalsIgnoreCase(processCode)) {
                return "INSTITUTION_BLOCK_APPROVE";
            }
            return null;
        }
        if ("PROMOTION_RUN".equalsIgnoreCase(subjectType) || "PROMOTION".equalsIgnoreCase(processCode)) {
            return "PROMOTION_APPROVE";
        }
        if ("STUDENT_GUARDIAN_LINK".equalsIgnoreCase(subjectType) || "GUARDIAN_CHANGE".equalsIgnoreCase(processCode)) {
            return "GUARDIAN_CHANGE_APPROVE";
        }
        if ("ATTENDANCE_CORRECTION_REQUEST".equalsIgnoreCase(subjectType)
                || "ATTENDANCE_CORRECTION".equalsIgnoreCase(subjectType)
                || (processCode != null && processCode.startsWith("ATTENDANCE_CORRECTION_"))) {
            return "ATTENDANCE_CORRECTION_APPROVE";
        }
        if ("ATTENDANCE_BULK_UPLOAD".equalsIgnoreCase(subjectType)
                || (processCode != null && processCode.startsWith("ATTENDANCE_BULK_UPLOAD_"))) {
            return "ATTENDANCE_BULK_UPLOAD_APPROVE";
        }
        return null;
    }

    private void applyProcessSideEffect(UUID tenantId, ApprovalRequest request, boolean approved, UUID actor) {
        Instant now = Instant.now();
        if ("STUDENT_GUARDIAN_LINK".equals(request.getSubjectType())) {
            studentGuardianLinkRepository.findByIdTenantIdAndIdStudentGuardianLinkId(tenantId, request.getSubjectId())
                    .ifPresent(link -> {
                        if (approved) {
                            link.setStatus("BLOCKED");
                            link.setBlockedAt(now);
                        } else {
                            link.setChangeApprovalRequestId(null);
                            link.setBlockReason(null);
                        }
                        link.setUpdatedAt(now);
                        link.setUpdatedBy(actor);
                        studentGuardianLinkRepository.save(link);
                    });
        } else if ("INSTITUTION".equals(request.getSubjectType())) {
            boolean activation = "INSTITUTION_ACTIVATION".equalsIgnoreCase(request.getProcessCode());
            institutionRepository.findByIdTenantIdAndIdInstitutionId(tenantId, request.getSubjectId()).ifPresent(inst -> {
                if (approved && activation) {
                    // Rule C7: the Principal's-leave approver must be set before an institution goes live.
                    if (!leaveApproverSettingRepository.existsByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(
                            tenantId, "PRINCIPAL", inst.getInstitutionId(), "ACTIVE")) {
                        throw new BusinessRuleViolationException("GO_LIVE_BLOCKED",
                                "Principal's-leave approver not set for institution " + inst.getInstitutionId() + " [C7]");
                    }
                    inst.setStatus("ACTIVE");
                    inst.setActivatedAt(now);
                    inst.setBlockedAt(null);
                } else if (approved) {
                    inst.setStatus("BLOCKED");
                    inst.setBlockedAt(now);
                } else {
                    return;
                }
                inst.setUpdatedAt(now);
                inst.setUpdatedBy(actor);
                inst.setRowVersion(inst.getRowVersion() == null ? 1 : inst.getRowVersion() + 1);
                institutionRepository.save(inst);
                auditLogService.record(activation ? AuditEventType.INSTITUTION_ACTIVATED : AuditEventType.INSTITUTION_BLOCKED,
                        null, actor.toString(), null,
                        "Institution " + inst.getInstitutionName() + " (" + inst.getInstitutionId() + ") "
                                + (activation ? "activated" : "blocked") + " by approval request " + request.getId().getApprovalRequestId());
            });
        } else if ("PROMOTION_RUN".equals(request.getSubjectType())) {
            promotionRunRepository.findByIdTenantIdAndIdPromotionRunId(tenantId, request.getSubjectId()).ifPresent(run -> {
                run.setStatus(approved ? "APPROVED" : "REJECTED");
                run.setUpdatedAt(now);
                run.setUpdatedBy(actor);
                promotionRunRepository.save(run);
            });
        } else if (isAttendanceProcess(request)) {
            ApprovalOutcomeHandler handler = outcomeHandlers.stream().filter(candidate -> candidate.supports(request))
                    .findFirst().orElseThrow(() -> new BusinessRuleViolationException(
                            "APPROVAL_HANDLER_NOT_REGISTERED",
                            "No outcome handler is registered for approval process " + request.getProcessCode() + "."));
            handler.apply(tenantId, request, approved, actor);
        }
    }

    private static boolean isAttendanceCorrectionRequest(ApprovalRequest request) {
        String subject = request.getSubjectType();
        String process = request.getProcessCode();
        return "ATTENDANCE_CORRECTION_REQUEST".equals(subject) || "ATTENDANCE_CORRECTION".equals(subject)
                || "ATTENDANCE_CORRECTION_COLLEGE".equals(process) || "ATTENDANCE_CORRECTION_SCHOOL".equals(process);
    }

    private static boolean isAttendanceProcess(ApprovalRequest request) {
        String subject = request.getSubjectType();
        String process = request.getProcessCode();
        return isAttendanceCorrectionRequest(request)
                || "ATTENDANCE_BULK_UPLOAD".equals(subject)
                || (process != null && process.startsWith("ATTENDANCE_BULK_UPLOAD_"));
    }

    private static ResourceNotFoundException notFound(UUID approvalRequestId) {
        return new ResourceNotFoundException("Approval request " + approvalRequestId + " not found");
    }

    private ApprovalStepDto toStepDto(ApprovalStep step) {
        return new ApprovalStepDto(step.getStepNo(), step.getApproverRoleCode(), step.getDecision(),
                step.getActedByUserId(), step.getActedActiveRole(), step.getRemark(), step.getSkipReason(), step.getDecidedAt());
    }

    private ApprovalSummaryDto toSummaryDto(ApprovalRequest request, List<ApprovalStep> steps) {
        return new ApprovalSummaryDto(
                request.getId().getApprovalRequestId(), request.getProcessCode(), request.getStatus(),
                request.getCurrentStepNo(),
                new ApprovalSummaryDto.SubjectRef(request.getSubjectType(), request.getSubjectId()),
                steps.stream().map(this::toStepDto).toList());
    }
}
