package com.edu360.api.rbac;

import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * The query PermissionEvaluatorService actually runs for "does this role hold this action" -
 * see RoleAction's javadoc for why app.role_action, not ref.standard_role_action, is the
 * runtime source of truth. No delete method: role-action grants are changed by adding/removing
 * rows through the (future) role-management service's own audited flow, never a bare repository
 * delete, and this slice does not need write access at all yet.
 */
public interface RoleActionRepository extends Repository<RoleAction, RoleActionId> {

    RoleAction save(RoleAction roleAction);

    boolean existsByIdTenantIdAndRoleDefinitionIdAndActionCode(UUID tenantId, UUID roleDefinitionId, String actionCode);
}
