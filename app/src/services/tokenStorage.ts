// Session credentials are deliberately scoped to the current tab. They remain readable
// to same-origin JavaScript, so this is not an XSS defence; production should move refresh
// credentials to Secure, HttpOnly, SameSite cookies with a coordinated backend change.
const ACCESS_TOKEN_KEY = "edu360.accessToken";
const REFRESH_TOKEN_KEY = "edu360.refreshToken";

export const setTokens = (accessToken: string, refreshToken: string | null) => {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  if (refreshToken) {
    sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  } else {
    // Do not retain an older refresh token when the server rotates to a response
    // that intentionally contains no refresh token.
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  }
};

export const getAccessToken = (): string | null => sessionStorage.getItem(ACCESS_TOKEN_KEY);

export const getRefreshToken = (): string | null => sessionStorage.getItem(REFRESH_TOKEN_KEY);

export const clearTokens = () => {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
};
