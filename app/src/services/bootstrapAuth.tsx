import { store } from "../store";
import { clearUser, setLoading, setUser } from "../store/slices/authSlice";
import { authService } from "./modules/authService";
// Runs once when the app first loads. It asks the backend "am I already logged in?"
// and saves the answer to the store before the app shows any screen, so the login
// page or dashboard doesn't flash by mistake while we're still checking.
export const bootstrapAuth = async () => {
  store.dispatch(setLoading(true));
  try {
    const { data } = await authService.me();
    const user = { id: data.id, name: data.name, email: data.email, role: data.role };
    store.dispatch(setUser(user));
    return user;
  } catch {
    store.dispatch(clearUser());
    return null;
  } finally {
    store.dispatch(setLoading(false));
  }
};
