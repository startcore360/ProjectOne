package com.edu360.api.rbac;

import java.util.List;

import org.springframework.data.repository.Repository;

/**
 * Read-only on purpose (see StandardRoleRepository's javadoc for why): ref.standard_role_action
 * is seeded exclusively by sql/05_ref_data.sql. Used by the (future) role-provisioning service
 * to copy a standard role's template actions into app.role_action - not read by
 * PermissionEvaluatorService itself (see StandardRoleAction's javadoc).
 */
public interface StandardRoleActionRepository extends Repository<StandardRoleAction, Integer> {

    List<StandardRoleAction> findByRoleCode(String roleCode);
}
