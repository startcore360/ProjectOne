import api from "../../../services/axios";

// Network calls behind the tenant-side "Users & roles" screens (USER_VIEW/USER_EDIT/
// USER_DEACTIVATE/USER_PASSWORD_RESET_UNLOCK, plus the USER_CREATE authorization-check
// endpoint). Shapes below mirror the backend DTOs byte for byte - see
// api/src/main/java/com/edu360/api/rbac/dto/*.java and the four *Controller.java files in that
// same package. All calls go through the shared axios instance (../../../services/axios), which
// already attaches the bearer token - this file does not touch services/ itself.

// Mirrors com.edu360.api.rbac.ScopeType - app.user_role_assignment can be held at exactly one
// of these three shapes.
export type ScopeType = "TENANT" | "INSTITUTION" | "DEPARTMENT";

// Mirrors com.edu360.api.identity.UserType (ck_user_account_user_type).
export type UserType = "STAFF" | "STUDENT" | "GUARDIAN" | "APPLICANT" | "GOVERNANCE" | "SYSTEM";

// Mirrors com.edu360.api.identity.UserAccountStatus (ck_user_account_status). DEACTIVATED is
// permanent in this schema - there is no "reactivate" action, by design.
export type UserAccountStatus = "PENDING_ACTIVATION" | "ACTIVE" | "DEACTIVATED";

// Mirrors UserSummaryResponse.java - the one projection shared by USER_VIEW (list/detail) and
// USER_EDIT's response.
export type UserSummary = {
  userAccountId: string;
  userType: UserType;
  displayName: string;
  email: string;
  mobile: string;
  status: UserAccountStatus;
};

// The "which scope am I, the caller, asking in" shape every one of these endpoints needs -
// PermissionEvaluatorService has no column recording "the one scope a user operates in", so the
// caller always states it explicitly. institutionId/departmentId are omitted from the querystring
// (via axios dropping undefined params) when the scope does not need them.
export type ActingScope = {
  scopeType: ScopeType;
  institutionId?: string;
  departmentId?: string;
};

// Mirrors CreateUserCheckResponse.java (reused as-is by UserDeactivationController and the
// /unlock operation, which both return the same {actionCode, reasonCode, message} shape).
export type ActionResponse = {
  actionCode: string;
  reasonCode: string;
  message: string;
};

// Mirrors PasswordResetResponse.java - temporaryPassword is only ever present on a successful
// reset and must never be logged or persisted client-side beyond the one-time dialog that shows
// it.
export type PasswordResetResponse = ActionResponse & {
  temporaryPassword: string;
};

export type UpdateUserFields = {
  displayName?: string;
  email?: string;
  mobile?: string;
};

function scopeParams(scope: ActingScope) {
  return {
    scopeType: scope.scopeType,
    institutionId: scope.scopeType === "TENANT" ? undefined : scope.institutionId || undefined,
    departmentId: scope.scopeType === "DEPARTMENT" ? scope.departmentId || undefined : undefined,
  };
}

export const userManagementService = {
  // GET /api/tenants/{tenantId}/users (USER_VIEW, list form)
  listUsers: (tenantId: string, scope: ActingScope) =>
    api.get<{ users: UserSummary[] }>(`/tenants/${tenantId}/users`, { params: scopeParams(scope) }),

  // GET /api/tenants/{tenantId}/users/{userId} (USER_VIEW, detail form)
  getUser: (tenantId: string, userId: string, scope: ActingScope) =>
    api.get<UserSummary>(`/tenants/${tenantId}/users/${userId}`, { params: scopeParams(scope) }),

  // PATCH /api/tenants/{tenantId}/users/{userId} (USER_EDIT) - a partial update; fields left
  // undefined here are left untouched server-side, never cleared.
  updateUser: (tenantId: string, userId: string, scope: ActingScope, fields: UpdateUserFields) =>
    api.patch<UserSummary>(`/tenants/${tenantId}/users/${userId}`, { ...scopeParams(scope), ...fields }),

  // POST /api/tenants/{tenantId}/users/{userId}/deactivation (USER_DEACTIVATE) - always
  // institution-scoped; soft delete only, idempotent if already deactivated.
  deactivateUser: (tenantId: string, userId: string, institutionId: string) =>
    api.post<ActionResponse>(`/tenants/${tenantId}/users/${userId}/deactivation`, { institutionId }),

  // POST /api/tenants/{tenantId}/users/{userId}/password-reset (USER_PASSWORD_RESET_UNLOCK) -
  // the response carries a one-time plaintext temporary password; see PasswordResetResponse's
  // own javadoc on the backend for why.
  resetPassword: (tenantId: string, userId: string, institutionId: string) =>
    api.post<PasswordResetResponse>(`/tenants/${tenantId}/users/${userId}/password-reset`, { institutionId }),

  // POST /api/tenants/{tenantId}/users/{userId}/unlock (USER_PASSWORD_RESET_UNLOCK)
  unlockUser: (tenantId: string, userId: string, institutionId: string) =>
    api.post<ActionResponse>(`/tenants/${tenantId}/users/${userId}/unlock`, { institutionId }),

  // POST /api/tenants/{tenantId}/users (USER_CREATE) - deliberately NOT a real user-creation
  // call. UserCreationController.java is an authorization-check / proof-of-integration endpoint
  // only: "no user was created" (its own javadoc). Named checkCreateUserAuthorization here, not
  // createUser, so nothing in this file implies it inserts a row.
  checkCreateUserAuthorization: (tenantId: string, scope: ActingScope) =>
    api.post<ActionResponse>(`/tenants/${tenantId}/users`, { ...scopeParams(scope) }),
};
