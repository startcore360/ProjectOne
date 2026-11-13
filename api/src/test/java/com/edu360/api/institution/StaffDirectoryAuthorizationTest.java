package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;

class StaffDirectoryAuthorizationTest {
    private record Fixture(StaffDirectoryService service, StaffProfileRepository profiles,
            PermissionEvaluatorService permissions, RbacPrincipal actor) {}

    private Fixture fixture() {
        StaffProfileRepository profiles = mock(StaffProfileRepository.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        UserRoleAssignment active = mock(UserRoleAssignment.class);
        when(active.getScopeType()).thenReturn(ScopeType.TENANT);
        when(permissions.activeAssignment(any())).thenReturn(Optional.of(active));
        StaffDirectoryService service = new StaffDirectoryService(profiles,
                mock(StaffAssignmentRepository.class), mock(StaffServiceRecordRepository.class),
                mock(StaffDocumentRepository.class), mock(InstitutionRepository.class),
                mock(DepartmentRepository.class), mock(InstitutionIdFormatService.class),
                permissions, mock(AuditLogService.class), mock(JdbcTemplate.class));
        return new Fixture(service, profiles, permissions,
                new RbacPrincipal(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void staffRecordViewDenialStopsBeforeQueryingProfiles() {
        Fixture f = fixture();
        doThrow(new UnauthorizedException("Denied")).when(f.permissions())
                .authorize(eq(f.actor()), eq("STAFF_RECORD_VIEW"), eq(RbacScope.tenant()), eq(RbacTarget.NONE));

        assertThatThrownBy(() -> f.service().list(f.actor(), null, 50))
                .isInstanceOf(UnauthorizedException.class);

        verify(f.permissions()).authorize(f.actor(), "STAFF_RECORD_VIEW", RbacScope.tenant(), RbacTarget.NONE);
        verifyNoInteractions(f.profiles());
    }

    @Test
    void staffRecordManageDenialStopsBeforeCreatingProfile() {
        Fixture f = fixture();
        doThrow(new UnauthorizedException("Denied")).when(f.permissions())
                .authorize(eq(f.actor()), eq("STAFF_RECORD_MANAGE"), eq(RbacScope.tenant()), eq(RbacTarget.NONE));

        assertThatThrownBy(() -> f.service().create(f.actor(), null))
                .isInstanceOf(UnauthorizedException.class);

        verify(f.permissions()).authorize(f.actor(), "STAFF_RECORD_MANAGE", RbacScope.tenant(), RbacTarget.NONE);
        verify(f.profiles(), never()).saveAndFlush(any());
    }
}
