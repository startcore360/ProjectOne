package com.edu360.api.attendance.staffcheckin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.approval.ApprovalInboxService;
import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.approval.ApprovalRequestId;
import com.edu360.api.approval.ApprovalRequestRepository;
import com.edu360.api.approval.ApprovalRouting;
import com.edu360.api.approval.ApprovalStep;
import com.edu360.api.approval.ApprovalStepId;
import com.edu360.api.approval.ApprovalStepRepository;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
import com.edu360.api.attendance.staffcheckin.dto.CheckInInboxItemResponse;
import com.edu360.api.attendance.staffcheckin.dto.CheckInStatusResponse;
import com.edu360.api.attendance.staffcheckin.dto.CheckInSubmitRequest;
import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.files.FileObject;
import com.edu360.api.files.FileObjectId;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.files.FilePurpose;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.StaffAssignment;
import com.edu360.api.institution.StaffAssignmentRepository;
import com.edu360.api.institution.StaffProfile;
import com.edu360.api.institution.StaffProfileRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.tenant.TenantContext;

/**
 * WHAT: staff self-attendance check-in (fresh selfie + basic liveness + strict geofencing) and
 * its provisional review - sub-task 2 of the V4.1 feature (sql/76_v41_staff_attendance_checkin.sql
 * is sub-task 1). There is NO face matching and NO identity verification: liveness only scores
 * whether the frames look like a live camera capture rather than a still image.
 *
 * GEOFENCE / ACCURACY: hard, never-bypassable blocks. GPS accuracy worse than the institution's
 * threshold, or a position outside its radius, refuses the submission outright; neither is ever
 * routed to PROVISIONAL. Each refusal is audited as DENIED_ATTEMPT in its own transaction
 * (StaffCheckinDeniedAttemptAuditor) so the audit survives the rollback.
 *
 * ONE PER DAY: one check-in per staff member per institution-local day (attendance_date, from
 * captured_at in the location's timezone). Any existing row for that date blocks another (409);
 * a hard-blocked attempt writes no row, so it does not use up the day.
 *
 * LIVENESS: a doubtful score never rejects or discards the capture. It is stored as PROVISIONAL
 * and raises one request in the shared approval engine (app.approval_request / app.approval_step,
 * process STAFF_ATTENDANCE_REVIEW, subject STAFF_ATTENDANCE_CHECKIN). When no reviewer can be
 * resolved the request is raised BLOCKED (D-002) and the capture is still kept.
 *
 * REVIEW: approve/reject here are thin wrappers over ApprovalInboxService.decideApproval - the one
 * place a step is decided (scope, self-approval, routing, delegation, roll-down, separation of
 * duties, the STAFF_ATTENDANCE_CHECKIN_REVIEW action). The engine then calls
 * StaffCheckinApprovalOutcomeHandler, which moves the check-in to PRESENT / REJECTED and audits it.
 *
 * DAY END: expireProvisional() flips an unreviewed PROVISIONAL check-in to CHECK_IN_ONLY, skips
 * the unreviewed step and escalates the same request to the next higher authority, so a
 * CHECK_IN_ONLY record can still be resolved later through the engine, audited like any decision.
 *
 * CAPTURE COLUMNS ARE NEVER REWRITTEN: they are mapped updatable=false and guarded by
 * app.fn_checkin_capture_guard.
 */
@Service
public class StaffCheckInService {

    public static final String PROCESS_CODE = "STAFF_ATTENDANCE_REVIEW";
    public static final String SUBJECT_TYPE = "STAFF_ATTENDANCE_CHECKIN";
    public static final String SUBMIT_ACTION = "STAFF_ATTENDANCE_CHECKIN_SUBMIT";
    public static final String REVIEW_ACTION = "STAFF_ATTENDANCE_CHECKIN_REVIEW";

