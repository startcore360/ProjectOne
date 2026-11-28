package com.edu360.api.attendance.staffcheckin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.edu360.api.approval.ApprovalInboxService;
import com.edu360.api.approval.ApprovalRequest;
import com.edu360.api.approval.ApprovalRequestId;
import com.edu360.api.approval.ApprovalRequestRepository;
import com.edu360.api.approval.ApprovalRouting;
import com.edu360.api.approval.ApprovalStep;
import com.edu360.api.approval.ApprovalStepId;
import com.edu360.api.approval.ApprovalStepRepository;
import com.edu360.api.approval.dto.DecideApprovalRequestDto;
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
import com.edu360.api.identity.UserType;
import com.edu360.api.institution.Department;
import com.edu360.api.institution.DepartmentRepository;
import com.edu360.api.institution.Institution;
import com.edu360.api.institution.InstitutionId;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.StaffAssignment;
import com.edu360.api.institution.StaffAssignmentRepository;
import com.edu360.api.institution.StaffProfile;
import com.edu360.api.institution.StaffProfileId;
import com.edu360.api.institution.StaffProfileRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.TenantContext;

class StaffCheckInServiceTest {

    private static final BigDecimal LAT = new BigDecimal("12.971600");
    private static final BigDecimal LNG = new BigDecimal("77.594600");

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();
    private final UUID staffProfileId = UUID.randomUUID();
    private final TenantPrincipal principal = new TenantPrincipal(tenantId, userId, UserType.STAFF, "Test Staff", false);

    private JdbcTemplate jdbc;
    private LivenessVerifier livenessVerifier;
    private StaffAssignmentRepository staffAssignments;
    private DepartmentRepository departments;
    private InstitutionAttendanceLocationRepository locations;
    private StaffAttendanceCheckinRepository checkins;
    private FileObjectRepository files;
    private ApprovalRequestRepository approvalRequests;
    private ApprovalStepRepository approvalSteps;
    private ApprovalInboxService approvalInbox;
    private AuditLogService audit;
    private StaffCheckinDeniedAttemptAuditor deniedAttempts;
    private InstitutionAttendanceLocation location;
    private Institution institution;
    private StaffCheckInService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        livenessVerifier = mock(LivenessVerifier.class);
        StaffProfileRepository staffProfiles = mock(StaffProfileRepository.class);
        staffAssignments = mock(StaffAssignmentRepository.class);
        departments = mock(DepartmentRepository.class);
        InstitutionRepository institutions = mock(InstitutionRepository.class);
        locations = mock(InstitutionAttendanceLocationRepository.class);
        checkins = mock(StaffAttendanceCheckinRepository.class);
        files = mock(FileObjectRepository.class);
        approvalRequests = mock(ApprovalRequestRepository.class);
        approvalSteps = mock(ApprovalStepRepository.class);
        approvalInbox = mock(ApprovalInboxService.class);
        audit = mock(AuditLogService.class);
        deniedAttempts = mock(StaffCheckinDeniedAttemptAuditor.class);
        service = new StaffCheckInService(mock(PermissionEvaluatorService.class), livenessVerifier, staffProfiles,
                staffAssignments, institutions, locations, checkins, files, approvalRequests, approvalSteps,
                approvalInbox, mock(ApprovalRouting.class), new StaffCheckinApproverResolver(jdbc, departments), audit,
                deniedAttempts);

