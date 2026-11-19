package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditFailureException;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.identity.StudentProfileRepository;
import com.edu360.api.rbac.PermissionDecision;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
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
    void sensitiveStaffListViewIsAuditedOnlyForManagePermissionWithBatch() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID profileId1 = UUID.randomUUID();
        UUID profileId2 = UUID.randomUUID();

        StaffProfile profile1 = new StaffProfile();
        profile1.setId(new StaffProfileId(tenantId, profileId1));
        profile1.setStaffCode("ST-001");
        profile1.setFullName("Staff One");

        StaffProfile profile2 = new StaffProfile();
        profile2.setId(new StaffProfileId(tenantId, profileId2));
        profile2.setStaffCode("ST-002");
        profile2.setFullName("Staff Two");

        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        AuditLogService audit = mock(AuditLogService.class);
        UserRoleAssignment active = mock(UserRoleAssignment.class);
        when(active.getScopeType()).thenReturn(ScopeType.TENANT);
        when(permissions.activeAssignment(any(RbacPrincipal.class))).thenReturn(Optional.of(active));
        when(permissions.can(any(RbacPrincipal.class), eq("STAFF_RECORD_MANAGE"),
                eq(RbacScope.tenant()), eq(RbacTarget.NONE))).thenReturn(PermissionDecision.allow());
        when(profiles.searchTenantStaff(tenantId, null, null, null, 50)).thenReturn(List.of(profile1, profile2));

        StaffDirectoryService service = new StaffDirectoryService(profiles,
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                permissions, audit, mock(JdbcTemplate.class));

        service.list(new RbacPrincipal(tenantId, actorId), null, 50);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuditEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(audit).recordAll(captor.capture());
        List<AuditEntry> entries = captor.getValue();
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).entityId()).isEqualTo(profileId1);
        assertThat(entries.get(0).actionCode()).isEqualTo("STAFF_RECORD_VIEW");
        assertThat(entries.get(0).entityType()).isEqualTo("STAFF_PROFILE");
        assertThat(entries.get(1).entityId()).isEqualTo(profileId2);

        verify(audit, never()).record(any(AuditEntry.class));
        verify(permissions).authorize(any(RbacPrincipal.class), eq("STAFF_RECORD_VIEW"),
                eq(RbacScope.tenant()), eq(RbacTarget.NONE));
    }

    @Test
    void emptyStaffListDoesNotCallAudit() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        AuditLogService audit = mock(AuditLogService.class);
        UserRoleAssignment active = mock(UserRoleAssignment.class);
        when(active.getScopeType()).thenReturn(ScopeType.TENANT);
        when(permissions.activeAssignment(any(RbacPrincipal.class))).thenReturn(Optional.of(active));
        when(permissions.can(any(RbacPrincipal.class), eq("STAFF_RECORD_MANAGE"),
                eq(RbacScope.tenant()), eq(RbacTarget.NONE))).thenReturn(PermissionDecision.allow());
        when(profiles.searchTenantStaff(tenantId, null, null, null, 50)).thenReturn(List.of());

        StaffDirectoryService service = new StaffDirectoryService(profiles,
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                permissions, audit, mock(JdbcTemplate.class));

        service.list(new RbacPrincipal(tenantId, actorId), null, 50);

        verify(audit, never()).recordAll(any());
        verify(audit, never()).record(any(AuditEntry.class));
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

        verify(audit, never()).recordAll(any());
        verify(audit, never()).record(any(AuditEntry.class));
    }

    @Test
    void staffListAuditFailureThrowsException() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        StaffProfile profile = new StaffProfile();
        profile.setId(new StaffProfileId(tenantId, profileId));
        profile.setStaffCode("ST-003");
        profile.setFullName("Staff Member");

        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        AuditLogService audit = mock(AuditLogService.class);
        UserRoleAssignment active = mock(UserRoleAssignment.class);
        when(active.getScopeType()).thenReturn(ScopeType.TENANT);
        when(permissions.activeAssignment(any(RbacPrincipal.class))).thenReturn(Optional.of(active));
        when(permissions.can(any(RbacPrincipal.class), eq("STAFF_RECORD_MANAGE"),
                eq(RbacScope.tenant()), eq(RbacTarget.NONE))).thenReturn(PermissionDecision.allow());
        when(profiles.searchTenantStaff(tenantId, null, null, null, 50)).thenReturn(List.of(profile));
        doThrow(new AuditFailureException("Audit failed", new RuntimeException())).when(audit).recordAll(any());

        StaffDirectoryService service = new StaffDirectoryService(profiles,
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                permissions, audit, mock(JdbcTemplate.class));

        assertThrows(AuditFailureException.class, () ->
                service.list(new RbacPrincipal(tenantId, actorId), null, 50));
    }

}
