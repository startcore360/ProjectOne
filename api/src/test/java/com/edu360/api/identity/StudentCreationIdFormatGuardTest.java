package com.edu360.api.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.institution.*;
import com.edu360.api.rbac.PermissionEvaluatorService;

class StudentCreationIdFormatGuardTest {
    @Test
    void studentCreationWithoutConfiguredInstitutionFormatReturns422BeforeAccountOrProfileCreation() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        TenantPrincipal actor = new TenantPrincipal(tenantId, actorId, UserType.STAFF, "Registrar", false);

        StudentProfileRepository students = mock(StudentProfileRepository.class);
        InstitutionRepository institutions = mock(InstitutionRepository.class);
        AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
        SectionRepository sections = mock(SectionRepository.class);
        SchoolClassRepository schoolClasses = mock(SchoolClassRepository.class);
        InstitutionIdFormatService idFormats = mock(InstitutionIdFormatService.class);
        StudentEnrollmentService enrollments = mock(StudentEnrollmentService.class);
        UserAccountService accounts = mock(UserAccountService.class);
        AuditLogService audit = mock(AuditLogService.class);

        when(institutions.findByIdTenantIdAndIdInstitutionId(tenantId, institutionId))
                .thenReturn(Optional.of(Institution.builder().id(new InstitutionId(tenantId, institutionId))
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
        when(idFormats.nextStudentCode(tenantId, institutionId, 2026))
                .thenThrow(new BusinessRuleViolationException("STUDENT_ID_FORMAT_NOT_SET",
                        "Set the Student ID format in Institution settings before creating students."));

        StudentCreationService service = new StudentCreationService(students, institutions, sessions,
                sections, schoolClasses, idFormats, enrollments, accounts, audit,
                mock(PermissionEvaluatorService.class));
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        assertThatThrownBy(() -> service.create(actor, new StudentCreateRequest(institutionId,
                "A Student", null, LocalDate.of(2026, 6, 1), sectionId, sessionId, null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((BusinessRuleViolationException) error).getErrorCode())
                        .isEqualTo("STUDENT_ID_FORMAT_NOT_SET"));

        verifyNoInteractions(accounts, enrollments);
        verify(students, never()).saveAndFlush(any(StudentProfile.class));
    }
}
