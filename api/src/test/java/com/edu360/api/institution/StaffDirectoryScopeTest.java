package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;

class StaffDirectoryScopeTest {
    @Test
    void staffRecordViewUsesOnlyActiveInstitutionScope() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        RbacPrincipal actor = new RbacPrincipal(tenantId, userId);
        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        UserRoleAssignment active = mock(UserRoleAssignment.class);
        when(active.getScopeType()).thenReturn(ScopeType.INSTITUTION);
        when(active.getInstitutionId()).thenReturn(institutionId);
        when(permissions.activeAssignment(actor)).thenReturn(Optional.of(active));
        doThrow(new AccessDeniedException("ACTION_SCOPE_MISMATCH"))
                .when(permissions).authorize(actor, "STAFF_RECORD_VIEW",
                        RbacScope.institution(institutionId), RbacTarget.NONE);
        StaffDirectoryService service = new StaffDirectoryService(profiles,
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                permissions, mock(AuditLogService.class), mock(JdbcTemplate.class));

        assertThatThrownBy(() -> service.list(actor, null, 50))
                .isInstanceOf(AccessDeniedException.class);
        verify(permissions).authorize(actor, "STAFF_RECORD_VIEW",
                RbacScope.institution(institutionId), RbacTarget.NONE);
        verifyNoInteractions(profiles);
    }
}
