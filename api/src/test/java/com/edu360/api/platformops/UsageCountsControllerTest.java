package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.dto.UsageCountsResponse;
import com.edu360.api.tenant.TenantRepository;

@ExtendWith(MockitoExtension.class)
class UsageCountsControllerTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UsageMetricDailyRepository usageMetricDailyRepository;
    @Mock private com.edu360.api.platformops.usage.TenantUsageMonthlyRepository tenantUsageMonthlyRepository;

    private final PlatformPermissionService platformPermissionService = com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());

    private UsageCountsController controller() {
        return new UsageCountsController(platformPermissionService, tenantRepository, usageMetricDailyRepository, tenantUsageMonthlyRepository);
    }

    private Authentication authFor(PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(UUID.randomUUID(), role, "po@edu360.test", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void usageCounts_returnsMaterializedActiveUsersWithoutTheOldGapText() {
        when(tenantRepository.count()).thenReturn(7L);
        when(tenantUsageMonthlyRepository.sumMeasuredQuantityForAnalyticsTenants(
                LocalDate.now(java.time.ZoneOffset.UTC).withDayOfMonth(1), "ACTIVE_USERS"))
                .thenReturn(java.math.BigDecimal.valueOf(9));
        UsageMetricDaily loginsRow = UsageMetricDaily.builder()
                .usageMetricDailyId(UUID.randomUUID())
                .metricCode("LOGINS")
                .value(42L)
                .usageDate(LocalDate.of(2026, 11, 1))
                .build();
        when(usageMetricDailyRepository.findFirstByMetricCodeOrderByUsageDateDesc("LOGINS"))
                .thenReturn(Optional.of(loginsRow));
        when(usageMetricDailyRepository.findFirstByMetricCodeOrderByUsageDateDesc("ATTENDANCE_MARKED"))
                .thenReturn(Optional.empty());
        when(usageMetricDailyRepository.findFirstByMetricCodeOrderByUsageDateDesc("FEES_PAID_ONLINE"))
                .thenReturn(Optional.empty());

        ResponseEntity<UsageCountsResponse> response = controller().usageCounts(authFor(PlatformRole.PRODUCT_OWNER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UsageCountsResponse body = response.getBody();
        assertThat(body.totalTenantCount()).isEqualTo(7L);
        assertThat(body.metrics()).hasSize(4);
        assertThat(body.metrics().get(0).metricCode()).isEqualTo("LOGINS");
        assertThat(body.metrics().get(0).value()).isEqualTo(42L);
        assertThat(body.metrics().get(3).metricCode()).isEqualTo("ACTIVE_USERS");
        assertThat(body.metrics().get(3).value()).isEqualTo(9L);
    }

    @Test
    void usageCounts_deniesNonPlatformRole() {
        assertThatThrownBy(() -> controller().usageCounts(authFor(PlatformRole.SUPPORT_MANAGER)))
                .isInstanceOf(UnauthorizedException.class);
    }
}
