package com.edu360.api.rbac;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * No delete method anywhere on this interface, deliberately: Addendum rule 17 says roles with
 * historical assignments or records must not be hard-deleted, only soft-deleted/archived (a
 * save() that flips status to RETIRED). Extends the bare marker {@link Repository}, not
 * CrudRepository/JpaRepository, specifically so a DELETE capability can never be added back by
 * accident through an inherited default method.
 */
public interface RoleDefinitionRepository extends Repository<RoleDefinition, RoleDefinitionId> {

    RoleDefinition save(RoleDefinition roleDefinition);

    Optional<RoleDefinition> findById(RoleDefinitionId id);

    List<RoleDefinition> findByIdTenantId(UUID tenantId);

    boolean existsByIdTenantIdAndRoleCode(UUID tenantId, String roleCode);

    Optional<RoleDefinition> findByIdTenantIdAndRoleCode(UUID tenantId, String roleCode);
}
