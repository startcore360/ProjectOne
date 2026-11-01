import api from "../axios";

export type AuthResponse = {
  token: string;
  userId: number;
  name: string;
  email: string;
  role: "TEACHER" | "STUDENT" | string;
};

export const authService = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>("/auth/login", { email, password }),
  register: (payload: {
    name: string;
    email: string;
    password: string;
    role: "TEACHER" | "STUDENT";
  }) => api.post<AuthResponse>("/auth/register", payload),
  logout: () => Promise.resolve(),
  me: async () => {
    const userJson = localStorage.getItem("edu360-user");
    const token = localStorage.getItem("edu360-token");

    if (!userJson || !token) {
      throw new Error("No active session");
    }

    return { data: JSON.parse(userJson) };
  },
};
