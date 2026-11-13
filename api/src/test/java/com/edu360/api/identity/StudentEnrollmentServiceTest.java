package com.edu360.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.institution.AcademicSession;
import com.edu360.api.institution.AcademicSessionRepository;
import com.edu360.api.institution.Section;
import com.edu360.api.institution.SectionRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.identity.UserType;

@ExtendWith(MockitoExtension.class)
class StudentEnrollmentServiceTest {
    @Mock private StudentEnrollmentRepository enrollments;
    @Mock private StudentProfileRepository students;
    @Mock private SectionRepository sections;
    @Mock private AcademicSessionRepository sessions;
    @Mock private PermissionEvaluatorService permissions;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final LocalDate sessionStart = LocalDate.of(2026, 4, 1);
    private final LocalDate sessionEnd = LocalDate.of(2027, 3, 31);
    private final TenantPrincipal actor =
            new TenantPrincipal(tenantId, userId, UserType.STAFF, "Registrar", false);
    private StudentEnrollmentService service;

    @BeforeEach
    void setUp() {
        service = new StudentEnrollmentService(enrollments, students, sections, sessions, permissions);
    }

    private void arrangeValidRecords() {
        when(students.findById(studentId)).thenReturn(Optional.of(
                new StudentProfile(studentId, tenantId, institutionId, UUID.randomUUID(), "SCH-001", "ACTIVE")));
        when(sections.findByTenantIdAndSectionId(tenantId, sectionId)).thenReturn(Optional.of(
                Section.builder().sectionId(sectionId).tenantId(tenantId).institutionId(institutionId)
                        .academicSessionId(sessionId).sectionName("A").status("ACTIVE").build()));
        when(sessions.findByTenantIdAndAcademicSessionId(tenantId, sessionId)).thenReturn(Optional.of(
                AcademicSession.builder().academicSessionId(sessionId).tenantId(tenantId)
                        .institutionId(institutionId).sessionLabel("2026-27")
                        .startDate(sessionStart).endDate(sessionEnd).academicYear(2026).status("ACTIVE").build()));
    }

    @Test
    void enrolsActiveStudentIntoMatchingActiveSectionAndSession() {
        arrangeValidRecords();
        when(enrollments.save(any(StudentEnrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDate startedOn = LocalDate.of(2026, 6, 15);

        StudentEnrollment result = service.enrolExistingStudent(actor, studentId, institutionId,
                sectionId, sessionId, "  R-01  ", startedOn);

        assertThat(result.getId().getTenantId()).isEqualTo(tenantId);
        assertThat(result.getStudentProfileId()).isEqualTo(studentId);
        assertThat(result.getInstitutionId()).isEqualTo(institutionId);
        assertThat(result.getSectionId()).isEqualTo(sectionId);
        assertThat(result.getAcademicSessionId()).isEqualTo(sessionId);
        assertThat(result.getRollNumber()).isEqualTo("R-01");
        assertThat(result.getOutcome()).isEqualTo("IN_PROGRESS");
        assertThat(result.getStartedOn()).isEqualTo(startedOn);
        verify(permissions).authorize(any(), org.mockito.ArgumentMatchers.eq("USER_CREATE"),
                org.mockito.ArgumentMatchers.eq(com.edu360.api.rbac.RbacScope.institution(institutionId)),
                org.mockito.ArgumentMatchers.eq(com.edu360.api.rbac.RbacTarget.NONE));
    }

    @Test
    void rejectsEnrollmentOutsideAcademicSession() {
        arrangeValidRecords();

        assertThatThrownBy(() -> service.enrolExistingStudent(actor, studentId, institutionId,
                sectionId, sessionId, null, sessionStart.minusDays(1)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("startedOn must fall within");
        verify(enrollments, never()).save(any());
    }


    @Test
    void rejectsInactiveAcademicSession() {
        arrangeValidRecords();
        when(sessions.findByTenantIdAndAcademicSessionId(tenantId, sessionId)).thenReturn(Optional.of(
                AcademicSession.builder().academicSessionId(sessionId).tenantId(tenantId)
                        .institutionId(institutionId).sessionLabel("2026-27")
                        .startDate(sessionStart).endDate(sessionEnd).academicYear(2026).status("INACTIVE").build()));

        assertThatThrownBy(() -> service.enrolExistingStudent(actor, studentId, institutionId,
                sectionId, sessionId, null, LocalDate.of(2026, 6, 15)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("active academic session");
        verify(enrollments, never()).save(any());
    }

    @Test
    void rejectsAcademicSessionWithoutDateBounds() {
        arrangeValidRecords();
        when(sessions.findByTenantIdAndAcademicSessionId(tenantId, sessionId)).thenReturn(Optional.of(
                AcademicSession.builder().academicSessionId(sessionId).tenantId(tenantId)
                        .institutionId(institutionId).sessionLabel("2026-27")
                        .academicYear(2026).status("ACTIVE").build()));

        assertThatThrownBy(() -> service.enrolExistingStudent(actor, studentId, institutionId,
                sectionId, sessionId, null, LocalDate.of(2026, 6, 15)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("start and end dates");
        verify(enrollments, never()).save(any());
    }

    @Test
    void rejectsStudentFromAnotherTenant() {
        when(students.findById(studentId)).thenReturn(Optional.of(
                new StudentProfile(studentId, UUID.randomUUID(), institutionId, UUID.randomUUID(), "OTHER-01", "ACTIVE")));

        assertThatThrownBy(() -> service.enrolExistingStudent(actor, studentId, institutionId,
                sectionId, sessionId, null, LocalDate.of(2026, 6, 15)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(sections, never()).findByTenantIdAndSectionId(any(), any());
        verify(enrollments, never()).save(any());
    }

    @Test
    void rejectsInactiveStudentBeforeSectionLookup() {
        when(students.findById(studentId)).thenReturn(Optional.of(
                new StudentProfile(studentId, tenantId, institutionId, UUID.randomUUID(), "SCH-001", "INACTIVE")));

        assertThatThrownBy(() -> service.enrolExistingStudent(actor, studentId, institutionId,
                sectionId, sessionId, null, LocalDate.of(2026, 6, 15)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Only an active student");
        verify(sections, never()).findByTenantIdAndSectionId(any(), any());
        verify(enrollments, never()).save(any());
    }
}
