// Holds the access/refresh tokens this app's own /api/auth/login returns. There is no
// Keycloak session cookie any more - these tokens ARE the session, so losing them means
// being signed out. sessionStorage (not localStorage) so they clear when the tab closes.
const ACCESS_TOKEN_KEY = "edu360.accessToken";
const REFRESH_TOKEN_KEY = "edu360.refreshToken";

export const setTokens = (accessToken: string, refreshToken: string | null) => {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  if (refreshToken) {
    sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }
};

export const getAccessToken = (): string | null => sessionStorage.getItem(ACCESS_TOKEN_KEY);

export const getRefreshToken = (): string | null => sessionStorage.getItem(REFRESH_TOKEN_KEY);

export const clearTokens = () => {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
};
