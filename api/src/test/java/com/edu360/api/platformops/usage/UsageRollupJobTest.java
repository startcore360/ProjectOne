package com.edu360.api.platformops.usage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.platformops.UsageMetricDaily;
import com.edu360.api.platformops.UsageMetricDailyRepository;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import com.edu360.api.tenant.TenantState;

class UsageRollupJobTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantUsageMonthlyRepository monthly = mock(TenantUsageMonthlyRepository.class);
    private final UsageMetricDailyRepository platformDaily = mock(UsageMetricDailyRepository.class);
    private final JdbcTemplate tenantJdbc = mock(JdbcTemplate.class);

    @AfterEach
    void clearTenantContext() { TenantContext.clear(); }

    @Test
    void rollsUpTenantMetricsAndClearsContextAfterEachTenant() {
        Tenant tenant = mock(Tenant.class);
        when(tenant.getTenantId()).thenReturn(UUID.randomUUID());
        when(tenant.getIsolationMode()).thenReturn(IsolationMode.SHARED);
        when(tenant.getState()).thenReturn(TenantState.ACTIVE);
        when(tenant.isAnalyticsOptOut()).thenReturn(false);
        when(tenants.findAll()).thenReturn(List.of(tenant));
        doAnswer(invocation -> {
            org.assertj.core.api.Assertions.assertThat(TenantContext.currentInstitutionScope()).isEqualTo("ALL");
            return 0L;
        }).when(tenantJdbc).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        doAnswer(invocation -> {
            org.assertj.core.api.Assertions.assertThat(TenantContext.currentInstitutionScope()).isEqualTo("ALL");
            return 0L;
        }).when(tenantJdbc).queryForObject(anyString(), eq(Long.class));
        when(monthly.findByTenantIdAndUsageMonthAndMetricCode(any(UUID.class), any(LocalDate.class), anyString()))
                .thenReturn(Optional.empty());
        when(platformDaily.findByUsageDateAndMetricCode(any(LocalDate.class), anyString()))
                .thenReturn(Optional.empty());

        new UsageRollupJob(tenants, monthly, platformDaily, tenantJdbc).rollup(LocalDate.of(2026, 11, 17));

        verify(tenantJdbc, never()).update(anyString(), any(Object[].class));
        // UsageRollupJob.saveMonthly is called once per tracked metric (LOGINS, ATTENDANCE_MARKED,
        // FEES_PAID_ONLINE, SMS_SENT, STORAGE_GB, ACTIVE_USERS, ACTIVE_STUDENTS, ACTIVE_STAFF,
        // ACTIVE_COLLEGES, ACTIVE_SCHOOLS, BILLABLE_ACTIVE_STUDENTS) - 11 metrics, not 6.
        verify(monthly, times(11)).save(any(TenantUsageMonthly.class));
        verify(platformDaily, times(3)).save(any(UsageMetricDaily.class));
        org.assertj.core.api.Assertions.assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void optedOutTenantStillGetsMonthlyUsageButIsExcludedFromCombinedRows() {
        Tenant tenant = mock(Tenant.class);
        when(tenant.getTenantId()).thenReturn(UUID.randomUUID());
        when(tenant.getIsolationMode()).thenReturn(IsolationMode.SHARED);
        when(tenant.getState()).thenReturn(TenantState.TRIAL);
        when(tenant.isAnalyticsOptOut()).thenReturn(true);
        when(tenants.findAll()).thenReturn(List.of(tenant));
        doReturn(0L).when(tenantJdbc).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        doReturn(0L).when(tenantJdbc).queryForObject(anyString(), eq(Long.class));
        when(monthly.findByTenantIdAndUsageMonthAndMetricCode(any(UUID.class), any(LocalDate.class), anyString()))
                .thenReturn(Optional.empty());

        new UsageRollupJob(tenants, monthly, platformDaily, tenantJdbc).rollup(LocalDate.of(2026, 11, 17));

        verify(monthly, times(11)).save(any(TenantUsageMonthly.class));
        verify(platformDaily, never()).save(any(UsageMetricDaily.class));
    }
}
