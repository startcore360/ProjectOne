import api from "../../../services/axios";
import { store } from "../../../store";
import {
  clearTenantPermissions,
  setTenantPermissions,
  tenantPermissionsFailed,
  tenantPermissionsLoading,
  type TenantPermissions,
} from "../../../store/slices/tenantPermissionsSlice";

type UserPermissionsResponse = {
  tenantId: string;
  userAccountId: string;
  activeRoleCode: string | null;
  activeRoleName: string | null;
  scopeType: "TENANT" | "INSTITUTION" | "DEPARTMENT" | null;
  institutionId: string | null;
  departmentId: string | null;
  allowedActions: string[];
};

export const tenantPermissionsService = {
  async reload(): Promise<TenantPermissions | null> {
    const user = store.getState().auth.user;
    if (!user?.tenantId || user.platformRole) {
      store.dispatch(clearTenantPermissions());
      return null;
    }
    store.dispatch(tenantPermissionsLoading());
    try {
      const { data } = await api.get<UserPermissionsResponse>("/me/permissions");
      const permissions: TenantPermissions = {
        tenantId: data.tenantId,
        userAccountId: data.userAccountId,
        activeRoleCode: data.activeRoleCode,
        activeRoleName: data.activeRoleName,
        scopeType: data.scopeType,
        institutionId: data.institutionId,
        departmentId: data.departmentId,
        allowedActions: data.allowedActions ?? [],
      };
      store.dispatch(setTenantPermissions(permissions));
      return permissions;
    } catch {
      store.dispatch(tenantPermissionsFailed("Could not load permissions for the active role."));
      return null;
    }
  },
  clear() {
    store.dispatch(clearTenantPermissions());
  },
};
