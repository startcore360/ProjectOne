package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.CalendarEventRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@ExtendWith(MockitoExtension.class)
class CalendarEventServiceTest {

    @Mock private AcademicCalendarEventRepository academicCalendarEventRepository;
    @Mock private PermissionEvaluatorService permissionEvaluatorService;
    @Mock private AcademicSessionRepository academicSessionRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID INSTITUTION_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    private final RbacPrincipal principal = new RbacPrincipal(TENANT_ID, USER_ID);

    private CalendarEventService service() {
        return new CalendarEventService(academicCalendarEventRepository, permissionEvaluatorService, academicSessionRepository);
    }

    @Test
    void addLocalEvent_savesAnInstitutionScopedLocalHolidayWhenAuthorized() {
        AcademicSession session = new AcademicSession(); session.setAcademicSessionId(SESSION_ID); session.setTenantId(TENANT_ID); session.setInstitutionId(INSTITUTION_ID);
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(TENANT_ID, SESSION_ID)).thenReturn(java.util.Optional.of(session));
        when(academicCalendarEventRepository.save(any(AcademicCalendarEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CalendarEventRequest(
                INSTITUTION_ID, SESSION_ID, "LOCAL_HOLIDAY", "Founder's Day", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10));
        var response = service().addLocalEvent(principal, request);

        assertThat(response.sourceLevel()).isEqualTo("INSTITUTION");
        assertThat(response.eventType()).isEqualTo("LOCAL_HOLIDAY");
        assertThat(response.institutionId()).isEqualTo(INSTITUTION_ID);
        org.mockito.Mockito.verify(permissionEvaluatorService).authorize(
                principal, "CALENDAR_LOCAL_SET", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);
    }

    @Test
    void addLocalHoliday_refusesOverlapWithTypedUniversityExamDay() {
        AcademicSession session = new AcademicSession();
        session.setAcademicSessionId(SESSION_ID); session.setTenantId(TENANT_ID); session.setInstitutionId(null);
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(TENANT_ID, SESSION_ID))
                .thenReturn(java.util.Optional.of(session));
        AcademicCalendarEvent exam = new AcademicCalendarEvent();
        exam.setEventType("EXAM_DAY"); exam.setTitle("Mid-term assessment");
        exam.setStartDate(LocalDate.of(2026, 11, 10)); exam.setEndDate(LocalDate.of(2026, 11, 12));
        when(academicCalendarEventRepository.findByTenantIdAndSourceLevelOrderByStartDateAsc(TENANT_ID, "UNIVERSITY")).thenReturn(List.of(exam));
        when(academicCalendarEventRepository.findByTenantIdAndInstitutionIdOrderByStartDateAsc(TENANT_ID, INSTITUTION_ID)).thenReturn(List.of());
        var request = new CalendarEventRequest(INSTITUTION_ID, SESSION_ID, "LOCAL_HOLIDAY", "Local closure",
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13));
        assertThatThrownBy(() -> service().addLocalEvent(principal, request))
                .isInstanceOfSatisfying(com.edu360.api.exception.BusinessRuleViolationException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("HOLIDAY_EXAM_CONFLICT"));
        org.mockito.Mockito.verify(academicCalendarEventRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void addLocalEvent_allowsTypedLocalExamDay() {
        AcademicSession session = new AcademicSession();
        session.setAcademicSessionId(SESSION_ID); session.setTenantId(TENANT_ID); session.setInstitutionId(INSTITUTION_ID);
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(TENANT_ID, SESSION_ID)).thenReturn(java.util.Optional.of(session));
        when(academicCalendarEventRepository.save(any(AcademicCalendarEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        var request = new CalendarEventRequest(INSTITUTION_ID, SESSION_ID, "EXAM_DAY", "Unit test",
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10));
        assertThat(service().addLocalEvent(principal, request).eventType()).isEqualTo("EXAM_DAY");
    }

    @Test
    void addLocalEvent_rejectsSemesterDatesBecauseThatIsTheSeparateCalendarMasterSetStory() {
        var request = new CalendarEventRequest(
                INSTITUTION_ID, SESSION_ID, "SEMESTER_DATES", "Odd semester", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 1));

        assertThatThrownBy(() -> service().addLocalEvent(principal, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CALENDAR_MASTER_SET");

        org.mockito.Mockito.verify(academicCalendarEventRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void addLocalEvent_deniesWhenEvaluatorDenies() {
        org.mockito.Mockito.doThrow(new UnauthorizedException("denied"))
                .when(permissionEvaluatorService)
                .authorize(principal, "CALENDAR_LOCAL_SET", RbacScope.institution(INSTITUTION_ID), RbacTarget.NONE);

        var request = new CalendarEventRequest(
                INSTITUTION_ID, SESSION_ID, "LOCAL_EVENT", "Sports day", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1));

        assertThatThrownBy(() -> service().addLocalEvent(principal, request)).isInstanceOf(UnauthorizedException.class);
        org.mockito.Mockito.verify(academicCalendarEventRepository, org.mockito.Mockito.never()).save(any());
    }


    @Test
    void addLocalEvent_rejectsSessionFromAnotherInstitution() {
        AcademicSession foreign = new AcademicSession(); foreign.setAcademicSessionId(SESSION_ID);
        foreign.setTenantId(TENANT_ID); foreign.setInstitutionId(UUID.randomUUID());
        when(academicSessionRepository.findByTenantIdAndAcademicSessionId(TENANT_ID, SESSION_ID)).thenReturn(java.util.Optional.of(foreign));
        var request = new CalendarEventRequest(INSTITUTION_ID, SESSION_ID, "LOCAL_EVENT", "Sports day",
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1));
        assertThatThrownBy(() -> service().addLocalEvent(principal, request))
                .isInstanceOf(com.edu360.api.exception.ResourceNotFoundException.class);
        org.mockito.Mockito.verify(academicCalendarEventRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void listLocalEvents_authorizesThenReturnsInstitutionRows() {
        AcademicCalendarEvent row = new AcademicCalendarEvent();
        row.setAcademicCalendarEventId(UUID.randomUUID());
        row.setInstitutionId(INSTITUTION_ID);
        row.setAcademicSessionId(SESSION_ID);
        row.setSourceLevel("INSTITUTION");
        row.setEventType("LOCAL_EVENT");
        row.setTitle("Annual day");
        row.setStartDate(LocalDate.of(2026, 12, 20));
        row.setEndDate(LocalDate.of(2026, 12, 20));
        when(academicCalendarEventRepository.findByTenantIdAndInstitutionIdOrderByStartDateAsc(TENANT_ID, INSTITUTION_ID))
                .thenReturn(List.of(row));

        var result = service().listLocalEvents(principal, INSTITUTION_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Annual day");
    }
}
