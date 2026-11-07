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
  // Null for platform accounts (file storage is tenant-scoped - a platform account has nowhere
  // in the schema to own a stored file) and null for a tenant account with no photo set yet.
  // Non-null just means "GET this path to fetch the current photo bytes" - this app authenticates
  // with a bearer header (see services/axios.ts), not cookies, so a plain <img src> can never
  // carry that header; ProfilePage must fetch this via axios and turn it into an object URL.
  photoUrl: string | null;
};

export type ProfileUpdateRequest = {
  displayName?: string;
  mobile?: string;
};

export const profileService = {
  get: () => api.get<ProfileResponse>("/me/profile"),
  update: (request: ProfileUpdateRequest) => api.patch<ProfileResponse>("/me/profile", request),
  uploadPhoto: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return api.post<ProfileResponse>("/me/profile/photo", form);
  },
  getPhoto: () => api.get<Blob>("/me/profile/photo", { responseType: "blob" }),
};
