package com.edu360.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RoleAssignmentService;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

class UserAccountStudentProvisioningTest {
    @Test
    void studentAccountUsesStudentCodeIdentityAndNoEmail() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserIdentityLinkRepository links = mock(UserIdentityLinkRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        PlanRepository plans = mock(PlanRepository.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
        RoleAssignmentService roles = mock(RoleAssignmentService.class);
        AuditLogService audit = mock(AuditLogService.class);

        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        Tenant tenant = mock(Tenant.class);
        when(tenant.getKeycloakRealm()).thenReturn("tenant-realm");
        when(tenant.getPlanId()).thenReturn(null);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(accounts.countByIdTenantIdAndStatusNot(tenantId, UserAccountStatus.DEACTIVATED)).thenReturn(0L);
        when(keycloak.provisionStudentUser("tenant-realm", "Asha Rao", "COL-2026-001", "Edu360@Abc234", true))
                .thenReturn("kc-student-1");

        UserAccountService service = new UserAccountService(accounts, links, tenants, plans,
                permissions, keycloak, roles, audit);
        RbacPrincipal actor = new RbacPrincipal(tenantId, actorId);

        var response = service.createStudentAccount(actor, "Asha Rao", "COL-2026-001", institutionId);

        assertThat(response.userType()).isEqualTo(UserType.STUDENT);
        assertThat(response.email()).isNull();
        assertThat(response.initialRole()).isEqualTo("STUDENT");
        verify(keycloak).provisionStudentUser(eq("tenant-realm"), eq("Asha Rao"),
                eq("COL-2026-001"), any(String.class), eq(true));

        ArgumentCaptor<UserAccount> account = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).save(account.capture());
        assertThat(account.getValue().getEmail()).isNull();
        assertThat(account.getValue().getUserType()).isEqualTo(UserType.STUDENT);

        ArgumentCaptor<UserIdentityLink> identity = ArgumentCaptor.forClass(UserIdentityLink.class);
        verify(links).save(identity.capture());
        assertThat(identity.getValue().getUsernameKind()).isEqualTo(UsernameKind.STUDENT_CODE);
        assertThat(identity.getValue().getIdentityRealm()).isEqualTo("tenant-realm");
        assertThat(identity.getValue().getIdentitySubject()).isEqualTo("kc-student-1");
        verify(roles).assignRole(eq(actor), eq(response.userAccountId()), any());
    }
}
