import api from "./axios";
import { store } from "../store";
import { clearUser } from "../store/slices/authSlice";
import { authService } from "./modules/authService";
import { clearTokens, getRefreshToken, setTokens } from "./tokenStorage";

type RetriableRequest = {
  _retried?: boolean;
  url?: string;
};

export const setupAxiosInterceptors = () => {
  let refreshInFlight: Promise<string | null> | null = null;

  const signOutLocally = () => {
    clearTokens();
    store.dispatch(clearUser());
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

      // A rejected refresh request must never trigger another refresh attempt.
      // Login failures are credential errors, not expired-session events.
      if (
        error?.response?.status === 401 &&
        original &&
        !original._retried &&
        !isRefreshRequest &&
        !isLoginRequest
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
      return Promise.reject(error);
    },
  );
};
