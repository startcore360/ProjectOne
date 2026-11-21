package com.edu360.api.platformops.subscription;

import java.math.BigDecimal;
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

@Entity
@Table(schema = "platform", name = "tenant_subscription_addon")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSubscriptionAddon {
    @Id
    @Column(name = "subscription_addon_id", nullable = false)
    private UUID subscriptionAddonId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitAmount;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "REQUESTED";

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(name = "approved_by_platform_user_id")
    private UUID approvedByPlatformUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "request_reason")
    private String requestReason;

    @Column(name = "decision_reason")
    private String decisionReason;

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
