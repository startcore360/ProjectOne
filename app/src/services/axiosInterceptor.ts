import api, { SUPPORT_SESSION_EXPIRY_KEY, SUPPORT_SESSION_TOKEN_KEY } from "./axios";
import { store } from "../store";
import { clearAllSessionState } from "./sessionCleanup";
import { authService } from "./modules/authService";
import { getLogoutReason, setLogoutReason } from "./logoutNotice";
import { clearTokens, getRefreshToken, setTokens } from "./tokenStorage";
import { announceActiveRoleRequired, isActiveRoleRequired } from "./activeRoleRequired";
import { announceTrialExpired } from "./trialNotice";

type RetriableRequest = {
  _retried?: boolean;
  url?: string;
};

// A support-session tab was never logged in and has no refresh token: it carries only a
// short-lived support token that the request interceptor attaches exclusively to
// /tenants/{id}/... paths. Any other endpoint (session-settings, notifications, branding,
// profile, etc.) will correctly 401 for that token by design. That is an authorization
// boundary, not an expired login - it must never trigger the normal refresh/forced-logout
// flow below, which would otherwise redirect the whole read-only diagnostic tab to /login.
function isActiveSupportSession(): boolean {
  const token = window.sessionStorage.getItem(SUPPORT_SESSION_TOKEN_KEY);
  if (!token) return false;
  const expiry = window.sessionStorage.getItem(SUPPORT_SESSION_EXPIRY_KEY);
  return !expiry || new Date(expiry).getTime() > Date.now();
}

export const setupAxiosInterceptors = () => {
  let refreshInFlight: Promise<string | null> | null = null;

  const signOutLocally = () => {
    if (!getLogoutReason()) setLogoutReason("Your session has ended. Please sign in again.");
    clearTokens();
    clearAllSessionState(store.dispatch);
    if (window.location.pathname !== "/login") window.location.replace("/login");
  };

  const refreshAccessToken = (): Promise<string | null> => {
    if (!refreshInFlight) {
      refreshInFlight = (async () => {
        const refreshToken = getRefreshToken();
        if (!refreshToken) return null;
        try {
          const { data } = await authService.refresh(refreshToken);
          setTokens(data.accessToken, data.refreshToken);
          return data.accessToken;
        } catch {
          return null;
        } finally {
          refreshInFlight = null;
        }
      })();
    }
    return refreshInFlight;
  };

  api.interceptors.response.use(
    (response) => response,
    async (error) => {
      const original = error?.config as (typeof error.config & RetriableRequest) | undefined;
      const requestPath = (original?.url ?? "").split("?")[0].replace(/\/+$/, "");
      const isRefreshRequest = requestPath.endsWith("/auth/refresh");
      const isLoginRequest = requestPath.endsWith("/auth/login");
      const isPublicRequest = requestPath.startsWith("/public/") || requestPath === "/public" || requestPath.includes("/api/public/");

      // A rejected refresh request must never trigger another refresh attempt.
      // Login failures and public landing requests are not expired-session events.
      if (
        error?.response?.status === 401 &&
        original &&
        !original._retried &&
        !isRefreshRequest &&
        !isLoginRequest &&
        !isPublicRequest &&
        !isActiveSupportSession()
      ) {
        original._retried = true;
        const newToken = await refreshAccessToken();
        if (newToken) {
          original.headers = { ...original.headers, Authorization: `Bearer ${newToken}` };
          return api(original);
        }
        signOutLocally();
      }

      if (
        error?.response?.status === 403 &&
        error.response.data?.error === "PASSWORD_CHANGE_REQUIRED" &&
        window.location.pathname !== "/change-password"
      ) {
        window.location.replace("/change-password");
      }

      // A-02: several roles and none chosen - send the user to the role switcher.
      if (isActiveRoleRequired(error?.response?.status, error?.response?.data)) {
        announceActiveRoleRequired();
      }
      // E1-S7: the trial has ended - the app shell shows a persistent read-only banner.
      if (error?.response?.status === 403 && error.response.data?.code === "TRIAL_EXPIRED") {
        announceTrialExpired(error.response.data?.trialEndsAt ?? null);
      }
      return Promise.reject(error);
    },
  );
};
