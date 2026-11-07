package com.edu360.api.rbac;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenant_role_template", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantRoleTemplate {

    @EmbeddedId
    private TenantRoleTemplateId id;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Column(name = "description")
    private String description;

    @Column(name = "base_role_code", nullable = false)
    private String baseRoleCode;

    @Column(name = "owner_level", nullable = false)
    @Builder.Default
    private String ownerLevel = "TENANT";

    @Column(name = "is_standard", nullable = false)
    private boolean standard;

    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}
