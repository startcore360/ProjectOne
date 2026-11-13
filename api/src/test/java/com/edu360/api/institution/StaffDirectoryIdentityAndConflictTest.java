package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;

class StaffDirectoryIdentityAndConflictTest {

    @Test
    void ensureStaffAssignmentIsIdempotentForExistingProfileAndAssignment() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        StaffAssignmentRepository assignments = mock(StaffAssignmentRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StaffProfile existing = new StaffProfile();
        existing.setId(new StaffProfileId(tenantId, profileId));
        existing.setUserAccountId(userId);

        when(jdbc.queryForObject(anyString(), eq(String.class), eq(tenantId), eq(userId)))
                .thenReturn("Existing Teacher");
        when(profiles.findByIdTenantIdAndUserAccountId(tenantId, userId)).thenReturn(Optional.of(existing));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class),
                eq(tenantId), eq(profileId), eq(institutionId), eq(null), eq(null), any(LocalDate.class))).thenReturn(true);

        StaffDirectoryService service = new StaffDirectoryService(profiles, assignments,
                mock(StaffServiceRecordRepository.class), mock(StaffDocumentRepository.class),
                mock(InstitutionRepository.class), mock(DepartmentRepository.class),
                mock(InstitutionIdFormatService.class), mock(PermissionEvaluatorService.class),
                mock(AuditLogService.class), jdbc);

        service.ensureStaffAssignment(tenantId, userId, institutionId, null, actorId, "Professor");

        verify(profiles, never()).saveAndFlush(any(StaffProfile.class));
        verify(assignments, never()).saveAndFlush(any(StaffAssignment.class));
    }

    @Test
    void endingAssignmentIsBlockedWhenPublishedTimetableDependsOnIt() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        StaffAssignmentRepository assignments = mock(StaffAssignmentRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StaffAssignment assignment = new StaffAssignment();
        assignment.setStaffAssignmentId(assignmentId);
        assignment.setTenantId(tenantId);
        assignment.setStaffProfileId(staffId);
        assignment.setInstitutionId(institutionId);
        assignment.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        assignment.setStatus("ACTIVE");

        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(tenantId), eq(assignmentId), eq(staffId)))
                .thenReturn(1);
        when(assignments.findByTenantIdAndStaffAssignmentIdAndStaffProfileId(tenantId, assignmentId, staffId))
                .thenReturn(Optional.of(assignment));
        doReturn(List.of("PUBLISHED_TIMETABLE_SLOT:" + slotId))
                .when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        StaffDirectoryService service = new StaffDirectoryService(mock(StaffProfileRepository.class),
                assignments, mock(StaffServiceRecordRepository.class), mock(StaffDocumentRepository.class),
                mock(InstitutionRepository.class), mock(DepartmentRepository.class),
                mock(InstitutionIdFormatService.class), mock(PermissionEvaluatorService.class),
                mock(AuditLogService.class), jdbc);

        assertThatThrownBy(() -> service.endAssignment(new RbacPrincipal(tenantId, userId), staffId,
                assignmentId, "\"1\"", new EndStaffAssignmentRequest(LocalDate.of(2026, 11, 13))))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("STAFF_ASSIGNMENT_IN_USE")
                .hasMessageContaining(slotId.toString());

        verify(assignments, never()).saveAndFlush(any(StaffAssignment.class));
    }

    @Test
    void ensureStaffAssignmentCreatesLinkedProfileAndScopedAssignmentWhenMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        StaffAssignmentRepository assignments = mock(StaffAssignmentRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InstitutionIdFormatService formats = mock(InstitutionIdFormatService.class);
        jakarta.persistence.EntityManager entityManager = mock(jakarta.persistence.EntityManager.class);

        when(jdbc.queryForObject(anyString(), eq(String.class), eq(tenantId), eq(userId)))
                .thenReturn("New Teacher");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class),
                eq(tenantId), any(UUID.class), eq(institutionId), eq(departmentId), eq(null), any(LocalDate.class))).thenReturn(false);
        when(formats.resolveStaffCode(eq(tenantId), eq(institutionId), eq(null), anyInt()))
                .thenReturn("STF-2026-0001");
        when(profiles.saveAndFlush(any(StaffProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assignments.saveAndFlush(any(StaffAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        StaffDirectoryService service = new StaffDirectoryService(profiles, assignments,
                mock(StaffServiceRecordRepository.class), mock(StaffDocumentRepository.class),
                mock(InstitutionRepository.class), mock(DepartmentRepository.class), formats,
                mock(PermissionEvaluatorService.class), mock(AuditLogService.class), jdbc);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "entityManager", entityManager);

        service.ensureStaffAssignment(tenantId, userId, institutionId, departmentId, actorId, "Subject Teacher");

        org.mockito.ArgumentCaptor<StaffProfile> profileCaptor =
                org.mockito.ArgumentCaptor.forClass(StaffProfile.class);
        verify(profiles).saveAndFlush(profileCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(profileCaptor.getValue().getUserAccountId()).isEqualTo(userId);
        org.assertj.core.api.Assertions.assertThat(profileCaptor.getValue().getFullName()).isEqualTo("New Teacher");

        org.mockito.ArgumentCaptor<StaffAssignment> assignmentCaptor =
                org.mockito.ArgumentCaptor.forClass(StaffAssignment.class);
        verify(assignments).saveAndFlush(assignmentCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(assignmentCaptor.getValue().getInstitutionId()).isEqualTo(institutionId);
        org.assertj.core.api.Assertions.assertThat(assignmentCaptor.getValue().getDepartmentId()).isEqualTo(departmentId);
        org.assertj.core.api.Assertions.assertThat(assignmentCaptor.getValue().getDesignation()).isEqualTo("Subject Teacher");
    }

    @Test
    void teacherLookupAuthorizesInstitutionScopeBeforeReturningPickerResults() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        StaffDirectoryService service = new StaffDirectoryService(mock(StaffProfileRepository.class),
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                permissions, mock(AuditLogService.class), jdbc);

        service.teacherLookup(new RbacPrincipal(tenantId, userId), institutionId, null, "math", 20);

        verify(permissions).authorize(eq(new RbacPrincipal(tenantId, userId)),
                eq("SUBJECT_ALLOCATE"), eq(com.edu360.api.rbac.RbacScope.institution(institutionId)),
                eq(com.edu360.api.rbac.RbacTarget.NONE));
    }

    @Test
    void teacherLookupAuthorizesDepartmentScope() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        StaffDirectoryService service = new StaffDirectoryService(mock(StaffProfileRepository.class),
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                permissions, mock(AuditLogService.class), jdbc);

        service.teacherLookup(new RbacPrincipal(tenantId, userId), institutionId, departmentId, null, 20);

        verify(permissions).authorize(eq(new RbacPrincipal(tenantId, userId)),
                eq("SUBJECT_ALLOCATE"), eq(com.edu360.api.rbac.RbacScope.department(institutionId, departmentId)),
                eq(com.edu360.api.rbac.RbacTarget.NONE));
    }
}
