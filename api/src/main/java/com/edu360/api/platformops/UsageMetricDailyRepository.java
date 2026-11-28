package com.edu360.api.platformops;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UsageMetricDailyRepository extends JpaRepository<UsageMetricDaily, UUID> {
    Optional<UsageMetricDaily> findFirstByMetricCodeOrderByUsageDateDesc(String metricCode);
    Optional<UsageMetricDaily> findByUsageDateAndMetricCode(LocalDate usageDate, String metricCode);
}
