package com.edu360.api.platformops.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenant_usage_monthly", schema = "platform")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUsageMonthly {

    @Id
    @Column(name = "tenant_usage_monthly_id", nullable = false)
    private UUID tenantUsageMonthlyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "usage_month", nullable = false)
    private LocalDate usageMonth;

    @Column(name = "metric_code", nullable = false)
    private String metricCode;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "is_final", nullable = false)
    private boolean isFinal;

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
