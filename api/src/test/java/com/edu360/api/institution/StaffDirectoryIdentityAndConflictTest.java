package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
                eq(tenantId), eq(profileId), eq(institutionId), eq(null))).thenReturn(true);

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
}
