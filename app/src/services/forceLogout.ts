import { setLogoutReason } from "./logoutNotice";

// Used when we need to kick the user out right now (for example, their session expired).
// It saves why we're logging them out, then sends them to the login page.
export const forceLogout = (reason = "session_expired") => {
  // We save the reason before leaving the page, because the redirect below reloads
  // the browser page and would otherwise wipe out any in-memory message.
  setLogoutReason(reason);
  window.location.replace("/login");
};
