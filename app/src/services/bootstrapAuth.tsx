import { store } from "../store";
import { clearUser, setLoading, setUser } from "../store/slices/authSlice";

export const bootstrapAuth = async () => {
  store.dispatch(setLoading(true));

  try {
    const userJson = localStorage.getItem("edu360-user");
    const token = localStorage.getItem("edu360-token");

    if (userJson && token) {
      const user = JSON.parse(userJson);
      store.dispatch(setUser(user));
      return user;
    }

    store.dispatch(clearUser());
    return null;
  } catch (error) {
    localStorage.removeItem("edu360-token");
    localStorage.removeItem("edu360-user");
    store.dispatch(clearUser());
    return null;
  } finally {
    store.dispatch(setLoading(false));
  }
};
