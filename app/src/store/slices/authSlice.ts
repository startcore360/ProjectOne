import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { PlatformRole, UserType } from "../../services/modules/authService";

// Mirrors api/.../auth/dto/AuthUserResponse.java: exactly one of (tenantId + userType) or
// (platformRole) is populated for a real signed-in user, never both, never neither. There is
// no generic "role" string any more - tenant identities are scoped by userType (STAFF/STUDENT/
// GUARDIAN/APPLICANT/GOVERNANCE/SYSTEM), platform identities by platformRole.
//
// roleCode/roleName are the tenant user's resolved RBAC role (app.role_definition, via their
// ACTIVE app.user_role_assignment - see AuthService.resolveRole on the backend). Null for
// platform principals and for a tenant user with no active assignment yet - the dashboard falls
// back to its generic userType-only view in that case, same as before this field existed.
//
// photoUrl: same meaning as ProfileResponse.photoUrl - null for platform principals and for a
// tenant user with no active photo yet; non-null means "GET this path (via axios, not a plain
// <img src>, since this app authenticates with a bearer header) to fetch the current photo".
export type AuthUser = {
  id: string;
  displayName: string;
  tenantId: string | null;
  userType: UserType | null;
  platformRole: PlatformRole | null;
  roleCode: string | null;
  roleName: string | null;
  photoUrl: string | null;
  // Bumped on every successful photo upload in this session. photoUrl is the same fixed path
  // ("/me/profile/photo") before and after a replacement, so it alone cannot tell the sidebar
  // avatar to re-fetch; useProfilePhoto keys on (photoUrl, photoRevision). Absent = 0.
  photoRevision?: number;
  mustChangePassword: boolean;
};

type AuthState = {
  user: AuthUser | null;
  isAuthenticated: boolean;
  loading: boolean;
};

// This piece of the store holds "who is logged in right now" (or nobody, if no
// one is). loading starts as true on purpose: when the app first opens, we don't
// yet know if the user is logged in, so the app can show a loading state instead
// of briefly flashing the login page before bootstrapAuth finishes checking.
const initialState: AuthState = {
  user: null,
  isAuthenticated: false,
  loading: true,
};

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    setUser: (state, action: PayloadAction<AuthUser | null>) => {
      state.user = action.payload;
      state.isAuthenticated = !!action.payload;
    },
    setLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload;
    },
    clearUser: (state) => {
      state.user = null;
      state.isAuthenticated = false;
      state.loading = false;
    },
    switchActiveRole: (
      state,
      action: PayloadAction<{ roleCode: string; roleName: string }>
    ) => {
      if (state.user) {
        state.user.roleCode = action.payload.roleCode;
        state.user.roleName = action.payload.roleName;
      }
    },
    // The profile page saved the caller's own profile: keep the signed-in identity shown in the
    // app shell (sidebar / mobile menu name and avatar) in step without a re-login.
    profileUpdated: (
      state,
      action: PayloadAction<{ displayName?: string; photoUrl?: string | null; photoChanged?: boolean }>
    ) => {
      if (!state.user) return;
      const { displayName, photoUrl, photoChanged } = action.payload;
      if (displayName !== undefined) state.user.displayName = displayName;
      if (photoUrl !== undefined) state.user.photoUrl = photoUrl;
      if (photoChanged) state.user.photoRevision = (state.user.photoRevision ?? 0) + 1;
    },
  },
});

export const { setUser, setLoading, clearUser, switchActiveRole, profileUpdated } = authSlice.actions;
export default authSlice.reducer;
