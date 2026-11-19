package com.edu360.api.platformops.release;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hourly aggregate of HTTP 5xx responses; stores route templates only, never request paths. */
@Entity
@Table(schema = "platform", name = "request_metric_hourly",
        uniqueConstraints = @UniqueConstraint(name = "uq_request_metric_hourly_bucket_route_status",
                columnNames = {"bucket_start", "route_template", "status_class"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestMetricHourly {
    @Id
    @Column(name = "request_metric_hourly_id", nullable = false)
    private UUID requestMetricHourlyId;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "route_template", nullable = false, length = 300)
    private String routeTemplate;

    @Column(name = "status_class", nullable = false, length = 3)
    private String statusClass;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;
}
