import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

export type Role = "TEACHER" | "STUDENT" | string;

export type AuthUser = {
  id: number | string;
  email: string;
  name: string;
  role?: Role;
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
