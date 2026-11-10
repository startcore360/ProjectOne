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
@Table(name = "tenant_role_template_action", schema = "app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantRoleTemplateAction {

    @EmbeddedId
    private TenantRoleTemplateActionId id;

    @Column(name = "tenant_role_template_id", nullable = false)
    private UUID tenantRoleTemplateId;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
