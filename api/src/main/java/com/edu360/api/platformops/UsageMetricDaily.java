package com.edu360.api.platformops;
import java.time.Instant;
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
import lombok.Setter;
@Entity @Table(schema="platform", name="usage_metric_daily")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UsageMetricDaily {
    @Id @Column(name="usage_metric_daily_id") private UUID usageMetricDailyId;
    @Column(name="usage_date", nullable=false) private LocalDate usageDate;
    @Column(name="metric_code", nullable=false) private String metricCode;
    @Column(nullable=false) private long value;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="created_by", nullable=false) private UUID createdBy;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Column(name="updated_by", nullable=false) private UUID updatedBy;
    @Column(name="row_version", nullable=false) private Integer rowVersion;
}
