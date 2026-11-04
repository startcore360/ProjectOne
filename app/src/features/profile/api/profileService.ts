import api from "../../../services/axios";

// Mirrors api/.../selfservice/dto/ProfileResponse.java and ProfileUpdateRequest.java exactly.
// PROFILE_EDIT_OWN (Sign-in epic, Scope: Self): GET/PATCH /api/me/profile - "who am I" is
// resolved server-side from the authenticated principal, never from an id this client sends.
//
// email is intentionally absent from ProfileUpdateRequest - it doubles as the Keycloak login
// username for STAFF/platform accounts, so it is read-only here (see the backend DTO's own
// class comment). Do not add an editable email field against this endpoint.
export type UserType = "STAFF" | "STUDENT" | "GUARDIAN" | "APPLICANT" | "GOVERNANCE" | "SYSTEM";
export type PlatformRole =
  | "PRODUCT_OWNER"
  | "PLATFORM_ADMIN"
  | "BILLING_MANAGER"
  | "SUPPORT_MANAGER"
  | "RELEASE_MANAGER"
  | "DATA_PROTECTION_OFFICER";

export type ProfileResponse = {
  tenantId: string | null;
  userAccountId: string | null;
  userType: UserType | null;
  platformUserId: string | null;
  platformRole: PlatformRole | null;
  displayName: string;
  email: string;
  mobile: string | null;
};

export type ProfileUpdateRequest = {
  displayName?: string;
  mobile?: string;
};

export const profileService = {
  get: () => api.get<ProfileResponse>("/me/profile"),
  update: (request: ProfileUpdateRequest) => api.patch<ProfileResponse>("/me/profile", request),
};
