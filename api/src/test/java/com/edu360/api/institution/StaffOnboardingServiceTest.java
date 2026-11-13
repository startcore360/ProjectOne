package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.identity.UserAccountService;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RoleAssignmentService;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.dto.CreateUserResponse;

class StaffOnboardingServiceTest {
    @Test
    void createsAccountLinksProfileAndStoresPersonalEmailAsUnverifiedAlias() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        RbacPrincipal actor = new RbacPrincipal(tenantId, actorId);
        UserAccountService accounts = mock(UserAccountService.class);
        StaffDirectoryService directory = mock(StaffDirectoryService.class);
        RoleAssignmentService roles = mock(RoleAssignmentService.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UserRoleAssignment active = mock(UserRoleAssignment.class);
        when(permissions.activeAssignment(actor)).thenReturn(Optional.of(active));
        when(active.getScopeType()).thenReturn(ScopeType.INSTITUTION);
        when(active.getInstitutionId()).thenReturn(institutionId);
        CreateUserResponse login = new CreateUserResponse(accountId, tenantId, UserType.STAFF,
                "Asha Rao", "asha@school.edu", null, UserAccountStatus.PENDING_ACTIVATION,
                "temporary", null, Instant.now());
        when(accounts.createUser(eq(actor), any())).thenReturn(login);
        StaffProfileResponse profile = new StaffProfileResponse(UUID.randomUUID(), accountId,
                "ST-001", "Asha Rao", null, null, null, null, null, "ACTIVE",
                LocalDate.of(2026, 11, 1), null, null, actorId, null, actorId, 1);
        when(directory.create(eq(actor), any(StaffProfileRequest.class), eq(accountId))).thenReturn(profile);

        StaffProfileRequest request = new StaffProfileRequest(null, "Asha Rao", null,
                "asha.personal@gmail.com", null, null, null, "ACTIVE",
                LocalDate.of(2026, 11, 1), null, "asha@school.edu", null);

        StaffOnboardingResponse result = new StaffOnboardingService(accounts, directory, roles,
                permissions, jdbc).onboard(actor, request);

        assertThat(result.staff().userAccountId()).isEqualTo(accountId);
        assertThat(result.login().temporaryPassword()).isEqualTo("temporary");
        assertThat(result.personalEmailVerificationRequired()).isTrue();
        verify(jdbc).update(any(String.class), eq(tenantId), any(UUID.class), eq(accountId),
                eq("asha.personal@gmail.com"), eq(actorId), eq(actorId));
        verify(roles, never()).assignRole(any(), any(), any());
    }

    @Test
    void rejectsSameOfficialAndPersonalEmailBeforeProvisioning() {
        UserAccountService accounts = mock(UserAccountService.class);
        StaffOnboardingService service = new StaffOnboardingService(accounts,
                mock(StaffDirectoryService.class), mock(RoleAssignmentService.class),
                mock(PermissionEvaluatorService.class), mock(JdbcTemplate.class));
        StaffProfileRequest request = new StaffProfileRequest(null, "Asha Rao", null,
                "asha@school.edu", null, null, null, "ACTIVE", null, null,
                "ASHA@SCHOOL.EDU", null);

        assertThatThrownBy(() -> service.onboard(new RbacPrincipal(UUID.randomUUID(), UUID.randomUUID()), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be different");
        verifyNoInteractions(accounts);
    }
}
