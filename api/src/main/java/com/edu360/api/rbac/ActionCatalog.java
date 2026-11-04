package com.edu360.api.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WHAT: one row of ref.action_catalog - a read-only platform-seeded row (all 278 actions of
 * Action List V3.3). Seeded exactly once by sql/05_ref_data.sql; never written by the
 * application (same reasoning as StandardRole).
 *
 * isApprovalAction is what PermissionEvaluatorService uses to enforce Addendum rule 11
 * (self-elevation/self-approval is prohibited): every action flagged true here is a chain-step
 * action a principal must never be allowed to action against their own record, regardless of
 * what their role otherwise grants.
 */
@Entity
@Table(schema = "ref", name = "action_catalog")
@Getter
@NoArgsConstructor
public class ActionCatalog {

    @Id
    @Column(name = "action_code")
    private String actionCode;

    @Column(name = "action_no", nullable = false)
    private short actionNo;

    @Column(name = "module_name", nullable = false)
    private String moduleName;

    @Column(nullable = false)
    private short phase;

    @Column(nullable = false)
    private String description;

    @Column(name = "is_approval_action", nullable = false)
    private boolean approvalAction;

    @Column(name = "sensitive_class")
    private String sensitiveClass;

    @Column(name = "changed_in")
    private String changedIn;
}
