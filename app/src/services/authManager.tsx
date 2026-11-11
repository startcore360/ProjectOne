import { store } from "../store";
import { clearUser, setUser, type AuthUser } from "../store/slices/authSlice";
import { setPlatformPermissions } from "../store/slices/platformPermissionsSlice";
import { clearTenantPermissions } from "../store/slices/tenantPermissionsSlice";
import { authService, type AuthUserResponse } from "./modules/authService";
import { setLogoutReason } from "./logoutNotice";
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "./tokenStorage";

const toAuthUser = (data: AuthUserResponse): AuthUser => ({
  id: data.id, displayName: data.displayName, tenantId: data.tenantId,
  userType: data.userType, platformRole: data.platformRole, roleCode: data.roleCode,
  roleName: data.roleName, photoUrl: data.photoUrl, mustChangePassword: data.mustChangePassword,
});

export const login = async (email: string, password: string, totp?: string) => {
  const { data } = await authService.login(email, password, totp);
  setTokens(data.accessToken, data.refreshToken);
  const user = toAuthUser(data.user);
  store.dispatch(setUser(user));
  store.dispatch(clearTenantPermissions());
  return user;
};

export const IDLE_LOGOUT_MESSAGE = "You were signed out after inactivity. Please sign in again.";
export const idleLogout = async () => { setLogoutReason(IDLE_LOGOUT_MESSAGE); await logout(); };

export const logout = async () => {
  try { await authService.logout(); }
  finally {
    clearTokens();
    store.dispatch(clearUser());
    store.dispatch(setPlatformPermissions(null));
    store.dispatch(clearTenantPermissions());
    window.location.replace("/login");
  }
};

export const hydrateAuth = async () => {
  if (!getAccessToken()) {
    store.dispatch(clearUser());
    store.dispatch(setPlatformPermissions(null));
    store.dispatch(clearTenantPermissions());
    return null;
  }
  try {
    const { data } = await authService.me();
    const user = toAuthUser(data);
    store.dispatch(setUser(user));
    store.dispatch(clearTenantPermissions());
    return user;
  } catch {
    clearTokens();
    store.dispatch(clearUser());
    store.dispatch(setPlatformPermissions(null));
    store.dispatch(clearTenantPermissions());
    return null;
  }
};

export const changePassword = async (currentPassword: string, newPassword: string) => {
  const { data } = await authService.changePassword(currentPassword, newPassword);
  const refreshToken = getRefreshToken();
  try {
    if (!refreshToken) throw new Error("No refresh token");
    const { data: tokens } = await authService.refresh(refreshToken);
    setTokens(tokens.accessToken, tokens.refreshToken);
  } catch {
    setLogoutReason("Your password was changed. Please sign in with your new password.");
    clearTokens();
    store.dispatch(clearUser());
    store.dispatch(setPlatformPermissions(null));
    store.dispatch(clearTenantPermissions());
    window.location.replace("/login");
    return null;
  }
  const user = toAuthUser(data);
  store.dispatch(setUser(user));
  store.dispatch(clearTenantPermissions());
  return user;
};
