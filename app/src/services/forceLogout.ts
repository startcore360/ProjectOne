import { setLogoutReason } from "./logoutNotice";

export const forceLogout = (reason = "session_expired") => {
  setLogoutReason(reason);
  window.location.replace("/login");
};
