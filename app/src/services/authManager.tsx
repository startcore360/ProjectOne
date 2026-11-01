import { store } from "../store";
import { clearUser, setUser } from "../store/slices/authSlice";
import { authService } from "./modules/authService";

const TOKEN_KEY = "edu360-token";
const USER_KEY = "edu360-user";

export const login = async (email: string, password: string) => {
  const { data } = await authService.login(email, password);
  const user = {
    id: data.userId,
    name: data.name,
    email: data.email,
    role: data.role,
  };

  localStorage.setItem(TOKEN_KEY, data.token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  store.dispatch(setUser(user));

  return user;
};

export const logout = async () => {
  try {
    await authService.logout();
  } finally {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    store.dispatch(clearUser());
    window.location.href = "/login";
  }
};

export const hydrateAuth = async () => {
  try {
    const userJson = localStorage.getItem(USER_KEY);
    const token = localStorage.getItem(TOKEN_KEY);

    if (!userJson || !token) {
      store.dispatch(clearUser());
      return null;
    }

    const user = JSON.parse(userJson);
    store.dispatch(setUser(user));
    return user;
  } catch (error) {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    store.dispatch(clearUser());
    return null;
  }
};
