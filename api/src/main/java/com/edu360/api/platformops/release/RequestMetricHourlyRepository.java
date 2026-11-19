package com.edu360.api.platformops.release;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface RequestMetricHourlyRepository extends JpaRepository<RequestMetricHourly, UUID> {

    @Modifying
    @Transactional(transactionManager = "platformTransactionManager")
    @Query(value = """
            INSERT INTO platform.request_metric_hourly
                (request_metric_hourly_id, bucket_start, route_template, status_class, request_count,
                 created_at, created_by, updated_at, updated_by, row_version)
            VALUES (:id, :bucketStart, :routeTemplate, '5XX', :requestCount,
                    now(), :systemActor, now(), :systemActor, 1)
            ON CONFLICT (bucket_start, route_template, status_class)
            DO UPDATE SET request_count = platform.request_metric_hourly.request_count + EXCLUDED.request_count,
                          updated_by = EXCLUDED.updated_by
            """, nativeQuery = true)
    int upsertFiveXx(@Param("id") UUID id,
                     @Param("bucketStart") Instant bucketStart,
                     @Param("routeTemplate") String routeTemplate,
                     @Param("requestCount") long requestCount,
                     @Param("systemActor") UUID systemActor);

    @Query(value = """
            SELECT COALESCE(SUM(request_count), 0)
              FROM platform.request_metric_hourly
             WHERE status_class = '5XX'
               AND bucket_start >= :fromAt
               AND bucket_start < :toAt
            """, nativeQuery = true)
    long sumFiveXxBetween(@Param("fromAt") Instant fromAt, @Param("toAt") Instant toAt);
}
