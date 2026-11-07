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

export type CreateUserPayload = {
  displayName: string;
  userType: UserType;
  email: string;
  mobile: string;
  nationalIdLast4?: string;
  initialRoleCode?: string;
  scopeType: ScopeType;
  institutionId?: string;
  departmentId?: string;
};

export type CreateUserResponse = {
  userAccountId: string;
  displayName: string;
  email: string;
  mobile: string;
  userType: UserType;
  status: UserAccountStatus;
  temporaryPassword?: string;
  initialRoleCode?: string;
  message: string;
};

export type ImportJobStatus =
  | "UPLOADED"
  | "VALIDATING"
  | "VALIDATED"
  | "IMPORTING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export type ImportJobResponse = {
  importJobId: string;
  status: ImportJobStatus;
  importKind: string;
  institutionId?: string;
  totalRows: number;
  validRows: number;
  errorRows: number;
  importMode?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt: string;
};

export type ImportRowErrorResponse = {
  id: string;
  rowNumber: number;
  fieldName?: string;
  errorCode: string;
  errorMessage: string;
  rowData: string;
};

export type ImportTemplateResponse = {
  importKind: string;
  filename: string;
  headers: string[];
  sampleRows: string[][];
  csvTemplate: string;
};

// Story #23: TENANT_TOP_USERS_CREATE (Action #268)
export type TopUserRoleCode =
  | "MANAGEMENT"
  | "DATA_PROTECTION_CONTACT"
  | "VICE_CHANCELLOR"
  | "GOVERNING_BODY_APPROVER";

export type TopUserCreatePayload = {
  roleCode: TopUserRoleCode;
  displayName: string;
  email: string;
  mobile?: string;
};

export type TopUserResponse = {
  userAccountId: string;
  displayName: string;
  email: string;
  mobile?: string;
  roleCode: string;
  userType: UserType;
  status: UserAccountStatus;
  temporaryPassword: string;
  twoStepLoginRequired: boolean;
  message: string;
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

  // POST /api/tenants/{tenantId}/users (USER_CREATE) - real user creation
  // with Keycloak provisioning, user account creation, DPDP Act 2023 compliance, and temporary credentials.
  createUser: (tenantId: string, payload: CreateUserPayload) =>
    api.post<CreateUserResponse>(`/tenants/${tenantId}/users`, payload),

  // POST /api/tenants/{tenantId}/users/check (USER_CREATE authorization-check)
  checkCreateUserAuthorization: (tenantId: string, scope: ActingScope) =>
    api.post<ActionResponse>(`/tenants/${tenantId}/users/check`, { ...scopeParams(scope) }),

  // GET /api/tenants/{tenantId}/import-templates/{kind} (USER_BULK_UPLOAD / Rule MD-26)
  getImportTemplate: (tenantId: string, kind: string = "USERS") =>
    api.get<ImportTemplateResponse>(`/tenants/${tenantId}/import-templates/${kind}`),

  // POST /api/tenants/{tenantId}/user-imports (USER_BULK_UPLOAD / Rule MD-27 pre-validation)
  createUserImport: (
    tenantId: string,
    csvContent: string,
    institutionId?: string,
    importKind: string = "USERS"
  ) =>
    api.post<ImportJobResponse>(`/tenants/${tenantId}/user-imports`, {
      csvContent,
      institutionId,
      importKind,
    }),

  // GET /api/tenants/{tenantId}/imports/{importJobId}
  getImportJob: (tenantId: string, importJobId: string) =>
    api.get<ImportJobResponse>(`/tenants/${tenantId}/imports/${importJobId}`),

  // GET /api/tenants/{tenantId}/imports/{importJobId}/errors (Rule MD-27 bad rows breakdown)
  listImportErrors: (tenantId: string, importJobId: string) =>
    api.get<ImportRowErrorResponse[]>(`/tenants/${tenantId}/imports/${importJobId}/errors`),

  // POST /api/tenants/{tenantId}/imports/{importJobId}/start (execute import under chosen mode: CLEAN_ONLY | GOOD_ROWS_ONLY)
  startImport: (
    tenantId: string,
    importJobId: string,
    importMode: "CLEAN_ONLY" | "GOOD_ROWS_ONLY"
  ) =>
    api.post<ImportJobResponse>(`/tenants/${tenantId}/imports/${importJobId}/start`, {
      importMode,
    }),

  // POST /api/tenants/{tenantId}/top-users (TENANT_TOP_USERS_CREATE / Action #268)
  createTopUser: (tenantId: string, payload: TopUserCreatePayload) =>
    api.post<TopUserResponse>(`/tenants/${tenantId}/top-users`, payload),
};