    public static final String STATUS_PRESENT = "PRESENT";
    public static final String STATUS_PROVISIONAL = "PROVISIONAL";
    public static final String STATUS_CHECK_IN_ONLY = "CHECK_IN_ONLY";
    public static final String STATUS_REJECTED = "REJECTED";
    /** Statuses a reviewer may still decide: PROVISIONAL same day, CHECK_IN_ONLY after escalation. */
    public static final Set<String> REVIEWABLE_STATUSES = Set.of(STATUS_PROVISIONAL, STATUS_CHECK_IN_ONLY);

    /** Liveness confidence at/above this is PRESENT; below it the capture goes PROVISIONAL. */
    static final double LIVENESS_CONFIDENCE_THRESHOLD = 0.55;
    /** The selfie must have been uploaded this recently ("fresh selfie"). */
    static final Duration PHOTO_FRESHNESS = Duration.ofMinutes(10);
    static final int MAX_LIVENESS_FRAMES = 5;
    static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;
    static final int MAX_DEVICE_TYPE_LENGTH = 30;
    static final int MAX_REMARK_LENGTH = 1000;
    private static final Set<String> SELFIE_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    static final String ONE_PER_DAY_INDEX = "ux_staff_attendance_checkin_staff_day";
    static final String ALREADY_CHECKED_IN_MESSAGE = "You have already checked in today.";

    private final PermissionEvaluatorService permissions;
    private final LivenessVerifier livenessVerifier;
    private final StaffProfileRepository staffProfiles;
    private final StaffAssignmentRepository staffAssignments;
    private final InstitutionRepository institutions;
    private final InstitutionAttendanceLocationRepository locations;
    private final StaffAttendanceCheckinRepository checkins;
    private final FileObjectRepository files;
    private final ApprovalRequestRepository approvalRequests;
    private final ApprovalStepRepository approvalSteps;
    private final ApprovalInboxService approvalInbox;
    private final ApprovalRouting approvalRouting;
    private final StaffCheckinApproverResolver approverResolver;
    private final AuditLogService audit;
    private final StaffCheckinDeniedAttemptAuditor deniedAttempts;

    public StaffCheckInService(PermissionEvaluatorService permissions, LivenessVerifier livenessVerifier,
            StaffProfileRepository staffProfiles, StaffAssignmentRepository staffAssignments,
            InstitutionRepository institutions, InstitutionAttendanceLocationRepository locations,
            StaffAttendanceCheckinRepository checkins, FileObjectRepository files,
            ApprovalRequestRepository approvalRequests, ApprovalStepRepository approvalSteps,
            ApprovalInboxService approvalInbox, ApprovalRouting approvalRouting,
            StaffCheckinApproverResolver approverResolver, AuditLogService audit,
            StaffCheckinDeniedAttemptAuditor deniedAttempts) {
        this.deniedAttempts = deniedAttempts;
        this.permissions = permissions;
        this.livenessVerifier = livenessVerifier;
        this.staffProfiles = staffProfiles;
        this.staffAssignments = staffAssignments;
        this.institutions = institutions;
        this.locations = locations;
        this.checkins = checkins;
        this.files = files;
        this.approvalRequests = approvalRequests;
        this.approvalSteps = approvalSteps;
        this.approvalInbox = approvalInbox;
        this.approvalRouting = approvalRouting;
        this.approverResolver = approverResolver;
        this.audit = audit;
    }

    // ------------------------------------------------------------------------------- submit

