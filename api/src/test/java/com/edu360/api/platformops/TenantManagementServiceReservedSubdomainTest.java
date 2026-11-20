package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.edu360.api.config.keycloak.KeycloakAdminClient;
import com.edu360.api.exception.ApiException;
import com.edu360.api.rbac.RoleActionRepository;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.StandardRoleActionRepository;
import com.edu360.api.rbac.StandardRoleRepository;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserIdentityLinkRepository;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.institution.InstitutionRepository;
import com.edu360.api.institution.LeaveApproverSettingRepository;
import com.edu360.api.platform.PlanRepository;
import com.edu360.api.platformops.billing.InvoiceRepository;
import com.edu360.api.platformops.dto.TenantCreateRequest;
import com.edu360.api.platformops.role.PlatformAuditWriter;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.TenantRepository;

class TenantManagementServiceReservedSubdomainTest {

    @Test
    void platformOwnedHostnameCannotBeCreatedAsTenant() {
        TenantManagementService service = new TenantManagementService(
                mock(TenantRepository.class),
                mock(PlanRepository.class),
                mock(KeycloakAdminClient.class),
                mock(LeaveApproverSettingRepository.class),
                mock(InstitutionRepository.class),
                mock(UserAccountRepository.class),
                mock(UserIdentityLinkRepository.class),
                mock(RoleDefinitionRepository.class),
                mock(StandardRoleRepository.class),
                mock(StandardRoleActionRepository.class),
                mock(RoleActionRepository.class),
                mock(UserRoleAssignmentRepository.class),
                mock(InvoiceRepository.class),
                mock(PlatformAuditWriter.class));

        TenantCreateRequest request = TenantCreateRequest.builder()
                .tenantName("Reserved Host Test")
                .tenantType("STANDALONE_COLLEGE")
                .planId(UUID.randomUUID())
                .subdomain("platform")
                .isolationMode(IsolationMode.SHARED)
                .retentionMonths(36)
                .backupRetentionDays(90)
                .firstAdminName("Test Admin")
                .firstAdminEmail("admin@example.test")
                .firstAdminMobile("+919876543210")
                .build();

        assertThatThrownBy(() -> service.createTenant(request, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("reserved");
    }
}
