import api from "./axios";
import { store } from "../store";
import { clearUser } from "../store/slices/authSlice";
import { authService } from "./modules/authService";
import { clearTokens, getRefreshToken, setTokens } from "./tokenStorage";

// Watches every API response for a 401 (meaning "your access token has expired or you were
// never signed in"). First tries a silent refresh (one attempt, using the stored refresh
// token) and retries the original request; only signs the user out and sends them to /login
// if that refresh also fails - otherwise a short-lived access token would log people out far
// more often than necessary.
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
      const original = error?.config;
      if (error?.response?.status === 401 && original && !original._retried) {
        original._retried = true;
        const newToken = await refreshAccessToken();
        if (newToken) {
          original.headers = { ...original.headers, Authorization: `Bearer ${newToken}` };
          return api(original);
        }
        signOutLocally();
      }
      // MustChangePasswordFilter on the backend blocks every endpoint except /me, this one,
      // and /logout until the password is changed - send the user straight there instead of
      // showing whatever generic error the blocked call would otherwise surface.
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
