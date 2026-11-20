package com.edu360.api.platformops.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformAccessLookup;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.PlatformTestSupport;
import com.edu360.api.platformops.usage.TenantUsageMonthlyRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

@ExtendWith(MockitoExtension.class)
class TenantUsageControllerTest {
    @Mock private TenantUsageMonthlyRepository usage;
    @Mock private TenantRepository tenants;

    private final PlatformPermissionService permissions =
            PlatformTestSupport.permissions(PlatformAccessLookup.standardRolesOnly());

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken(
                new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PRODUCT_OWNER, "po@edu360.test", false),
                null, java.util.List.of());
    }

    @Test
    void analyticsOptOutReturnsNoTenantUsageRows() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = org.mockito.Mockito.mock(Tenant.class);
        when(tenant.isAnalyticsOptOut()).thenReturn(true);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        var response = new TenantUsageController(usage, tenants, permissions).getTenantUsage(tenantId, auth());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verify(usage, never()).findByTenantIdOrderByUsageMonthDesc(tenantId);
    }
}
