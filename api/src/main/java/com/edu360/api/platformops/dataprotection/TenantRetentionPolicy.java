package com.edu360.api.platformops.dataprotection;

import java.time.Instant;
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
@Table(name = "tenant_retention_policy", schema = "platform")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRetentionPolicy {

    @Id
    @Column(name = "tenant_retention_policy_id", nullable = false)
    private UUID tenantRetentionPolicyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "data_class_code", nullable = false)
    private String dataClassCode;

    @Column(name = "retention_months")
    private Integer retentionMonths;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

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

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;
}
