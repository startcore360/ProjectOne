package com.edu360.api.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WHAT: one row of ref.standard_role_action - the platform's role-to-action TEMPLATE (Action
 * List V3.3 "Who can do it" column). Read-only, seeded by sql/05_ref_data.sql.
 *
 * NOT what PermissionEvaluatorService checks at runtime: the live, per-tenant grant is
 * app.role_action (see RoleAction), which is populated FROM this template when a tenant's role
 * is provisioned (a future role-management story), and can additionally hold custom rows an
 * admin picked for a CUSTOM role (Addendum rule 4). This entity exists so the (future)
 * provisioning service, and any reporting/comparison against the template, has a first-class
 * JPA mapping - it is not on the evaluator's hot path in this slice.
 */
@Entity
@Table(schema = "ref", name = "standard_role_action")
@Getter
@NoArgsConstructor
public class StandardRoleAction {

    @Id
    @Column(name = "standard_role_action_id")
    private Integer standardRoleActionId;

    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    /** Free-text scope rule from the Action List "Limited to" column (e.g. "Own scope
     *  (Registrar: university staff)"); enforced by the service, not by a structured column. */
    @Column(name = "limited_to")
    private String limitedTo;
}
