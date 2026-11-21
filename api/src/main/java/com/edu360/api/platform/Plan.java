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

    @Column(name = "max_colleges")
    private Integer maxColleges;

    @Column(name = "max_schools")
    private Integer maxSchools;

    @Column(name = "max_students")
    private Integer maxStudents;

    @Column(name = "max_staff")
    private Integer maxStaff;

    @Column(name = "setup_fee_amount", precision = 14, scale = 2)
    private BigDecimal setupFeeAmount;

    @Column(name = "discount_type")
    private String discountType;

    @Column(name = "discount_value", precision = 14, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "contract_duration_months")
    private Integer contractDurationMonths;

    @Column(name = "grace_period_days", nullable = false)
    @Builder.Default
    private Integer gracePeriodDays = 15;

    @Column(name = "renewal_mode", nullable = false)
    @Builder.Default
    private String renewalMode = "AUTOMATIC";

    @Column(name = "pricing_engine_version", nullable = false)
    @Builder.Default
    private String pricingEngineVersion = "LEGACY";

    @Column(name = "custom_domain_allowed", nullable = false)
    private boolean customDomainAllowed;

    @Column(name = "branding_colors_allowed", nullable = false)
    private boolean brandingColorsAllowed;

    @Column(name = "billing_cycle_months", nullable = false)
    private Integer billingCycleMonths;

    @Column(name = "sms_unit_amount", precision = 14, scale = 2)
    private BigDecimal smsUnitAmount;

    @Column(name = "storage_unit_amount_per_gb", precision = 14, scale = 2)
    private BigDecimal storageUnitAmountPerGb;

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
