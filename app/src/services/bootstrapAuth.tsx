import { store } from "../store";
import { setLoading } from "../store/slices/authSlice";
import { hydrateAuth } from "./authManager";
// Runs once when the app first loads, before React even renders (see main.tsx). Checks
// whether a stored access token still represents a valid session and saves the answer to the
// store first, so the login page or dashboard doesn't flash by mistake while we're checking.
export const bootstrapAuth = async () => {
  store.dispatch(setLoading(true));
  try {
    return await hydrateAuth();
  } finally {
    store.dispatch(setLoading(false));
  }
};
