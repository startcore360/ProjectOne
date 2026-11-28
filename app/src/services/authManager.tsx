import { store } from "../store";
import { clearUser, setUser, type AuthUser } from "../store/slices/authSlice";
import { setPlatformPermissions } from "../store/slices/platformPermissionsSlice";
import { clearAllSessionState } from "./sessionCleanup";
import { clearTenantPermissions } from "../store/slices/tenantPermissionsSlice";
import { tenantPermissionsService } from "../features/profile/api/tenantPermissionsService";
import { authService, type AuthUserResponse } from "./modules/authService";
import { setLogoutReason } from "./logoutNotice";
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "./tokenStorage";
import { clearTrialExpired } from "./trialNotice";
import { rememberInstitutionOnLoginSuccess } from "../lib/tenantHost";

const toAuthUser = (data: AuthUserResponse): AuthUser => ({
  id: data.id, displayName: data.displayName, tenantId: data.tenantId,
  userType: data.userType, platformRole: data.platformRole, roleCode: data.roleCode,
  roleName: data.roleName, photoUrl: data.photoUrl, mustChangePassword: data.mustChangePassword,
});

export const login = async (email: string, password: string, totp?: string) => {
  const { data } = await authService.login(email, password, totp);
  setTokens(data.accessToken, data.refreshToken);
  clearTrialExpired(); // E1-S7: a fresh sign-in (e.g. after activation) starts without the old banner
  const user = toAuthUser(data.user);
  store.dispatch(setUser(user));
  store.dispatch(clearTenantPermissions());
  // Not awaited - see the matching note in hydrateAuth(). Waiting on this here just delayed
  // navigate() after a successful sign-in for no benefit; ActionGate covers the gap.
  if (user.tenantId && !user.platformRole) void tenantPermissionsService.reload();
  if (typeof window !== "undefined") {
    rememberInstitutionOnLoginSuccess(window.location, typeof document !== "undefined" ? document.cookie : "");
  }
  return user;
};

export const IDLE_LOGOUT_MESSAGE = "You were signed out after inactivity.";
export const idleLogout = async () => { setLogoutReason(IDLE_LOGOUT_MESSAGE); await logout(); };

export const logout = async () => {
  try { await authService.logout(); }
  finally {
    clearTokens();
    clearTrialExpired();
    clearAllSessionState(store.dispatch);
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
    // Deliberately not awaited: bootstrapAuth blocks the app's first render on hydrateAuth(),
    // and this second call isn't needed before that - tenantPermissionsSlice already has its
    // own loading state, and ActionGate already renders a spinner while it's in flight. Awaiting
    // it here used to delay first paint by a second full round trip for no reason.
    if (user.tenantId && !user.platformRole) void tenantPermissionsService.reload();
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
  // Not awaited - see the matching note in hydrateAuth().
  if (user.tenantId && !user.platformRole) void tenantPermissionsService.reload();
  if (typeof window !== "undefined") {
    rememberInstitutionOnLoginSuccess(window.location, typeof document !== "undefined" ? document.cookie : "");
  }
  return user;
};
