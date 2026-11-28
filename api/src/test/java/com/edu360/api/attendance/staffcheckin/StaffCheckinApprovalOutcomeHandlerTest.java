package com.edu360.api.attendance.staffcheckin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.edu360.api.approval.ApprovalInboxService;
import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.approval.ApprovalRequestId;
import com.edu360.api.approval.ApprovalStep;
import com.edu360.api.approval.ApprovalStepRepository;
import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;

class StaffCheckinApprovalOutcomeHandlerTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID checkinId = UUID.randomUUID();
    private final UUID approvalId = UUID.randomUUID();
    private final UUID reviewer = UUID.randomUUID();

    private StaffAttendanceCheckinRepository checkins;
    private ApprovalStepRepository steps;
    private AuditLogService audit;
    private StaffCheckinApprovalOutcomeHandler handler;
    private ApprovalRequest request;

    @BeforeEach
    void setUp() {
        checkins = mock(StaffAttendanceCheckinRepository.class);
        steps = mock(ApprovalStepRepository.class);
        audit = mock(AuditLogService.class);
        handler = new StaffCheckinApprovalOutcomeHandler(checkins, steps, audit);
        request = ApprovalRequest.builder().id(new ApprovalRequestId(tenantId, approvalId))
                .processCode("STAFF_ATTENDANCE_REVIEW").subjectType("STAFF_ATTENDANCE_CHECKIN")
                .subjectId(checkinId).currentStepNo((short) 1).build();
    }

    private StaffAttendanceCheckin row(String status) {
        StaffAttendanceCheckin row = StaffAttendanceCheckin.builder().tenantId(tenantId).staffAttendanceCheckinId(checkinId)
                .institutionId(UUID.randomUUID()).staffUserId(UUID.randomUUID()).photoFileId(UUID.randomUUID())
                .capturedAt(Instant.now()).latitude(BigDecimal.ONE).longitude(BigDecimal.ONE)
                .gpsAccuracyMeters(BigDecimal.TEN).distanceFromInstitutionM(BigDecimal.TEN)
                .livenessConfidence(new BigDecimal("0.1000")).status(status).approvalRequestId(approvalId).build();
        when(checkins.findForUpdate(tenantId, checkinId)).thenReturn(Optional.of(row));
        return row;
    }

    private void remark(String text) {
        when(steps.findByIdTenantIdAndApprovalRequestIdAndStepNo(tenantId, approvalId, (short) 1))
                .thenReturn(Optional.of(ApprovalStep.builder().remark(text).build()));
    }

    @Test
    void engineResolvesTheReviewActionAndThisHandlerSupportsTheProcess() {
        assertThat(ApprovalInboxService.resolveApprovalAction(request)).isEqualTo("STAFF_ATTENDANCE_CHECKIN_REVIEW");
        assertThat(handler.supports(request)).isTrue();
        request.setProcessCode("FACULTY_LEAVE_COLLEGE");
        assertThat(handler.supports(request)).isFalse();
    }

    @Test
    void approved_setsPresentAndReviewer_andAudits() {
        StaffAttendanceCheckin row = row("PROVISIONAL");
        remark(null);

        handler.apply(tenantId, request, true, reviewer);

        assertThat(row.getStatus()).isEqualTo("PRESENT");
        assertThat(row.getReviewedBy()).isEqualTo(reviewer);
        assertThat(row.getReviewedAt()).isNotNull();
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue().eventType()).isEqualTo(AuditEventType.STAFF_ATTENDANCE_CHECKIN_APPROVED);
    }

    @Test
    void rejected_recordsTheStepRemarkAsReviewNote() {
        StaffAttendanceCheckin row = row("PROVISIONAL");
        remark("Was on leave that day");

        handler.apply(tenantId, request, false, reviewer);

        assertThat(row.getStatus()).isEqualTo("REJECTED");
        assertThat(row.getReviewNote()).isEqualTo("Was on leave that day");
    }

    @Test
    void rejectedWithoutRemark_isRefused() {
        row("PROVISIONAL");
        remark(" ");
        assertThatThrownBy(() -> handler.apply(tenantId, request, false, reviewer))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(checkins, never()).save(any());
    }

    @Test
    void checkInOnly_canStillBeResolvedLater_andIsAuditedAsLate() {
        StaffAttendanceCheckin row = row("CHECK_IN_ONLY");
        remark(null);

        handler.apply(tenantId, request, true, reviewer);

        assertThat(row.getStatus()).isEqualTo("PRESENT");
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue().newValues()).containsEntry("decided_after_day_end", true);
    }

    @Test
    void alreadyDecidedRecord_isRefused() {
        row("PRESENT");
        assertThatThrownBy(() -> handler.apply(tenantId, request, true, reviewer))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
