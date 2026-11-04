package com.edu360.api.platformops;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WHAT: a read-only view of platform.usage_metric_daily (sql/01_schema.sql line ~751) -
 * "Anonymous combined platform usage counts (logins, attendance marked, fees paid online);
 * opted-out tenants excluded" - i.e. exactly the "anonymous combined numbers only" shape
 * USAGE_COUNTS_VIEW's brief calls for, with no tenant id column at all (comment on the
 * `value` column: "Count, no tenant id"). metric_code is one of LOGINS, ATTENDANCE_MARKED,
 * FEES_PAID_ONLINE per the DB check constraint.
 */
@Entity
@Table(schema = "platform", name = "usage_metric_daily")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageMetricDaily {

    @Id
    @Column(name = "usage_metric_daily_id")
    private UUID usageMetricDailyId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "metric_code", nullable = false)
    private String metricCode;

    @Column(nullable = false)
    private long value;
}
