package com.edu360.api.platformops;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageMetricDailyRepository extends JpaRepository<UsageMetricDaily, UUID> {

    /** The latest recorded day for a given anonymous metric (LOGINS / ATTENDANCE_MARKED /
     *  FEES_PAID_ONLINE), if any has ever been computed. */
    Optional<UsageMetricDaily> findFirstByMetricCodeOrderByUsageDateDesc(String metricCode);
}