    @Transactional
    public CheckInStatusResponse submit(UUID tenantId, TenantPrincipal principal, CheckInSubmitRequest request) {
        validate(request);
        UUID userId = principal.userAccountId();
        RbacPrincipal actor = new RbacPrincipal(tenantId, userId);
        permissions.authorize(actor, SUBMIT_ACTION, permissions.ownScope(actor), RbacTarget.NONE);
        String activeRole = TenantContext.currentActiveRoleCode();
        if (activeRole == null || activeRole.isBlank()) {
            throw new BusinessRuleViolationException("ACTIVE_ROLE_REQUIRED", "Choose an active role before checking in.");
        }

        StaffProfile staff = staffProfiles.findByIdTenantIdAndUserAccountId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff profile not found"));
        StaffAssignment assignment = activeAssignment(tenantId, staff.getId().getStaffProfileId());
        UUID institutionId = assignment.getInstitutionId();
        if (institutionId == null) {
            throw new BusinessRuleViolationException("STAFF_INSTITUTION_REQUIRED",
                    "A staff institution assignment is required to check in.");
        }
        InstitutionAttendanceLocation location = locations.findByTenantIdAndInstitutionId(tenantId, institutionId)
                .orElseThrow(() -> new BusinessRuleViolationException("CHECKIN_LOCATION_NOT_CONFIGURED",
                        "This institution has no self-check-in geofence configured yet."));

        // One check-in per staff member per institution-local day, whatever the earlier one's
        // status (REJECTED included). Hard-blocked attempts below never write a row, so they do
        // not use up the day. ux_staff_attendance_checkin_staff_day is the hard guard (race below).
        Instant now = Instant.now();
        LocalDate attendanceDate = LocalDate.ofInstant(now, StaffCheckinExpiryScheduler.attendanceZone(location.getTimezone()));
        if (checkins.existsByTenantIdAndStaffUserIdAndAttendanceDate(tenantId, userId, attendanceDate)) {
            throw new DuplicateResourceException(ALREADY_CHECKED_IN_MESSAGE);
        }

        // Hard, never-bypassable blocks: checked before anything is written. Each refusal is
        // audited as a DENIED_ATTEMPT in its own transaction, so it survives this rollback.
        if (request.gpsAccuracyMeters().compareTo(BigDecimal.valueOf(location.getGpsAccuracyThresholdMeters())) > 0) {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("gps_accuracy_m", request.gpsAccuracyMeters().setScale(0, RoundingMode.HALF_UP).longValue());
            facts.put("gps_accuracy_threshold_m", location.getGpsAccuracyThresholdMeters());
            deniedAttempts.recordHardBlock(userId, institutionId, "GPS_ACCURACY_TOO_LOW", facts);
            throw new BusinessRuleViolationException("GPS_ACCURACY_TOO_LOW",
                    "Reported GPS accuracy is worse than this institution's threshold of "
                            + location.getGpsAccuracyThresholdMeters() + " meters. Move to open sky and try again.");
        }
        BigDecimal distanceMeters = haversineMeters(location.getLatitude(), location.getLongitude(),
                request.latitude(), request.longitude());
        if (distanceMeters.compareTo(BigDecimal.valueOf(location.getRadiusMeters())) > 0) {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("distance_m", distanceMeters.setScale(0, RoundingMode.HALF_UP).longValue());
            facts.put("radius_m", location.getRadiusMeters());
            deniedAttempts.recordHardBlock(userId, institutionId, "OUTSIDE_GEOFENCE", facts);
            throw new BusinessRuleViolationException("OUTSIDE_GEOFENCE",
                    "Check-in location is outside the institution's configured geofence.");
        }

        requireFreshOwnSelfie(tenantId, userId, request.photoFileId(), now);

        double rawConfidence = livenessVerifier.scoreLiveness(request.livenessFrames());
        double livenessConfidence = Double.isNaN(rawConfidence) ? 0.0 : Math.max(0.0, Math.min(1.0, rawConfidence));
        boolean livenessPassed = livenessConfidence >= LIVENESS_CONFIDENCE_THRESHOLD;
        String status = livenessPassed ? STATUS_PRESENT : STATUS_PROVISIONAL;

        UUID checkinId = UUID.randomUUID();
        UUID approvalRequestId = null;
        String approvalStatus = null;
        if (!livenessPassed) {
            Institution institution = institutions.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Institution not found"));
            Optional<StaffCheckinApproverResolver.StepSpec> approver =
                    approverResolver.firstApprover(tenantId, activeRole, assignment, institution, location);
            approvalRequestId = UUID.randomUUID();
            approvalStatus = approver.isPresent() ? "PENDING" : "BLOCKED";
            raiseApproval(tenantId, userId, activeRole, institutionId, checkinId, approvalRequestId, approver);
        }

        StaffAttendanceCheckin saved;
        try {
            // Flushed here so a same-day race on the unique index surfaces as a 409 now, not as a
            // generic error at commit.
            saved = checkins.saveAndFlush(StaffAttendanceCheckin.builder()
                    .tenantId(tenantId).staffAttendanceCheckinId(checkinId)
                    .institutionId(institutionId).staffUserId(userId)
                    .photoFileId(request.photoFileId())
                    .capturedAt(now)
                    .attendanceDate(attendanceDate)
                    .latitude(request.latitude()).longitude(request.longitude())
                    .gpsAccuracyMeters(request.gpsAccuracyMeters())
                    .distanceFromInstitutionM(distanceMeters)
                    .deviceType(normaliseDeviceType(request.deviceType()))
                    .livenessConfidence(BigDecimal.valueOf(livenessConfidence).setScale(4, RoundingMode.HALF_UP))
                    .status(status)
                    .approvalRequestId(approvalRequestId)
                    .createdBy(userId).updatedBy(userId).rowVersion(1)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            if (isOnePerDayViolation(ex)) {
                throw new DuplicateResourceException(ALREADY_CHECKED_IN_MESSAGE);
            }
            throw ex;
        }

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("status", status);
        newValues.put("distance_from_institution_m", distanceMeters.toPlainString());
        newValues.put("liveness_confidence", saved.getLivenessConfidence().toPlainString());
        if (approvalRequestId != null) {
            newValues.put("approval_request_id", approvalRequestId.toString());
            newValues.put("approval_status", approvalStatus);
        }
        audit.record(new AuditEntry(AuditEventType.STAFF_ATTENDANCE_CHECKIN_SUBMITTED, SUBMIT_ACTION, SUBJECT_TYPE,
                checkinId, institutionId, null, newValues, 1, "P", "SUCCESS"));
        return toStatusResponse(saved);
    }

    // ------------------------------------------------------------------------------- own reads

    @Transactional(readOnly = true)
    public CheckInStatusResponse ownStatus(UUID tenantId, UUID userId, UUID checkinId) {
        StaffAttendanceCheckin row = checkins.findByTenantIdAndStaffAttendanceCheckinId(tenantId, checkinId)
                .filter(r -> userId.equals(r.getStaffUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Check-in not found"));
        return toStatusResponse(row);
    }

    @Transactional(readOnly = true)
    public List<CheckInStatusResponse> ownHistory(UUID tenantId, UUID userId) {
        return checkins.findTop100ByTenantIdAndStaffUserIdOrderByCapturedAtDesc(tenantId, userId).stream()
                .map(this::toStatusResponse).toList();
    }

    // ------------------------------------------------------------------------------- review

    @Transactional
    public CheckInStatusResponse approve(UUID tenantId, TenantPrincipal principal, UUID checkinId, String remark) {
        String cleaned = remark == null || remark.isBlank() ? null : remark.trim();
        return decide(tenantId, principal, checkinId, "APPROVED", cleaned);
    }

    @Transactional
    public CheckInStatusResponse reject(UUID tenantId, TenantPrincipal principal, UUID checkinId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleViolationException("REJECTION_REMARK_REQUIRED", "A reason is required to reject a check-in.");
        }
        return decide(tenantId, principal, checkinId, "REJECTED", reason.trim());
    }

    private CheckInStatusResponse decide(UUID tenantId, TenantPrincipal principal, UUID checkinId,
            String decision, String remark) {
        if (remark != null && remark.length() > MAX_REMARK_LENGTH) {
            throw new BusinessRuleViolationException("REMARK_TOO_LONG", "A review remark may be at most 1000 characters.");
        }
        StaffAttendanceCheckin row = checkins.findByTenantIdAndStaffAttendanceCheckinId(tenantId, checkinId)
                .filter(r -> ApprovalInboxService.inCallerScope(r.getInstitutionId()))
                .orElseThrow(() -> new ResourceNotFoundException("Check-in not found"));
        if (row.getApprovalRequestId() == null || !REVIEWABLE_STATUSES.contains(row.getStatus())) {
            throw new BusinessRuleViolationException("CHECKIN_NOT_REVIEWABLE",
                    "Only a provisional or check-in-only record can be reviewed.");
        }
        ApprovalRequest request = approvalRequests.findByIdTenantIdAndIdApprovalRequestId(tenantId, row.getApprovalRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Check-in not found"));
        // The one decision path: every rule lives in the engine; the outcome handler moves the check-in.
        approvalInbox.decideApproval(tenantId, row.getApprovalRequestId(),
                new DecideApprovalRequestDto(request.getCurrentStepNo(), decision, remark, null), principal);
        StaffAttendanceCheckin decided = checkins.findByTenantIdAndStaffAttendanceCheckinId(tenantId, checkinId)
                .orElse(row);
        return toStatusResponse(decided);
    }

    /**
     * Provisional / check-in-only records waiting on the caller, decided by exactly the rules the
     * engine applies when the caller acts (ApprovalInboxService.listPendingApprovals): current
     * PENDING step, not the requester, institution scope, route (role, named user, delegation,
     * roll-down), not handed over, and the review action at the target's scope. A constant number
     * of queries plus routing per item - no per-row request/step lookups.
     */
    @Transactional(readOnly = true)
    public List<CheckInInboxItemResponse> pendingForApprover(UUID tenantId, TenantPrincipal principal) {
        UUID userId = principal.userAccountId();
        String activeRole = TenantContext.isSet() ? TenantContext.currentActiveRoleCode() : null;
        List<StaffAttendanceCheckin> rows = checkins
                .findByTenantIdAndStatusInAndApprovalRequestIdIsNotNull(tenantId, REVIEWABLE_STATUSES).stream()
                .filter(r -> !userId.equals(r.getStaffUserId()) && ApprovalInboxService.inCallerScope(r.getInstitutionId()))
                .toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, ApprovalRequest> requests = approvalRequests
                .findAllById(rows.stream().map(r -> new ApprovalRequestId(tenantId, r.getApprovalRequestId())).toList())
                .stream().collect(Collectors.toMap(r -> r.getId().getApprovalRequestId(), Function.identity(), (a, b) -> a));
        Map<String, ApprovalStep> pendingSteps = new HashMap<>();
        for (ApprovalStep step : approvalSteps.findByIdTenantIdAndDecision(tenantId, "PENDING")) {
            if (requests.containsKey(step.getApprovalRequestId())) {
                pendingSteps.put(step.getApprovalRequestId() + "#" + step.getStepNo(), step);
            }
        }
        return rows.stream().filter(row -> {
            ApprovalRequest req = requests.get(row.getApprovalRequestId());
            if (req == null || !"PENDING".equals(req.getStatus()) || !PROCESS_CODE.equalsIgnoreCase(req.getProcessCode())
                    || userId.equals(req.getRequestedByUserId())) {
                return false;
            }
            ApprovalStep step = pendingSteps.get(req.getId().getApprovalRequestId() + "#" + req.getCurrentStepNo());
            if (step == null) {
                return false;
            }
            Optional<ApprovalRouting.Route> route = approvalRouting.route(tenantId, userId, activeRole, req, step);
            if (route.isEmpty() || (!route.get().actingFor() && approvalRouting.handedOver(tenantId, userId, req))) {
                return false;
            }
            UUID checked = route.get().via() == ApprovalRouting.Via.DELEGATION ? route.get().delegatorUserId() : userId;
            if (checked == null) {
                return false;
            }
            RbacPrincipal rbac = new RbacPrincipal(tenantId, checked);
            // Same scope the engine checks at decision time: institution, or the check-in's
            // department for an HOD (so an HOD sees only their own department's check-ins).
            return permissions.can(rbac, REVIEW_ACTION, approvalInbox.approvalScope(rbac, req),
                    RbacTarget.of(req.getRequestedByUserId())).allowed();
        }).map(row -> new CheckInInboxItemResponse(row.getStaffAttendanceCheckinId(), row.getApprovalRequestId(),
                row.getStaffUserId(), row.getInstitutionId(), row.getCapturedAt(), row.getDistanceFromInstitutionM(),
                row.getGpsAccuracyMeters(), row.getLivenessConfidence(), row.getStatus()))
                .toList();
    }

    // ------------------------------------------------------------------------------- day end

    /**
     * Scheduled-job entry point for one check-in: PROVISIONAL -> CHECK_IN_ONLY, the unreviewed
     * step SKIPPED and a new step raised for the next higher authority on the same request.
     * Returns false when the row was already decided (a reviewer won the race). Never touches
     * capture columns.
     */
    @Transactional
    public boolean expireProvisional(UUID tenantId, UUID checkinId, UUID systemActorId,
            InstitutionAttendanceLocation location, Institution institution) {
        StaffAttendanceCheckin row = checkins.findForUpdate(tenantId, checkinId).orElse(null);
        if (row == null || !STATUS_PROVISIONAL.equals(row.getStatus())) {
            return false;
        }
        Instant now = Instant.now();
        String escalatedTo = row.getApprovalRequestId() == null ? null
                : escalate(tenantId, row.getApprovalRequestId(), systemActorId, location, institution, now);

        row.setStatus(STATUS_CHECK_IN_ONLY);
        row.setUpdatedBy(systemActorId);
        checkins.save(row);

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("status", STATUS_CHECK_IN_ONLY);
        newValues.put("reason", "NOT_REVIEWED_BY_DAY_END");
        if (escalatedTo != null) {
            newValues.put("escalated_to_role", escalatedTo);
        }
        audit.record(new AuditEntry(AuditEventType.STAFF_ATTENDANCE_CHECKIN_EXPIRED, REVIEW_ACTION, SUBJECT_TYPE,
                checkinId, row.getInstitutionId(), Map.of("status", STATUS_PROVISIONAL), newValues, 1, "P", "SUCCESS"));
        return true;
    }

    /** Skips the unreviewed step and raises the next one; returns the role it now waits on, or null. */
    private String escalate(UUID tenantId, UUID approvalRequestId, UUID systemActorId,
            InstitutionAttendanceLocation location, Institution institution, Instant now) {
        ApprovalRequest request = approvalRequests.findByIdTenantIdAndIdApprovalRequestId(tenantId, approvalRequestId)
                .orElse(null);
        if (request == null) {
            return null;
        }
        if ("BLOCKED".equals(request.getStatus())) {
            // Raised with no reviewer (Principal with no configured higher authority). If one has
            // been configured since, the request can start now; otherwise it stays BLOCKED.
            String configured = location == null ? null : location.getApproverForPrincipalRole();
            if (configured == null || configured.isBlank()) {
                return null;
            }
            saveStep(tenantId, approvalRequestId, (short) 1, configured.trim(), null, systemActorId);
            request.setStatus("PENDING");
            request.setCurrentStepNo((short) 1);
            request.setUpdatedAt(now);
            request.setUpdatedBy(systemActorId);
            approvalRequests.save(request);
            return configured.trim();
        }
        if (!"PENDING".equals(request.getStatus())) {
            return null;
        }
        short stepNo = request.getCurrentStepNo();
        ApprovalStep current = approvalSteps.findByIdTenantIdAndApprovalRequestIdAndStepNo(tenantId, approvalRequestId, stepNo)
                .orElse(null);
        if (current == null || !"PENDING".equals(current.getDecision())) {
            return null;
        }
        current.setDecision("SKIPPED");
        current.setSkipReason("Not reviewed by the end of the check-in day; escalated to a higher authority.");
        current.setDecidedAt(now);
        current.setUpdatedAt(now);
        current.setUpdatedBy(systemActorId);
        approvalSteps.save(current);

        StaffCheckinApproverResolver.StepSpec next =
                approverResolver.escalationFor(current.getApproverRoleCode(), institution, location);
        short nextNo = (short) (stepNo + 1);
        saveStep(tenantId, approvalRequestId, nextNo, next.roleCode(), next.userId(), systemActorId);
        request.setCurrentStepNo(nextNo);
        request.setUpdatedAt(now);
        request.setUpdatedBy(systemActorId);
        approvalRequests.save(request);
        return next.roleCode();
    }

    // ------------------------------------------------------------------------------- helpers

    /** True when the violated constraint is the one-check-in-per-day index (any other is rethrown). */
    static boolean isOnePerDayViolation(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains(ONE_PER_DAY_INDEX)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private void raiseApproval(UUID tenantId, UUID userId, String activeRole, UUID institutionId, UUID checkinId,
            UUID approvalRequestId, Optional<StaffCheckinApproverResolver.StepSpec> approver) {
        approvalRequests.save(ApprovalRequest.builder()
                .id(new ApprovalRequestId(tenantId, approvalRequestId))
                .institutionId(institutionId)
                .processCode(PROCESS_CODE)
                .subjectType(SUBJECT_TYPE)
                .subjectId(checkinId)
                .requestedByUserId(userId)
                .requesterActiveRole(activeRole)
                .status(approver.isPresent() ? "PENDING" : "BLOCKED")
                .currentStepNo((short) 1)
                .createdBy(userId).updatedBy(userId).build());
        approver.ifPresent(spec -> saveStep(tenantId, approvalRequestId, (short) 1, spec.roleCode(), spec.userId(), userId));
    }

    private void saveStep(UUID tenantId, UUID approvalRequestId, short stepNo, String roleCode, UUID approverUserId, UUID actor) {
        approvalSteps.save(ApprovalStep.builder()
                .id(new ApprovalStepId(tenantId, UUID.randomUUID()))
                .approvalRequestId(approvalRequestId).stepNo(stepNo)
                .approverRoleCode(roleCode).approverUserId(approverUserId)
                .decision("PENDING").createdBy(actor).updatedBy(actor).build());
    }

    /**
     * "Fresh selfie": the file must be this caller's own upload, active, not infected, a JPEG/PNG
     * image, uploaded within the last {@link #PHOTO_FRESHNESS}, and not already used by another
     * check-in. A file that does not exist or belongs to someone else looks missing (no leak).
     */
    private void requireFreshOwnSelfie(UUID tenantId, UUID userId, UUID photoFileId, Instant now) {
        FileObject file = files.findById(new FileObjectId(tenantId, photoFileId))
                .filter(f -> userId.equals(f.getUploadedBy()))
                .orElseThrow(() -> new ResourceNotFoundException("Check-in photo not found"));
        if (!FilePurpose.STAFF_ATTENDANCE_SELFIE.name().equals(file.getPurpose())) {
            throw new BusinessRuleViolationException("FILE_PURPOSE_MISMATCH",
                    "Upload the check-in photo with purpose STAFF_ATTENDANCE_SELFIE.");
        }
        if (!"ACTIVE".equals(file.getStatus()) || "INFECTED".equals(file.getScanStatus())) {
            throw new BusinessRuleViolationException("FILE_NOT_AVAILABLE", "The check-in photo is not available.");
        }
        if (file.getContentType() == null || !SELFIE_CONTENT_TYPES.contains(file.getContentType().toLowerCase(Locale.ROOT))) {
            throw new BusinessRuleViolationException("FILE_TYPE_NOT_ALLOWED", "The check-in photo must be a JPG or PNG image.");
        }
        if (file.getCreatedAt() == null || file.getCreatedAt().isBefore(now.minus(PHOTO_FRESHNESS))) {
            throw new BusinessRuleViolationException("PHOTO_NOT_FRESH",
                    "Take a new selfie: the check-in photo must be captured within the last 10 minutes.");
        }
        if (checkins.existsByTenantIdAndPhotoFileId(tenantId, photoFileId)) {
            throw new BusinessRuleViolationException("PHOTO_ALREADY_USED",
                    "This photo was already used for a check-in. Take a new selfie.");
        }
    }

    private static void validate(CheckInSubmitRequest request) {
        if (request == null || request.photoFileId() == null || request.latitude() == null
                || request.longitude() == null || request.gpsAccuracyMeters() == null) {
            throw new IllegalArgumentException("A photo, latitude, longitude and GPS accuracy are required.");
        }
        if (request.latitude().abs().compareTo(BigDecimal.valueOf(90)) > 0
                || request.longitude().abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("Latitude must be within -90..90 and longitude within -180..180.");
        }
        if (request.gpsAccuracyMeters().signum() < 0) {
            throw new IllegalArgumentException("GPS accuracy cannot be negative.");
        }
        List<byte[]> frames = request.livenessFrames();
        if (frames != null) {
            if (frames.size() > MAX_LIVENESS_FRAMES) {
                throw new IllegalArgumentException("At most " + MAX_LIVENESS_FRAMES + " liveness frames may be sent.");
            }
            for (byte[] frame : frames) {
                if (frame != null && frame.length > MAX_FRAME_BYTES) {
                    throw new BusinessRuleViolationException("PAYLOAD_TOO_LARGE", "Each liveness frame may be at most 2 MiB.");
                }
            }
        }
    }

    /** Informational only (never a security decision): trimmed, upper-cased, bounded. */
    static String normaliseDeviceType(String deviceType) {
        if (deviceType == null || deviceType.isBlank()) {
            return null;
        }
        String value = deviceType.trim().toUpperCase(Locale.ROOT);
        return value.length() > MAX_DEVICE_TYPE_LENGTH ? value.substring(0, MAX_DEVICE_TYPE_LENGTH) : value;
    }

    private StaffAssignment activeAssignment(UUID tenantId, UUID staffProfileId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        List<StaffAssignment> active = staffAssignments.findByTenantIdAndStaffProfileIdOrderByEffectiveFromDesc(tenantId, staffProfileId)
                .stream().filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()))
                .filter(a -> a.getEffectiveTo() == null || !a.getEffectiveTo().isBefore(today))
                .toList();
        if (active.isEmpty()) {
            throw new BusinessRuleViolationException("NO_ACTIVE_ASSIGNMENT", "No active staff assignment found.");
        }
        if (active.size() == 1) {
            return active.get(0);
        }
        // Several institutions: use the one the caller's active role is scoped to (same rule as leave).
        UUID chosen = TenantContext.currentInstitutionIds().stream().findFirst().orElse(null);
        return active.stream().filter(a -> chosen != null && chosen.equals(a.getInstitutionId())).findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException("STAFF_ASSIGNMENT_AMBIGUOUS",
                        "A single active staff institution assignment is required."));
    }

    private CheckInStatusResponse toStatusResponse(StaffAttendanceCheckin row) {
        return new CheckInStatusResponse(row.getStaffAttendanceCheckinId(), row.getInstitutionId(),
                row.getCapturedAt(), row.getDistanceFromInstitutionM(), row.getGpsAccuracyMeters(),
                row.getLivenessConfidence(), row.getStatus(), row.getApprovalRequestId(),
                row.getReviewedAt(), row.getReviewNote());
    }

    /** Standard haversine great-circle distance, in meters. */
    static BigDecimal haversineMeters(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double r = 6371000.0;
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double dLambda = Math.toRadians(lon2.subtract(lon1).doubleValue());
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(r * c).setScale(2, RoundingMode.HALF_UP);
    }
}
