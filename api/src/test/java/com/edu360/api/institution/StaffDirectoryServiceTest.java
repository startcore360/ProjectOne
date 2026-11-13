package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import java.util.List;
import java.util.Optional;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.identity.StudentProfileRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;

class StaffDirectoryServiceTest {
    private StaffDirectoryService service() {
        return new StaffDirectoryService(mock(StaffProfileRepository.class),
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                mock(PermissionEvaluatorService.class), mock(AuditLogService.class),
                mock(JdbcTemplate.class));
    }

    @Test
    void profileUpdateRequiresIfMatchBeforeDatabaseAccess() {
        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        StaffDirectoryService service = new StaffDirectoryService(profiles,
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                mock(PermissionEvaluatorService.class), mock(AuditLogService.class),
                mock(JdbcTemplate.class));

        assertThatThrownBy(() -> service.update(null, UUID.randomUUID(), null, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("If-Match is required");
        verifyNoInteractions(profiles);
    }

    @Test
    void assignmentEndRequiresIfMatchBeforeDatabaseAccess() {
        StaffAssignmentRepository assignments = mock(StaffAssignmentRepository.class);
        StaffDirectoryService service = new StaffDirectoryService(mock(StaffProfileRepository.class),
                assignments, mock(StaffServiceRecordRepository.class), mock(StaffDocumentRepository.class),
                mock(InstitutionRepository.class), mock(DepartmentRepository.class),
                mock(InstitutionIdFormatService.class), mock(PermissionEvaluatorService.class),
                mock(AuditLogService.class), mock(JdbcTemplate.class));

        assertThatThrownBy(() -> service.endAssignment(null, UUID.randomUUID(), UUID.randomUUID(), "invalid", null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("numeric ETag");
        verifyNoInteractions(assignments);
    }
    @Test
    void sensitiveStaffListViewIsAuditedOnlyForManagePermission() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        StaffProfile profile = new StaffProfile();
        profile.setId(new StaffProfileId(tenantId, profileId));
        profile.setStaffCode("ST-001");
        profile.setFullName("Staff Member");
        profile.setPersonalEmail("private@example.test");
        profile.setNationalIdLast4("1234");

        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        AuditLogService audit = mock(AuditLogService.class);
        UserRoleAssignment active = mock(UserRoleAssignment.class);
        when(active.getScopeType()).thenReturn(ScopeType.TENANT);
        when(permissions.activeAssignment(any(RbacPrincipal.class))).thenReturn(Optional.of(active));
        when(permissions.can(any(RbacPrincipal.class), eq("STAFF_RECORD_MANAGE"),
                eq(RbacScope.tenant()), eq(RbacTarget.NONE))).thenReturn(PermissionDecision.allow());
        when(profiles.searchTenantStaff(tenantId, null, null, null, 50)).thenReturn(List.of(profile));

        StaffDirectoryService service = new StaffDirectoryService(profiles,
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                permissions, audit, mock(JdbcTemplate.class));

        service.list(new RbacPrincipal(tenantId, actorId), null, 50);

        verify(audit).record(any(AuditEntry.class));
        verify(permissions).authorize(any(RbacPrincipal.class), eq("STAFF_RECORD_VIEW"),
                eq(RbacScope.tenant()), eq(RbacTarget.NONE));
    }

    @Test
    void viewOnlyStaffListDoesNotWriteSensitiveViewAudit() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        StaffProfile profile = new StaffProfile();
        profile.setId(new StaffProfileId(tenantId, profileId));
        profile.setStaffCode("ST-002");
        profile.setFullName("Staff Member");

        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        AuditLogService audit = mock(AuditLogService.class);
        UserRoleAssignment active = mock(UserRoleAssignment.class);
        when(active.getScopeType()).thenReturn(ScopeType.TENANT);
        when(permissions.activeAssignment(any(RbacPrincipal.class))).thenReturn(Optional.of(active));
        when(permissions.can(any(RbacPrincipal.class), eq("STAFF_RECORD_MANAGE"),
                eq(RbacScope.tenant()), eq(RbacTarget.NONE))).thenReturn(PermissionDecision.deny("DENIED", "No manage permission"));
        when(profiles.searchTenantStaff(tenantId, null, null, null, 50)).thenReturn(List.of(profile));

        StaffDirectoryService service = new StaffDirectoryService(profiles,
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                permissions, audit, mock(JdbcTemplate.class));

        service.list(new RbacPrincipal(tenantId, actorId), null, 50);

        verify(audit, never()).record(any(AuditEntry.class));
    }

}
