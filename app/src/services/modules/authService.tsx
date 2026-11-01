import api from "../axios";
// The actual network calls for signing in, signing up, signing out, and asking
// "who am I logged in as right now". Everything else in the app goes through
// these functions instead of calling the backend directly.
export type AuthUserResponse = {
  id: number;
  name: string;
  email: string;
  role: "TEACHER" | "STUDENT" | string;
};
export const authService = {
  login: (email: string, password: string) =>
    api.post<AuthUserResponse>("/auth/login", { email, password }),
  register: (payload: {
    name: string;
    email: string;
    password: string;
    role: "TEACHER" | "STUDENT";
  }) => api.post<AuthUserResponse>("/auth/register", payload),
  logout: () => api.post<void>("/auth/logout"),
  me: () => api.get<AuthUserResponse>("/auth/me"),
};
