import { store } from "../store";
import { clearUser, setUser } from "../store/slices/authSlice";
import { authService } from "./modules/authService";
// The three actions a page can call to sign a user in, sign them out, or
// re-check who's currently signed in. Each one also updates the shared
// Redux store, so the rest of the app (sidebar, route guards, etc.)
// immediately sees the change.
export const login = async (email: string, password: string) => {
  const { data } = await authService.login(email, password);
  const user = { id: data.id, name: data.name, email: data.email, role: data.role };
  store.dispatch(setUser(user));
  return user;
};
export const logout = async () => {
  try {
    await authService.logout();
  } finally {
    // Clear the local "signed in" state and leave the page even if the
    // server call above failed — an offline sign-out should still work
    // from the user's point of view.
    store.dispatch(clearUser());
    window.location.replace("/login");
  }
};
// Asks the backend "who am I, if anyone?" and updates the store to match.
// Used when the app first loads, to pick up an existing session.
export const hydrateAuth = async () => {
  try {
    const { data } = await authService.me();
    const user = { id: data.id, name: data.name, email: data.email, role: data.role };
    store.dispatch(setUser(user));
    return user;
  } catch {
    store.dispatch(clearUser());
    return null;
  }
};
