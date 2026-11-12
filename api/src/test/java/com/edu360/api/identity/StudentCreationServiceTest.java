package com.edu360.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.identity.UserType;
import com.edu360.api.institution.*;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.dto.CreateUserResponse;
import com.edu360.api.identity.UserAccountStatus;

@ExtendWith(MockitoExtension.class)
class StudentCreationServiceTest {
    @Mock StudentProfileRepository students;
    @Mock InstitutionRepository institutions;
    @Mock AcademicSessionRepository sessions;
    @Mock SectionRepository sections;
    @Mock SchoolClassRepository schoolClasses;
    @Mock InstitutionIdFormatService idFormats;
    @Mock StudentEnrollmentService enrollments;
    @Mock UserAccountService accounts;
    @Mock AuditLogService audit;
    @Mock EntityManager entityManager;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID studentUserId = UUID.randomUUID();
    private final TenantPrincipal actor =
            new TenantPrincipal(tenantId, actorId, UserType.STAFF, "Registrar", false);
    private StudentCreationService service;

    @BeforeEach
    void setUp() {
        service = new StudentCreationService(students, institutions, sessions, sections,
                schoolClasses, idFormats, enrollments, accounts, audit);
        // EntityManager is injected by the container; the unit fixture supplies the same dependency.
        try {
            var field = StudentCreationService.class.getDeclaredField("entityManager");
            field.setAccessible(true);
            field.set(service, entityManager);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private void arrangeCollege() {
        when(institutions.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId))
                .thenReturn(Optional.of(Institution.builder()
                        .id(new InstitutionId(tenantId, institutionId))
                        .institutionType("COLLEGE").institutionCode("COL").institutionName("College")
                        .status("ACTIVE").build()));
        when(sections.findByTenantIdAndSectionId(tenantId, sectionId))
                .thenReturn(Optional.of(Section.builder().tenantId(tenantId).sectionId(sectionId)
                        .institutionId(institutionId).academicSessionId(sessionId)
                        .sectionName("A").status("ACTIVE").build()));
        when(sessions.findByTenantIdAndAcademicSessionId(tenantId, sessionId))
                .thenReturn(Optional.of(AcademicSession.builder().tenantId(tenantId)
                        .academicSessionId(sessionId).institutionId(institutionId)
                        .sessionLabel("2026-27").academicYear(2026)
                        .startDate(LocalDate.of(2026, 4, 1)).endDate(LocalDate.of(2027, 3, 31))
                        .status("ACTIVE").build()));
    }

    @Test
    void collegeDefaultsToStudentCodeLoginAndCreatesProfileAndEnrolment() {
        arrangeCollege();
        when(idFormats.nextStudentCode(tenantId, institutionId, 2026)).thenReturn("COL/2026/001");
        when(students.saveAndFlush(any(StudentProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accounts.createStudentAccount(any(RbacPrincipal.class), eq("A Student"),
                eq("COL/2026/001"), eq(institutionId)))
                .thenReturn(new CreateUserResponse(studentUserId, tenantId, UserType.STUDENT,
                        "A Student", null, null, UserAccountStatus.PENDING_ACTIVATION,
                        "temporary", null, null));

        StudentProfileResponse result = service.create(actor, new StudentCreateRequest(
                institutionId, "A Student", null, LocalDate.of(2026, 6, 1),
                sectionId, sessionId, null));

        assertThat(result.studentCode()).isEqualTo("COL/2026/001");
        assertThat(result.userAccountId()).isEqualTo(studentUserId);
        assertThat(result.temporaryPassword()).isEqualTo("temporary");
        verify(accounts).createStudentAccount(any(RbacPrincipal.class), eq("A Student"),
                eq("COL/2026/001"), eq(institutionId));
        verify(enrollments).enrolExistingStudent(eq(actor), any(UUID.class), eq(institutionId),
                eq(sectionId), eq(sessionId), isNull(), eq(LocalDate.of(2026, 6, 1)));
    }
    @Test
    void rejectsAdmissionOutsideSessionBeforeProvisioningAccount() {
        arrangeCollege();

        assertThatThrownBy(() -> service.create(actor, new StudentCreateRequest(
                institutionId, "A Student", null, LocalDate.of(2027, 4, 1),
                sectionId, sessionId, null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("admissionDate must fall within");
        verifyNoInteractions(accounts);
        verify(students, never()).saveAndFlush(any(StudentProfile.class));
        verifyNoInteractions(idFormats);
    }

    @Test
    void schoolClassLoginSwitchControlsStudentAccountCreation() {
        UUID classId = UUID.randomUUID();
        when(institutions.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId))
                .thenReturn(Optional.of(Institution.builder()
                        .id(new InstitutionId(tenantId, institutionId))
                        .institutionType("SCHOOL").institutionCode("SCH").institutionName("School")
                        .status("ACTIVE").build()));
        when(sections.findByTenantIdAndSectionId(tenantId, sectionId))
                .thenReturn(Optional.of(Section.builder().tenantId(tenantId).sectionId(sectionId)
                        .institutionId(institutionId).academicSessionId(sessionId).classId(classId)
                        .sectionName("A").status("ACTIVE").build()));
        when(schoolClasses.findByTenantIdAndClassId(tenantId, classId))
                .thenReturn(Optional.of(SchoolClass.builder().classId(classId).tenantId(tenantId)
                        .institutionId(institutionId).className("Class 8")
                        .studentLoginEnabled(false).status("ACTIVE").build()));
        when(sessions.findByTenantIdAndAcademicSessionId(tenantId, sessionId))
                .thenReturn(Optional.of(AcademicSession.builder().tenantId(tenantId)
                        .academicSessionId(sessionId).institutionId(institutionId)
                        .sessionLabel("2026-27").academicYear(2026)
                        .startDate(LocalDate.of(2026, 4, 1)).endDate(LocalDate.of(2027, 3, 31))
                        .status("ACTIVE").build()));
        when(idFormats.nextStudentCode(tenantId, institutionId, 2026)).thenReturn("SCH/2026/001");
        when(students.saveAndFlush(any(StudentProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StudentProfileResponse result = service.create(actor, new StudentCreateRequest(
                institutionId, "A Student", null, LocalDate.of(2026, 6, 1),
                sectionId, sessionId, null));

        assertThat(result.userAccountId()).isNull();
        assertThat(result.temporaryPassword()).isNull();
        assertThat(result.studentCode()).isEqualTo("SCH/2026/001");
        verifyNoInteractions(accounts);
    }

    @Test
    void explicitSchoolLoginRequestIsRejectedWhenClassSwitchIsDisabled() {
        UUID classId = UUID.randomUUID();
        when(institutions.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId))
                .thenReturn(Optional.of(Institution.builder()
                        .id(new InstitutionId(tenantId, institutionId))
                        .institutionType("SCHOOL").institutionCode("SCH").institutionName("School")
                        .status("ACTIVE").build()));
        when(sections.findByTenantIdAndSectionId(tenantId, sectionId))
                .thenReturn(Optional.of(Section.builder().tenantId(tenantId).sectionId(sectionId)
                        .institutionId(institutionId).academicSessionId(sessionId).classId(classId)
                        .sectionName("A").status("ACTIVE").build()));
        when(schoolClasses.findByTenantIdAndClassId(tenantId, classId))
                .thenReturn(Optional.of(SchoolClass.builder().classId(classId).tenantId(tenantId)
                        .institutionId(institutionId).className("Class 8")
                        .studentLoginEnabled(false).status("ACTIVE").build()));
        when(sessions.findByTenantIdAndAcademicSessionId(tenantId, sessionId))
                .thenReturn(Optional.of(AcademicSession.builder().tenantId(tenantId)
                        .academicSessionId(sessionId).institutionId(institutionId)
                        .sessionLabel("2026-27").academicYear(2026)
                        .startDate(LocalDate.of(2026, 4, 1)).endDate(LocalDate.of(2027, 3, 31))
                        .status("ACTIVE").build()));

        assertThatThrownBy(() -> service.create(actor, new StudentCreateRequest(
                institutionId, "A Student", null, LocalDate.of(2026, 6, 1),
                sectionId, sessionId, true)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Student login is disabled");
        verifyNoInteractions(accounts, idFormats);
    }

}
