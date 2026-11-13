package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;

class StaffDirectoryAuthorizationAllowTest {
    @Test
    void tenantScopedStaffRecordViewAllowsAuthorizedList() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RbacPrincipal actor = new RbacPrincipal(tenantId, userId);
        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        UserRoleAssignment active = mock(UserRoleAssignment.class);
        when(active.getScopeType()).thenReturn(ScopeType.TENANT);
        when(permissions.activeAssignment(actor)).thenReturn(Optional.of(active));
        when(profiles.searchTenantStaff(tenantId, null, null, null, 50)).thenReturn(List.of());

        StaffDirectoryService service = new StaffDirectoryService(profiles,
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                permissions, mock(AuditLogService.class), mock(JdbcTemplate.class));

        assertThat(service.list(actor, null, 50).items()).isEmpty();
        verify(permissions).authorize(actor, "STAFF_RECORD_VIEW", RbacScope.tenant(), RbacTarget.NONE);
        verify(profiles).searchTenantStaff(tenantId, null, null, null, 50);
    }
}
