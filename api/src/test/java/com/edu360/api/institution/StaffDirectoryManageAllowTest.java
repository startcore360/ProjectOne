package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.audit.AuditEntry;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;

class StaffDirectoryManageAllowTest {
    @Test
    void authorizedTenantManagerCanCreateStaffProfileWithHrSuppliedCode() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RbacPrincipal actor = new RbacPrincipal(tenantId, userId);
        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        InstitutionIdFormatService idFormats = mock(InstitutionIdFormatService.class);
        AuditLogService audit = mock(AuditLogService.class);
        EntityManager entityManager = mock(EntityManager.class);
        UserRoleAssignment active = mock(UserRoleAssignment.class);
        when(active.getScopeType()).thenReturn(ScopeType.TENANT);
        when(permissions.activeAssignment(actor)).thenReturn(Optional.of(active));
        when(idFormats.resolveStaffCode(tenantId, null, "HR-001", 2026)).thenReturn("HR-001");
        when(profiles.saveAndFlush(any(StaffProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StaffDirectoryService service = new StaffDirectoryService(profiles,
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), idFormats, permissions, audit, mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        StaffProfileResponse response = service.create(actor, new StaffProfileRequest(
                "HR-001", "Asha Rao", null, null, null, null, null, "ACTIVE",
                LocalDate.of(2026, 1, 1), null));

        assertThat(response.staffCode()).isEqualTo("HR-001");
        assertThat(response.fullName()).isEqualTo("Asha Rao");
        verify(permissions).authorize(actor, "STAFF_RECORD_MANAGE", RbacScope.tenant(), RbacTarget.NONE);
        verify(audit).record(any(AuditEntry.class));
    }
}
