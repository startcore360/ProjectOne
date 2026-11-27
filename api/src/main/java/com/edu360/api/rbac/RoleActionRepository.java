package com.edu360.api.rbac;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The query PermissionEvaluatorService actually runs for "does this role hold this action" -
 * see RoleAction's javadoc for why app.role_action, not ref.standard_role_action, is the
 * runtime source of truth.
 */
public interface RoleActionRepository extends JpaRepository<RoleAction, RoleActionId> {

    RoleAction save(RoleAction roleAction);

    boolean existsByIdTenantIdAndRoleDefinitionIdAndActionCode(UUID tenantId, UUID roleDefinitionId, String actionCode);

    List<RoleAction> findByIdTenantIdAndRoleDefinitionId(UUID tenantId, UUID roleDefinitionId);

    void deleteByIdTenantIdAndRoleDefinitionId(UUID tenantId, UUID roleDefinitionId);

    /** Every role in the tenant that currently holds this action - used only to tell a denied
     * caller which role(s) they'd need (PermissionEvaluatorService caps and filters this list;
     * never surfaced for a sensitive/locked action). */
    List<RoleAction> findByIdTenantIdAndActionCode(UUID tenantId, String actionCode);
}
