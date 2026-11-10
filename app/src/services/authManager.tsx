import { store } from "../store";
import { clearUser, setUser, type AuthUser } from "../store/slices/authSlice";
import { setPlatformPermissions } from "../store/slices/platformPermissionsSlice";
import { authService, type AuthUserResponse } from "./modules/authService";
import { setLogoutReason } from "./logoutNotice";
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "./tokenStorage";
// The actions a page can call to sign a user in, sign them out, re-check who's currently
// signed in, or change the password. Each one also updates the shared Redux store, so the
// rest of the app (sidebar, route guards, etc.) immediately sees the change. Login posts
// straight to this app's own /api/auth/login (see features/auth/pages/LoginPage.tsx) -
// Keycloak verifies the password behind the scenes, but this app's own screen is the only
// thing the user ever sees.

// Maps the backend's AuthUserResponse shape {id, displayName, tenantId, userType,
// platformRole, roleCode, roleName, photoUrl, mustChangePassword} onto the store's AuthUser -
// same fields, kept as its own function so the one mapping lives in one place.
const toAuthUser = (data: AuthUserResponse): AuthUser => ({
  id: data.id,
  displayName: data.displayName,
  tenantId: data.tenantId,
  userType: data.userType,
  platformRole: data.platformRole,
  roleCode: data.roleCode,
  roleName: data.roleName,
  photoUrl: data.photoUrl,
  mustChangePassword: data.mustChangePassword,
});

export const login = async (email: string, password: string, totp?: string) => {
  const { data } = await authService.login(email, password, totp);
  setTokens(data.accessToken, data.refreshToken);
  const user = toAuthUser(data.user);
  store.dispatch(setUser(user));
  return user;
};
// I-10: shown on the sign-in page after the idle timer ends a session (REG-I10-2).
export const IDLE_LOGOUT_MESSAGE = "You were signed out after inactivity. Please sign in again.";

// Used by every idle timer (workspace and change-password screen) so the sign-in page can say why.
export const idleLogout = async () => {
  setLogoutReason(IDLE_LOGOUT_MESSAGE);
  await logout();
};

export const logout = async () => {
  try {
    await authService.logout();
  } finally {
    // Clear the local "signed in" state and leave the page even if the
    // server call above failed — an offline sign-out should still work
    // from the user's point of view.
    clearTokens();
    store.dispatch(clearUser());
    store.dispatch(setPlatformPermissions(null));
    window.location.replace("/login");
  }
};
// Asks the backend "who am I, if anyone?" and updates the store to match. Used when the app
// first loads, to pick up an existing session from a stored access token.
export const hydrateAuth = async () => {
  if (!getAccessToken()) {
    store.dispatch(clearUser());
    store.dispatch(setPlatformPermissions(null));
    return null;
  }
  try {
    const { data } = await authService.me();
    const user = toAuthUser(data);
    store.dispatch(setUser(user));
    return user;
  } catch {
    clearTokens();
    store.dispatch(clearUser());
    store.dispatch(setPlatformPermissions(null));
    return null;
  }
};
// Used by ChangePasswordPage for both the forced first-time change and a later voluntary
// one - same backend endpoint either way. Clears the mustChangePassword flag in the store on
// success so route guards immediately let the user through to the rest of the app.
//
// The access token still says "must change password" until it's replaced, and the backend blocks
// most calls while it does (MustChangePasswordFilter), so get a fresh token before moving on. If
// that fails, the password has still changed, so send the user to sign in with the new one.
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
    window.location.replace("/login");
    return null;
  }
  const user = toAuthUser(data);
  store.dispatch(setUser(user));
  return user;
};
