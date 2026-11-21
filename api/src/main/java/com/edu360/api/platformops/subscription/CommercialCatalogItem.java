package com.edu360.api.platformops.subscription;

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

@Entity
@Table(schema = "platform", name = "commercial_catalog_item")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialCatalogItem {
    @Id
    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Column(name = "item_code", nullable = false, length = 80)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 120)
    private String itemName;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    @Column(name = "pricing_basis", nullable = false)
    private String pricingBasis;

    @Column(name = "unit_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitAmount;

    @Column(name = "units_per_quantity", nullable = false)
    private Integer unitsPerQuantity;

    @Column(name = "billing_interval_months")
    private Integer billingIntervalMonths;

    @Column(name = "module_code")
    private String moduleCode;

    @Column(name = "description")
    private String description;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false)
    @Builder.Default
    private Integer rowVersion = 1;
}
