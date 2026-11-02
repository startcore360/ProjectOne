import api from "../axios";
// The network calls behind sign-in/out and account management. login() posts straight to
// this app's own /api/auth/login - Keycloak checks the password behind the scenes
// (KeycloakAuthClient on the backend), but this SPA's code only ever talks to this API.
export type AuthUserResponse = {
  id: number;
  name: string;
  email: string;
  role: "TEACHER" | "STUDENT" | string;
  mustChangePassword: boolean;
};
export type AuthTokenResponse = {
  accessToken: string;
  refreshToken: string | null;
  expiresInSeconds: number;
  user: AuthUserResponse;
};
export const authService = {
  login: (email: string, password: string) =>
    api.post<AuthTokenResponse>("/auth/login", { email, password }),
  refresh: (refreshToken: string) =>
    api.post<AuthTokenResponse>("/auth/refresh", { refreshToken }),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post<AuthUserResponse>("/auth/change-password", { currentPassword, newPassword }),
  register: (payload: {
    name: string;
    email: string;
    password: string;
    role: "TEACHER" | "STUDENT";
  }) => api.post<AuthUserResponse>("/auth/register", payload),
  logout: () => api.post<void>("/auth/logout"),
  me: () => api.get<AuthUserResponse>("/auth/me"),
};
