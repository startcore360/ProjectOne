import api from "../axios";
import { getBrowserId } from "../browserId";
// The network calls behind sign-in/out and account management. login() posts straight to
// this app's own /api/auth/login - Keycloak checks the password behind the scenes
// (KeycloakAuthClient on the backend), but this SPA's code only ever talks to this API.
//
// Shape matches api/src/main/java/com/edu360/api/auth/dto/AuthUserResponse.java exactly: a
// signed-in identity is either a tenant identity (tenantId + userType populated, platformRole
// null) or a platform identity (platformRole populated, tenantId + userType null) - never both,
// never neither.
export type UserType = "STAFF" | "STUDENT" | "GUARDIAN" | "APPLICANT" | "GOVERNANCE" | "SYSTEM";
export type PlatformRole =
  | "PRODUCT_OWNER"
  | "PLATFORM_ADMIN"
  | "BILLING_MANAGER"
  | "SUPPORT_MANAGER"
  | "RELEASE_MANAGER"
  | "DATA_PROTECTION_OFFICER";

export type AuthUserResponse = {
  id: string;
  displayName: string;
  tenantId: string | null;
  userType: UserType | null;
  platformRole: PlatformRole | null;
  // The tenant user's resolved RBAC role (app.role_definition), null for platform principals
  // and for a tenant user with no active role assignment yet - see AuthService.resolveRole.
  roleCode: string | null;
  roleName: string | null;
  // Same meaning as ProfileResponse.photoUrl (see profileService.ts) - null for platform
  // principals and for a tenant user with no active photo yet.
  photoUrl: string | null;
  mustChangePassword: boolean;
};
export type AuthTokenResponse = {
  accessToken: string;
  refreshToken: string | null;
  expiresInSeconds: number;
  user: AuthUserResponse;
};
export const authService = {
  // X-Browser-Id (SES-01): lets the server replace THIS browser's previous session on a new
  // sign-in while keeping the user's other devices signed in.
  login: (email: string, password: string, totp?: string) => {
    const browserId = getBrowserId();
    return api.post<AuthTokenResponse>(
      "/auth/login",
      totp ? { email, password, totp } : { email, password },
      browserId ? { headers: { "X-Browser-Id": browserId } } : undefined,
    );
  },
  refresh: (refreshToken: string) =>
    api.post<AuthTokenResponse>("/auth/refresh", { refreshToken }),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post<AuthUserResponse>("/auth/change-password", { currentPassword, newPassword }),
  logout: () => api.post<void>("/auth/logout"),
  me: () => api.get<AuthUserResponse>("/auth/me"),
};
