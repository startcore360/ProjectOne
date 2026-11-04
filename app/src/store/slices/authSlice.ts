import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { PlatformRole, UserType } from "../../services/modules/authService";

// Mirrors api/.../auth/dto/AuthUserResponse.java: exactly one of (tenantId + userType) or
// (platformRole) is populated for a real signed-in user, never both, never neither. There is
// no generic "role" string any more - tenant identities are scoped by userType (STAFF/STUDENT/
// GUARDIAN/APPLICANT/GOVERNANCE/SYSTEM), platform identities by platformRole.
export type AuthUser = {
  id: string;
  displayName: string;
  tenantId: string | null;
  userType: UserType | null;
  platformRole: PlatformRole | null;
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
  },
});

export const { setUser, setLoading, clearUser } = authSlice.actions;
export default authSlice.reducer;
