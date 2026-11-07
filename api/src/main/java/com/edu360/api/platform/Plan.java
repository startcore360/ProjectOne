package com.edu360.api.platform;

import java.math.BigDecimal;
import java.time.Instant;
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

/**
 * Subscription plan from platform.plan.
 * Defines subscription tier limits and pricing for tenants.
 */
@Entity
@Table(schema = "platform", name = "plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "plan_name", nullable = false)
    private String planName;

    @Column(name = "pricing_type", nullable = false)
    private String pricingType;

    @Column(name = "price_amount", nullable = false)
    private BigDecimal priceAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "user_limit")
    private Integer userLimit;

    @Column(name = "storage_limit_gb")
    private Integer storageLimitGb;

    @Column(name = "sms_limit")
    private Integer smsLimit;

    @Column(name = "api_limit")
    private Integer apiLimit;

    @Column(name = "max_institutions")
    private Integer maxInstitutions;

    @Column(name = "status", nullable = false)
    private String status;

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
