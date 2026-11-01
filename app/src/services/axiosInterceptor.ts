import api from "./axios";
import { store } from "../store";
import { clearUser } from "../store/slices/authSlice";
// Watches every API response for a 401 (meaning "your login has expired or
// you were never signed in"). When that happens, it signs the user out on
// this side too and sends them to /login — otherwise the app would keep
// thinking they're signed in even though the server has already stopped
// trusting their session.
export const setupAxiosInterceptors = () => {
  api.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error?.response?.status === 401) {
        store.dispatch(clearUser());
        if (window.location.pathname !== "/login") window.location.replace("/login");
      }
      return Promise.reject(error);
    },
  );
};