        when(checkins.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(checkins.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StaffProfile staff = new StaffProfile();
        staff.setId(new StaffProfileId(tenantId, staffProfileId));
        staff.setUserAccountId(userId);
        when(staffProfiles.findByIdTenantIdAndUserAccountId(tenantId, userId)).thenReturn(Optional.of(staff));
        givenAssignment(null, UUID.randomUUID());

        institution = new Institution();
        institution.setId(new InstitutionId(tenantId, institutionId));
        institution.setInstitutionType("COLLEGE");
        when(institutions.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId)).thenReturn(Optional.of(institution));

        location = InstitutionAttendanceLocation.builder()
                .tenantId(tenantId).institutionAttendanceLocationId(UUID.randomUUID())
                .institutionId(institutionId).latitude(LAT).longitude(LNG)
                .radiusMeters(200).gpsAccuracyThresholdMeters(50)
                .timezone("Asia/Kolkata").retentionDays(180)
                .createdBy(userId).updatedBy(userId).rowVersion(1).build();
        when(locations.findByTenantIdAndInstitutionId(tenantId, institutionId)).thenReturn(Optional.of(location));

        activeRole("ACCOUNTANT", ScopeType.INSTITUTION);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void activeRole(String role, ScopeType scope) {
        TenantContext.clear();
        TenantContext.set(tenantId, IsolationMode.SHARED, null, "ALL", Set.of(), role, scope);
    }

    private StaffAssignment givenAssignment(UUID departmentId, UUID assignmentId) {
        StaffAssignment assignment = new StaffAssignment();
        assignment.setStaffAssignmentId(assignmentId);
        assignment.setStaffProfileId(staffProfileId);
        assignment.setInstitutionId(institutionId);
        assignment.setDepartmentId(departmentId);
        assignment.setStatus("ACTIVE");
        when(staffAssignments.findByTenantIdAndStaffProfileIdOrderByEffectiveFromDesc(tenantId, staffProfileId))
                .thenReturn(List.of(assignment));
        return assignment;
    }

    private UUID freshPhoto(UUID owner, Instant uploadedAt) {
        return photo(owner, uploadedAt, "STAFF_ATTENDANCE_SELFIE");
    }

    private UUID photo(UUID owner, Instant uploadedAt, String purpose) {
        UUID fileId = UUID.randomUUID();
        FileObject file = FileObject.builder().id(new FileObjectId(tenantId, fileId)).uploadedBy(owner)
                .purpose(purpose).status("ACTIVE").scanStatus("CLEAN").contentType("image/jpeg").createdAt(uploadedAt).build();
        when(files.findById(new FileObjectId(tenantId, fileId))).thenReturn(Optional.of(file));
        return fileId;
    }

    private CheckInSubmitRequest request(BigDecimal lat, BigDecimal accuracy, double liveness) {
        when(livenessVerifier.scoreLiveness(any())).thenReturn(liveness);
        return new CheckInSubmitRequest(freshPhoto(userId, Instant.now().minusSeconds(30)), lat, LNG, accuracy,
                " android ", List.of(new byte[]{1}, new byte[]{2}));
    }

    private ApprovalStep capturedStep() {
        ArgumentCaptor<ApprovalStep> captor = ArgumentCaptor.forClass(ApprovalStep.class);
        verify(approvalSteps).save(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------ submit

    @Test
    void insideGeofenceAndLivenessPasses_isPresentWithNoApproval() {
        CheckInStatusResponse response = service.submit(tenantId, principal, request(LAT, BigDecimal.TEN, 0.9));

        assertThat(response.status()).isEqualTo("PRESENT");
        assertThat(response.approvalRequestId()).isNull();
        assertThat(response.capturedAt()).isNotNull();
        verify(approvalRequests, never()).save(any());
        ArgumentCaptor<StaffAttendanceCheckin> saved = ArgumentCaptor.forClass(StaffAttendanceCheckin.class);
        verify(checkins).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDeviceType()).isEqualTo("ANDROID");
        // attendance_date is the institution-local (Asia/Kolkata) date of captured_at.
        assertThat(saved.getValue().getAttendanceDate())
                .isEqualTo(LocalDate.ofInstant(saved.getValue().getCapturedAt(), ZoneId.of("Asia/Kolkata")));
        verify(audit).record(any(AuditEntry.class));
        verify(deniedAttempts, never()).recordHardBlock(any(), any(), any(), any());
    }

    @Test
    void outsideGeofence_isHardBlockedEvenWithDoubtfulLiveness_andNothingIsWritten() {
        // ~1.1 km away from a 200 m radius; liveness doubt must not turn this into PROVISIONAL.
        assertThatThrownBy(() -> service.submit(tenantId, principal, request(new BigDecimal("12.981600"), BigDecimal.TEN, 0.1)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("geofence");
        verify(checkins, never()).saveAndFlush(any());
        verify(approvalRequests, never()).save(any());
        // The refusal is audited through the separate REQUIRES_NEW auditor, with numbers only.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> facts = ArgumentCaptor.forClass(Map.class);
        verify(deniedAttempts).recordHardBlock(eq(userId), eq(institutionId), eq("OUTSIDE_GEOFENCE"), facts.capture());
        assertThat(facts.getValue()).containsEntry("radius_m", 200).containsKey("distance_m")
                .doesNotContainKeys("latitude", "longitude");
        assertThat((Long) facts.getValue().get("distance_m")).isGreaterThan(1000L);
        verify(audit, never()).record(any(AuditEntry.class));
    }

    @Test
    void gpsAccuracyWorseThanThreshold_isHardBlocked_neverProvisional() {
        assertThatThrownBy(() -> service.submit(tenantId, principal, request(LAT, new BigDecimal("500"), 0.1)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("accuracy");
        verify(checkins, never()).saveAndFlush(any());
        verify(approvalRequests, never()).save(any());
        verify(deniedAttempts).recordHardBlock(eq(userId), eq(institutionId), eq("GPS_ACCURACY_TOO_LOW"),
                eq(Map.of("gps_accuracy_m", 500L, "gps_accuracy_threshold_m", 50)));
    }

    @Test
    void secondCheckInSameDay_is409_beforeAnythingIsWrittenOrRouted() {
        when(checkins.existsByTenantIdAndStaffUserIdAndAttendanceDate(eq(tenantId), eq(userId), any(LocalDate.class)))
                .thenReturn(true);

        // Even a hard-blockable attempt gets the clear "already checked in" answer, not an audit.
        assertThatThrownBy(() -> service.submit(tenantId, principal, request(new BigDecimal("12.981600"), BigDecimal.TEN, 0.1)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("You have already checked in today.");
        verify(checkins, never()).saveAndFlush(any());
        verify(approvalRequests, never()).save(any());
        verify(approvalSteps, never()).save(any());
        verify(deniedAttempts, never()).recordHardBlock(any(), any(), any(), any());
    }

    @Test
    void sameDayCheck_usesTheInstitutionLocalDate() {
        location.setTimezone("Pacific/Kiritimati"); // UTC+14: often a different date from UTC
        service.submit(tenantId, principal, request(LAT, BigDecimal.TEN, 0.9));

        ArgumentCaptor<LocalDate> day = ArgumentCaptor.forClass(LocalDate.class);
        verify(checkins).existsByTenantIdAndStaffUserIdAndAttendanceDate(eq(tenantId), eq(userId), day.capture());
        ArgumentCaptor<StaffAttendanceCheckin> saved = ArgumentCaptor.forClass(StaffAttendanceCheckin.class);
        verify(checkins).saveAndFlush(saved.capture());
        assertThat(day.getValue()).isEqualTo(saved.getValue().getAttendanceDate())
                .isEqualTo(LocalDate.ofInstant(saved.getValue().getCapturedAt(), ZoneId.of("Pacific/Kiritimati")));
    }

    @Test
    void sameDayRaceOnTheUniqueIndex_isMappedTo409() {
        when(checkins.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"ux_staff_attendance_checkin_staff_day\"")));

        assertThatThrownBy(() -> service.submit(tenantId, principal, request(LAT, BigDecimal.TEN, 0.9)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("You have already checked in today.");
    }

    @Test
    void otherIntegrityViolation_isNotDisguisedAsADuplicateDay() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("fk_staff_attendance_checkin_file_id");
        when(checkins.saveAndFlush(any())).thenThrow(other);

        assertThatThrownBy(() -> service.submit(tenantId, principal, request(LAT, BigDecimal.TEN, 0.9)))
                .isSameAs(other);
    }

    @Test
    void photoUploadedForAnotherPurpose_isRefused() {
        when(livenessVerifier.scoreLiveness(any())).thenReturn(0.9);
        UUID profilePhoto = photo(userId, Instant.now(), "PROFILE_PHOTO");
        assertThatThrownBy(() -> service.submit(tenantId, principal,
                new CheckInSubmitRequest(profilePhoto, LAT, LNG, BigDecimal.TEN, null, List.of())))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("STAFF_ATTENDANCE_SELFIE");
        verify(checkins, never()).saveAndFlush(any());
    }

    @Test
    void lowLiveness_isProvisionalAndRaisesRequestInSharedEngine_accountantGoesToPrincipal() {
        CheckInStatusResponse response = service.submit(tenantId, principal, request(LAT, BigDecimal.TEN, 0.1));

        assertThat(response.status()).isEqualTo("PROVISIONAL");
        assertThat(response.approvalRequestId()).isNotNull();
        ArgumentCaptor<ApprovalRequest> req = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequests).save(req.capture());
        assertThat(req.getValue().getProcessCode()).isEqualTo("STAFF_ATTENDANCE_REVIEW");
        assertThat(req.getValue().getSubjectType()).isEqualTo("STAFF_ATTENDANCE_CHECKIN");
        assertThat(req.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(req.getValue().getRequestedByUserId()).isEqualTo(userId);
        assertThat(capturedStep().getApproverRoleCode()).isEqualTo("PRINCIPAL_COLLEGE");
    }

    @Test
    void teacherInDepartment_routesToTheirHodByName() {
        activeRole("ASSOCIATE_PROFESSOR", ScopeType.DEPARTMENT);
        UUID departmentId = UUID.randomUUID();
        UUID hodAssignmentId = UUID.randomUUID();
        UUID hodUserId = UUID.randomUUID();
        givenAssignment(departmentId, UUID.randomUUID());
        when(departments.findByTenantIdAndDepartmentId(tenantId, departmentId)).thenReturn(Optional.of(Department.builder()
                .departmentId(departmentId).tenantId(tenantId).institutionId(institutionId)
                .hodStaffAssignmentId(hodAssignmentId).status("ACTIVE").build()));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(), eq(tenantId), eq(hodAssignmentId)))
                .thenReturn(hodUserId);

        service.submit(tenantId, principal, request(LAT, BigDecimal.TEN, 0.1));

        ApprovalStep step = capturedStep();
        assertThat(step.getApproverRoleCode()).isEqualTo("HEAD_OF_DEPARTMENT");
        assertThat(step.getApproverUserId()).isEqualTo(hodUserId);
    }

    @Test
    void hodOfOwnDepartment_routesToPrincipal() {
        activeRole("ASSOCIATE_PROFESSOR", ScopeType.DEPARTMENT);
        UUID departmentId = UUID.randomUUID();
        UUID ownAssignmentId = UUID.randomUUID();
        givenAssignment(departmentId, ownAssignmentId);
        when(departments.findByTenantIdAndDepartmentId(tenantId, departmentId)).thenReturn(Optional.of(Department.builder()
                .departmentId(departmentId).tenantId(tenantId).institutionId(institutionId)
                .hodStaffAssignmentId(ownAssignmentId).status("ACTIVE").build()));

        service.submit(tenantId, principal, request(LAT, BigDecimal.TEN, 0.1));

        assertThat(capturedStep().getApproverRoleCode()).isEqualTo("PRINCIPAL_COLLEGE");
    }

    @Test
    void principal_routesToConfiguredHigherAuthority() {
        activeRole("PRINCIPAL_COLLEGE", ScopeType.INSTITUTION);
        location.setApproverForPrincipalRole("MANAGEMENT");

        service.submit(tenantId, principal, request(LAT, BigDecimal.TEN, 0.1));

        assertThat(capturedStep().getApproverRoleCode()).isEqualTo("MANAGEMENT");
    }

    @Test
    void principalWithNoConfiguredApprover_isStillKeptProvisional_requestBlocked() {
        activeRole("PRINCIPAL_COLLEGE", ScopeType.INSTITUTION);

        CheckInStatusResponse response = service.submit(tenantId, principal, request(LAT, BigDecimal.TEN, 0.1));

        assertThat(response.status()).isEqualTo("PROVISIONAL");
        ArgumentCaptor<ApprovalRequest> req = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequests).save(req.capture());
        assertThat(req.getValue().getStatus()).isEqualTo("BLOCKED");
        verify(approvalSteps, never()).save(any());
        verify(checkins).saveAndFlush(any());
    }

    @Test
    void photoOfAnotherUser_looksMissing() {
        when(livenessVerifier.scoreLiveness(any())).thenReturn(0.9);
        UUID foreign = freshPhoto(UUID.randomUUID(), Instant.now());
        assertThatThrownBy(() -> service.submit(tenantId, principal,
                new CheckInSubmitRequest(foreign, LAT, LNG, BigDecimal.TEN, null, List.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void stalePhoto_isRefused() {
        UUID old = freshPhoto(userId, Instant.now().minusSeconds(3600));
        assertThatThrownBy(() -> service.submit(tenantId, principal,
                new CheckInSubmitRequest(old, LAT, LNG, BigDecimal.TEN, null, List.of())))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("10 minutes");
    }

    @Test
    void reusedPhoto_isRefused() {
        UUID used = freshPhoto(userId, Instant.now());
        when(checkins.existsByTenantIdAndPhotoFileId(tenantId, used)).thenReturn(true);
        assertThatThrownBy(() -> service.submit(tenantId, principal,
                new CheckInSubmitRequest(used, LAT, LNG, BigDecimal.TEN, null, List.of())))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void invalidCoordinates_areA400NotADatabaseError() {
        assertThatThrownBy(() -> service.submit(tenantId, principal,
                new CheckInSubmitRequest(UUID.randomUUID(), new BigDecimal("95"), LNG, BigDecimal.TEN, null, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ review

    private StaffAttendanceCheckin reviewable(String status, UUID approvalId) {
        UUID checkinId = UUID.randomUUID();
        StaffAttendanceCheckin row = StaffAttendanceCheckin.builder()
                .tenantId(tenantId).staffAttendanceCheckinId(checkinId)
                .institutionId(institutionId).staffUserId(UUID.randomUUID())
                .photoFileId(UUID.randomUUID()).capturedAt(Instant.now())
                .latitude(LAT).longitude(LNG)
                .gpsAccuracyMeters(BigDecimal.TEN).distanceFromInstitutionM(BigDecimal.TEN)
                .livenessConfidence(new BigDecimal("0.1000")).status(status).approvalRequestId(approvalId)
                .createdBy(userId).updatedBy(userId).rowVersion(1).build();
        when(checkins.findByTenantIdAndStaffAttendanceCheckinId(tenantId, checkinId)).thenReturn(Optional.of(row));
        if (approvalId != null) {
            when(approvalRequests.findByIdTenantIdAndIdApprovalRequestId(tenantId, approvalId)).thenReturn(Optional.of(
                    ApprovalRequest.builder().id(new ApprovalRequestId(tenantId, approvalId))
                            .currentStepNo((short) 2).status("PENDING").build()));
        }
        return row;
    }

    @Test
    void approve_goesThroughTheSharedEngineAtTheCurrentStep() {
        UUID approvalId = UUID.randomUUID();
        StaffAttendanceCheckin row = reviewable("PROVISIONAL", approvalId);

        service.approve(tenantId, principal, row.getStaffAttendanceCheckinId(), "  ");

        ArgumentCaptor<DecideApprovalRequestDto> dto = ArgumentCaptor.forClass(DecideApprovalRequestDto.class);
        verify(approvalInbox).decideApproval(eq(tenantId), eq(approvalId), dto.capture(), eq(principal));
        assertThat(dto.getValue().decision()).isEqualTo("APPROVED");
        assertThat(dto.getValue().stepNo()).isEqualTo((short) 2);
        assertThat(dto.getValue().remark()).isNull();
    }

    @Test
    void reject_requiresReason_andNeverReachesTheEngineWithout() {
        StaffAttendanceCheckin row = reviewable("PROVISIONAL", UUID.randomUUID());
        assertThatThrownBy(() -> service.reject(tenantId, principal, row.getStaffAttendanceCheckinId(), null))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("reason");
        assertThatThrownBy(() -> service.reject(tenantId, principal, row.getStaffAttendanceCheckinId(), "   "))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(approvalInbox, never()).decideApproval(any(), any(), any(DecideApprovalRequestDto.class), any(TenantPrincipal.class));
    }

    @Test
    void reject_withReason_passesTrimmedRemarkToEngine() {
        UUID approvalId = UUID.randomUUID();
        StaffAttendanceCheckin row = reviewable("CHECK_IN_ONLY", approvalId);

        service.reject(tenantId, principal, row.getStaffAttendanceCheckinId(), "  Not on campus at that time ");

        ArgumentCaptor<DecideApprovalRequestDto> dto = ArgumentCaptor.forClass(DecideApprovalRequestDto.class);
        verify(approvalInbox).decideApproval(eq(tenantId), eq(approvalId), dto.capture(), eq(principal));
        assertThat(dto.getValue().decision()).isEqualTo("REJECTED");
        assertThat(dto.getValue().remark()).isEqualTo("Not on campus at that time");
    }

    @Test
    void reviewingAPresentRecord_isRefused() {
        StaffAttendanceCheckin row = reviewable("PRESENT", null);
        assertThatThrownBy(() -> service.approve(tenantId, principal, row.getStaffAttendanceCheckinId(), null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void reviewOutsideCallerInstitutionScope_looksMissing() {
        StaffAttendanceCheckin row = reviewable("PROVISIONAL", UUID.randomUUID());
        TenantContext.clear();
        TenantContext.set(tenantId, IsolationMode.SHARED, null, "LIST", Set.of(UUID.randomUUID()), "PRINCIPAL_COLLEGE", ScopeType.INSTITUTION);
        assertThatThrownBy(() -> service.approve(tenantId, principal, row.getStaffAttendanceCheckinId(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ day end

    @Test
    void expire_flipsToCheckInOnly_skipsUnreviewedStep_andEscalatesHodToPrincipal() {
        UUID approvalId = UUID.randomUUID();
        UUID systemActor = UUID.randomUUID();
        StaffAttendanceCheckin row = reviewable("PROVISIONAL", approvalId);
        when(checkins.findForUpdate(tenantId, row.getStaffAttendanceCheckinId())).thenReturn(Optional.of(row));
        ApprovalRequest request = ApprovalRequest.builder().id(new ApprovalRequestId(tenantId, approvalId))
                .currentStepNo((short) 1).status("PENDING").build();
        when(approvalRequests.findByIdTenantIdAndIdApprovalRequestId(tenantId, approvalId)).thenReturn(Optional.of(request));
        ApprovalStep hodStep = ApprovalStep.builder().id(new ApprovalStepId(tenantId, UUID.randomUUID()))
                .approvalRequestId(approvalId).stepNo((short) 1).approverRoleCode("HEAD_OF_DEPARTMENT")
                .decision("PENDING").build();
        when(approvalSteps.findByIdTenantIdAndApprovalRequestIdAndStepNo(tenantId, approvalId, (short) 1))
                .thenReturn(Optional.of(hodStep));

        boolean expired = service.expireProvisional(tenantId, row.getStaffAttendanceCheckinId(), systemActor, location, institution);

        assertThat(expired).isTrue();
        assertThat(row.getStatus()).isEqualTo("CHECK_IN_ONLY");
        assertThat(hodStep.getDecision()).isEqualTo("SKIPPED");
        assertThat(hodStep.getSkipReason()).isNotBlank();
        assertThat(hodStep.getDecidedAt()).isNotNull();
        ArgumentCaptor<ApprovalStep> steps = ArgumentCaptor.forClass(ApprovalStep.class);
        verify(approvalSteps, org.mockito.Mockito.times(2)).save(steps.capture());
        ApprovalStep next = steps.getAllValues().get(1);
        assertThat(next.getStepNo()).isEqualTo((short) 2);
        assertThat(next.getApproverRoleCode()).isEqualTo("PRINCIPAL_COLLEGE");
        assertThat(next.getDecision()).isEqualTo("PENDING");
        assertThat(request.getCurrentStepNo()).isEqualTo((short) 2);
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue().eventType()).isEqualTo(AuditEventType.STAFF_ATTENDANCE_CHECKIN_EXPIRED);
    }

    @Test
    void expire_alreadyDecidedRow_isLeftAlone() {
        StaffAttendanceCheckin row = reviewable("PRESENT", null);
        when(checkins.findForUpdate(tenantId, row.getStaffAttendanceCheckinId())).thenReturn(Optional.of(row));

        assertThat(service.expireProvisional(tenantId, row.getStaffAttendanceCheckinId(), UUID.randomUUID(), location, institution))
                .isFalse();
        verify(checkins, never()).save(any());
        verify(audit, never()).record(any(AuditEntry.class));
    }

    // ------------------------------------------------------------------ mapping

    @Test
    void captureColumnsAreNeverWrittenByAnUpdate() throws Exception {
        for (String field : List.of("institutionId", "staffUserId", "photoFileId", "capturedAt", "attendanceDate", "latitude", "longitude",
                "gpsAccuracyMeters", "distanceFromInstitutionM", "deviceType", "livenessConfidence", "createdBy")) {
            Field f = StaffAttendanceCheckin.class.getDeclaredField(field);
            assertThat(f.getAnnotation(Column.class).updatable()).as(field).isFalse();
        }
        for (String field : List.of("status", "approvalRequestId", "reviewedAt", "reviewedBy", "reviewNote", "updatedBy")) {
            Field f = StaffAttendanceCheckin.class.getDeclaredField(field);
            assertThat(f.getAnnotation(Column.class).updatable()).as(field).isTrue();
        }
    }

    @Test
    void haversine_sameCoordinate_isZero() {
        assertThat(StaffCheckInService.haversineMeters(LAT, LNG, LAT, LNG)).isEqualTo(new BigDecimal("0.00"));
    }
}
