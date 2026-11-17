package com.edu360.api.platformops.usage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface TenantUsageMonthlyRepository extends JpaRepository<TenantUsageMonthly, UUID> {
    List<TenantUsageMonthly> findByTenantIdOrderByUsageMonthDesc(UUID tenantId);
    List<TenantUsageMonthly> findByTenantIdAndUsageMonthBetweenOrderByUsageMonthAsc(UUID tenantId, LocalDate from, LocalDate to);
    Optional<TenantUsageMonthly> findByTenantIdAndUsageMonthAndMetricCode(UUID tenantId, LocalDate usageMonth, String metricCode);
    @Query(value = """
        SELECT COALESCE(sum(u.quantity),0) FROM platform.tenant_usage_monthly u
        JOIN platform.tenant t ON t.tenant_id=u.tenant_id
        WHERE t.analytics_opt_out=false AND u.usage_month=:month
          AND u.metric_code=:metricCode AND u.measured=true
        """, nativeQuery=true)
    BigDecimal sumMeasuredQuantityForAnalyticsTenants(@Param("month") LocalDate month, @Param("metricCode") String metricCode);
}
