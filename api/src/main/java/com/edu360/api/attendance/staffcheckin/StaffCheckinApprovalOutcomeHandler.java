package com.edu360.api.attendance.staffcheckin;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.approval.ApprovalOutcomeHandler;
import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.approval.ApprovalStep;
import com.edu360.api.approval.ApprovalStepRepository;
import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;

/**
 * WHAT: the final side effect of a decided STAFF_ATTENDANCE_REVIEW approval request. The shared
 * engine (ApprovalInboxService.decideApproval) has already checked scope, self-approval, routing,
 * separation of duties and the STAFF_ATTENDANCE_CHECKIN_REVIEW action before calling this; this
 * class only moves the check-in to PRESENT or REJECTED and audits the transition.
 *
 * Only the review columns are written (status, reviewed_at, reviewed_by, review_note,
 * updated_by). The capture columns are mapped updatable=false and are also guarded by
 * app.fn_checkin_capture_guard.
 */
@Component
public class StaffCheckinApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private final StaffAttendanceCheckinRepository checkins;
    private final ApprovalStepRepository approvalSteps;
    private final AuditLogService audit;

    public StaffCheckinApprovalOutcomeHandler(StaffAttendanceCheckinRepository checkins,
            ApprovalStepRepository approvalSteps, AuditLogService audit) {
        this.checkins = checkins;
        this.approvalSteps = approvalSteps;
        this.audit = audit;
    }

    @Override
    public boolean supports(ApprovalRequest request) {
        return request != null
                && StaffCheckInService.PROCESS_CODE.equalsIgnoreCase(request.getProcessCode())
                && StaffCheckInService.SUBJECT_TYPE.equalsIgnoreCase(request.getSubjectType());
    }

    @Override
    @Transactional
    public void apply(UUID tenantId, ApprovalRequest request, boolean approved, UUID actorUserId) {
        StaffAttendanceCheckin row = checkins.findForUpdate(tenantId, request.getSubjectId())
                .orElseThrow(() -> new BusinessRuleViolationException("CHECKIN_MISSING",
                        "The check-in linked to this approval no longer exists."));
        String previous = row.getStatus();
        if (!StaffCheckInService.REVIEWABLE_STATUSES.contains(previous)) {
            throw new BusinessRuleViolationException("CHECKIN_NOT_REVIEWABLE",
                    "Only a provisional or check-in-only record can be reviewed.");
        }
        String remark = approvalSteps
                .findByIdTenantIdAndApprovalRequestIdAndStepNo(tenantId, request.getId().getApprovalRequestId(),
                        request.getCurrentStepNo())
                .map(ApprovalStep::getRemark).orElse(null);
        if (!approved && (remark == null || remark.isBlank())) {
            // The engine already requires a remark on rejection; never record a reasonless reject.
            throw new BusinessRuleViolationException("REJECTION_REMARK_REQUIRED",
                    "A reason is required to reject a check-in.");
        }
        String next = approved ? StaffCheckInService.STATUS_PRESENT : StaffCheckInService.STATUS_REJECTED;
        row.setStatus(next);
        row.setReviewedAt(Instant.now());
        row.setReviewedBy(actorUserId);
        row.setReviewNote(remark == null || remark.isBlank() ? null : remark.trim());
        row.setUpdatedBy(actorUserId);
        checkins.save(row);

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("status", next);
        newValues.put("approval_request_id", request.getId().getApprovalRequestId().toString());
        newValues.put("decided_after_day_end", StaffCheckInService.STATUS_CHECK_IN_ONLY.equals(previous));
        audit.record(new AuditEntry(
                approved ? AuditEventType.STAFF_ATTENDANCE_CHECKIN_APPROVED : AuditEventType.STAFF_ATTENDANCE_CHECKIN_REJECTED,
                StaffCheckInService.REVIEW_ACTION, StaffCheckInService.SUBJECT_TYPE, row.getStaffAttendanceCheckinId(),
                row.getInstitutionId(), Map.of("status", previous), newValues, 1, "P", "SUCCESS"));
    }
}
