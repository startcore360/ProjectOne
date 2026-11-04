package com.edu360.api.rbac;

/**
 * WHAT: app.user_role_assignment.status - mirrors ck_user_role_assignment_status in
 * sql/01_schema.sql word for word.
 *
 * WHY no DELETE, ever (Addendum rule 17): ending an assignment means writing ENDED here (and
 * setting effective_to), never removing the row - the row is the audit trail of who held what
 * role, when (Addendum rule 18). UserRoleAssignmentRepository deliberately exposes no delete
 * method, so this is enforced at the Java layer too, not just by convention.
 */
public enum AssignmentStatus {
    ACTIVE,
    ENDED
}
