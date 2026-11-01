const LOGOUT_REASON_KEY = "logoutReason";

export const setLogoutReason = (reason: string) => {
  sessionStorage.setItem(LOGOUT_REASON_KEY, reason);
};

export const getLogoutReason = () => sessionStorage.getItem(LOGOUT_REASON_KEY);

export const clearLogoutReason = () => {
  sessionStorage.removeItem(LOGOUT_REASON_KEY);
};
