// A tiny helper for passing a "why did you get logged out" message across a page
// reload. We use sessionStorage (not a React variable) because forceLogout reloads
// the whole page, which would otherwise erase any message held only in memory.
const LOGOUT_REASON_KEY = "logoutReason";

export const setLogoutReason = (reason: string) => {
  sessionStorage.setItem(LOGOUT_REASON_KEY, reason);
};

export const getLogoutReason = () => sessionStorage.getItem(LOGOUT_REASON_KEY);

export const clearLogoutReason = () => {
  sessionStorage.removeItem(LOGOUT_REASON_KEY);
};
