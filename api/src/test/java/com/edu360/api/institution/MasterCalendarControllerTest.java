package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.institution.dto.AcademicSessionResponse;
import com.edu360.api.institution.dto.CreateAcademicSessionRequest;
import com.edu360.api.institution.dto.CreateMasterCalendarEventRequest;
import com.edu360.api.institution.dto.MasterCalendarEventResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@ExtendWith(MockitoExtension.class)
class MasterCalendarControllerTest {

    @Mock private AcademicSessionRepository academicSessionRepository;
    @Mock private AcademicCalendarEventRepository academicCalendarEventRepository;
    @Mock private PermissionEvaluatorService permissionEvaluatorService;
    @Mock private AuditLogService auditLogService;
    @Mock private Authentication authentication;

    private MasterCalendarService masterCalendarService;
    private MasterCalendarController masterCalendarController;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        masterCalendarService = new MasterCalendarService(
                academicSessionRepository,
                academicCalendarEventRepository,
                permissionEvaluatorService,
                auditLogService);
        masterCalendarController = new MasterCalendarController(masterCalendarService);
    }

    private void mockPrincipal(UUID tenantId) {
        TenantPrincipal principal = new TenantPrincipal(tenantId, USER_ID, UserType.STAFF, "University Admin", false);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    // ----------------------------------------------------------------- ALLOW
    @Test
    void createAcademicSession_allowsUniversityAdmin() {
        mockPrincipal(TENANT_ID);

        when(academicSessionRepository.existsByTenantIdAndInstitutionIdIsNullAndSessionLabel(TENANT_ID, "2026-27"))
                .thenReturn(false);
        when(academicSessionRepository.save(any(AcademicSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateAcademicSessionRequest request = new CreateAcademicSessionRequest(
                null, "2026-27", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31), 2026, "ACTIVE");

        ResponseEntity<AcademicSessionResponse> response = masterCalendarController.createAcademicSession(
                TENANT_ID, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().sessionLabel()).isEqualTo("2026-27");
        assertThat(response.getBody().academicYear()).isEqualTo(2026);
        assertThat(response.getBody().status()).isEqualTo("ACTIVE");

        verify(permissionEvaluatorService).authorize(any(), eq("CALENDAR_MASTER_SET"), eq(RbacScope.tenant()), eq(RbacTarget.NONE));
        verify(auditLogService).record(eq(AuditEventType.ACADEMIC_SESSION_CREATED), any(), eq("2026-27"), any(), any());
    }

    @Test
    void createMasterCalendarEvent_allowsSemesterDates() {
        mockPrincipal(TENANT_ID);

        AcademicSession session = AcademicSession.builder()
                .academicSessionId(SESSION_ID)
                .tenantId(TENANT_ID)
                .institutionId(null)
                .sessionLabel("2026-27")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2027, 5, 31))
                .academicYear(2026)
                .status("ACTIVE")
                .build();
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(TENANT_ID, SESSION_ID))
                .thenReturn(Optional.of(session));
        when(academicCalendarEventRepository.save(any(AcademicCalendarEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateMasterCalendarEventRequest request = new CreateMasterCalendarEventRequest(
                SESSION_ID, "SEMESTER_DATES", "Odd Semester 2026", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 11, 30));

        ResponseEntity<MasterCalendarEventResponse> response = masterCalendarController.createMasterCalendarEvent(
                TENANT_ID, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().eventType()).isEqualTo("SEMESTER_DATES");
        assertThat(response.getBody().sourceLevel()).isEqualTo("UNIVERSITY");
        assertThat(response.getBody().institutionId()).isNull();
        assertThat(response.getBody().title()).isEqualTo("Odd Semester 2026");

        verify(permissionEvaluatorService).authorize(any(), eq("CALENDAR_MASTER_SET"), eq(RbacScope.tenant()), eq(RbacTarget.NONE));
        verify(auditLogService).record(eq(AuditEventType.MASTER_CALENDAR_EVENT_CREATED), any(), eq("Odd Semester 2026"), any(), any());
    }

    @Test
    void createMasterCalendarEvent_allowsUniversityHoliday() {
        mockPrincipal(TENANT_ID);

        AcademicSession session = AcademicSession.builder()
                .academicSessionId(SESSION_ID)
                .tenantId(TENANT_ID)
                .institutionId(null)
                .sessionLabel("2026-27")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2027, 5, 31))
                .academicYear(2026)
                .status("ACTIVE")
                .build();
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(TENANT_ID, SESSION_ID))
                .thenReturn(Optional.of(session));
        when(academicCalendarEventRepository.save(any(AcademicCalendarEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateMasterCalendarEventRequest request = new CreateMasterCalendarEventRequest(
                SESSION_ID, "UNIVERSITY_HOLIDAY", "Diwali Break", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5));

        ResponseEntity<MasterCalendarEventResponse> response = masterCalendarController.createMasterCalendarEvent(
                TENANT_ID, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().eventType()).isEqualTo("UNIVERSITY_HOLIDAY");
    }

    @Test
    void listAcademicSessions_and_listMasterEvents() {
        mockPrincipal(TENANT_ID);

        AcademicSession session = AcademicSession.builder()
                .academicSessionId(SESSION_ID)
                .tenantId(TENANT_ID)
                .institutionId(null)
                .sessionLabel("2026-27")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2027, 5, 31))
                .academicYear(2026)
                .status("ACTIVE")
                .build();
        when(academicSessionRepository.findByTenantIdOrderByAcademicYearDesc(TENANT_ID))
                .thenReturn(List.of(session));

        ResponseEntity<List<AcademicSessionResponse>> sessionListResponse = masterCalendarController.listAcademicSessions(
                TENANT_ID, null, authentication);
        assertThat(sessionListResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionListResponse.getBody()).hasSize(1);

        AcademicCalendarEvent event = new AcademicCalendarEvent();
        event.setAcademicCalendarEventId(UUID.randomUUID());
        event.setTenantId(TENANT_ID);
        event.setAcademicSessionId(SESSION_ID);
        event.setSourceLevel("UNIVERSITY");
        event.setEventType("SEMESTER_DATES");
        event.setTitle("Odd Semester");
        event.setStartDate(LocalDate.of(2026, 7, 1));
        event.setEndDate(LocalDate.of(2026, 11, 30));

        when(academicCalendarEventRepository.findByTenantIdAndAcademicSessionIdAndSourceLevelOrderByStartDateAsc(
                TENANT_ID, SESSION_ID, "UNIVERSITY"))
                .thenReturn(List.of(event));

        ResponseEntity<List<MasterCalendarEventResponse>> eventListResponse = masterCalendarController.listMasterCalendarEvents(
                TENANT_ID, SESSION_ID, authentication);
        assertThat(eventListResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eventListResponse.getBody()).hasSize(1);
    }

    // ----------------------------------------------------------------- DENY
    @Test
    void createAcademicSession_deniesWhenPermissionEvaluatorDenies() {
        mockPrincipal(TENANT_ID);

        doThrow(new UnauthorizedException("Access denied"))
                .when(permissionEvaluatorService)
                .authorize(any(), eq("CALENDAR_MASTER_SET"), eq(RbacScope.tenant()), eq(RbacTarget.NONE));

        CreateAcademicSessionRequest request = new CreateAcademicSessionRequest(
                null, "2026-27", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31), 2026, "ACTIVE");

        assertThrows(UnauthorizedException.class, () ->
                masterCalendarController.createAcademicSession(TENANT_ID, request, authentication));

        verify(academicSessionRepository, never()).save(any());
    }

    // ----------------------------------------------------------------- SCOPE
    @Test
    void endpoints_rejectCrossTenantPrincipal() {
        UUID crossTenantId = UUID.randomUUID();
        mockPrincipal(TENANT_ID);

        CreateAcademicSessionRequest request = new CreateAcademicSessionRequest(
                null, "2026-27", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31), 2026, "ACTIVE");

        assertThrows(UnauthorizedException.class, () ->
                masterCalendarController.createAcademicSession(crossTenantId, request, authentication));
    }

    // ----------------------------------------------------------------- GUARD
    @Test
    void createAcademicSession_rejectsEndDateBeforeStartDate() {
        mockPrincipal(TENANT_ID);

        CreateAcademicSessionRequest request = new CreateAcademicSessionRequest(
                null, "2026-27", LocalDate.of(2027, 6, 1), LocalDate.of(2026, 5, 31), 2026, "ACTIVE");

        assertThrows(BusinessRuleViolationException.class, () ->
                masterCalendarController.createAcademicSession(TENANT_ID, request, authentication));

        verify(academicSessionRepository, never()).save(any());
    }

    @Test
    void createAcademicSession_rejectsDuplicateLabel() {
        mockPrincipal(TENANT_ID);

        when(academicSessionRepository.existsByTenantIdAndInstitutionIdIsNullAndSessionLabel(TENANT_ID, "2026-27"))
                .thenReturn(true);

        CreateAcademicSessionRequest request = new CreateAcademicSessionRequest(
                null, "2026-27", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31), 2026, "ACTIVE");

        assertThrows(DuplicateResourceException.class, () ->
                masterCalendarController.createAcademicSession(TENANT_ID, request, authentication));

        verify(academicSessionRepository, never()).save(any());
    }

    @Test
    void createMasterCalendarEvent_rejectsLocalEventType() {
        mockPrincipal(TENANT_ID);

        AcademicSession session = AcademicSession.builder()
                .academicSessionId(SESSION_ID)
                .tenantId(TENANT_ID)
                .institutionId(null)
                .sessionLabel("2026-27")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2027, 5, 31))
                .academicYear(2026)
                .status("ACTIVE")
                .build();
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(TENANT_ID, SESSION_ID))
                .thenReturn(Optional.of(session));

        CreateMasterCalendarEventRequest request = new CreateMasterCalendarEventRequest(
                SESSION_ID, "LOCAL_HOLIDAY", "Local Holiday", LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15));

        assertThrows(BusinessRuleViolationException.class, () ->
                masterCalendarController.createMasterCalendarEvent(TENANT_ID, request, authentication));

        verify(academicCalendarEventRepository, never()).save(any());
    }

    @Test
    void createMasterCalendarEvent_rejectsMissingSession() {
        mockPrincipal(TENANT_ID);

        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(TENANT_ID, SESSION_ID))
                .thenReturn(Optional.empty());

        CreateMasterCalendarEventRequest request = new CreateMasterCalendarEventRequest(
                SESSION_ID, "SEMESTER_DATES", "Odd Semester", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 11, 30));

        assertThrows(ResourceNotFoundException.class, () ->
                masterCalendarController.createMasterCalendarEvent(TENANT_ID, request, authentication));

        verify(academicCalendarEventRepository, never()).save(any());
    }
}
