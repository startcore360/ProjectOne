package com.edu360.api.rbac;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WHAT: the composite primary key of app.role_definition - (tenant_id, role_definition_id)
 * together, same pattern as identity.UserAccountId (see that class's javadoc for why tenant_id
 * is always part of the key, never just a foreign key column, across every app.* table).
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RoleDefinitionId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "role_definition_id", nullable = false)
    private UUID roleDefinitionId;
}
