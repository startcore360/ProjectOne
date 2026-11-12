import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

export type TenantPermissions = {
  tenantId: string;
  userAccountId: string;
  activeRoleCode: string | null;
  activeRoleName: string | null;
  allowedActions: string[];
};

type TenantPermissionsState = {
  current: TenantPermissions | null;
  loading: boolean;
  error: string | null;
};

const initialState: TenantPermissionsState = { current: null, loading: false, error: null };

const slice = createSlice({
  name: "tenantPermissions",
  initialState,
  reducers: {
    tenantPermissionsLoading(state) {
      state.loading = true;
      state.error = null;
    },
    setTenantPermissions(state, action: PayloadAction<TenantPermissions | null>) {
      state.current = action.payload;
      state.loading = false;
      state.error = null;
    },
    tenantPermissionsFailed(state, action: PayloadAction<string>) {
      state.current = null;
      state.loading = false;
      state.error = action.payload;
    },
    clearTenantPermissions(state) {
      state.current = null;
      state.loading = false;
      state.error = null;
    },
  },
});

export const { tenantPermissionsLoading, setTenantPermissions, tenantPermissionsFailed, clearTenantPermissions } = slice.actions;
export default slice.reducer;
