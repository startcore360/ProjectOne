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
@Table(schema = "platform", name = "plan_component")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanComponent {
    @Id
    @Column(name = "plan_component_id", nullable = false)
    private UUID planComponentId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Column(name = "included_quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal includedQuantity;

    @Column(name = "unit_amount_override", precision = 14, scale = 2)
    private BigDecimal unitAmountOverride;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

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
