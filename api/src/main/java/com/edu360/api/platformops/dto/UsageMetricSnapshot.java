package com.edu360.api.platformops.dto;

import java.time.LocalDate;

/** The latest recorded value for one anonymous combined usage metric (platform.usage_metric_daily
 *  .metric_code: LOGINS / ATTENDANCE_MARKED / FEES_PAID_ONLINE). value/asOfDate are both null
 *  when no row has ever been computed for that metric yet - never fabricated as zero. */
public record UsageMetricSnapshot(String metricCode, Long value, LocalDate asOfDate) {
}
