// E1-S7: when the server refuses an action with 403 TRIAL_EXPIRED, the shared axios interceptor
// calls announceTrialExpired. The app shell listens and keeps a read-only banner on screen. The fact
// is kept for this browser tab only, so a new sign-in after activation starts clean.
export const TRIAL_EXPIRED_EVENT = "edu360:trial-expired";
const KEY = "edu360.trialEndedAt";

export function announceTrialExpired(trialEndsAt: string | null): void {
  try {
    sessionStorage.setItem(KEY, trialEndsAt ?? "");
  } catch {
    /* storage may be unavailable; the event still reaches the shell */
  }
  window.dispatchEvent(new CustomEvent(TRIAL_EXPIRED_EVENT, { detail: trialEndsAt }));
}

/** undefined = no trial-expired answer seen in this tab; "" = seen, date unknown. */
export function readTrialExpired(): string | undefined {
  try {
    return sessionStorage.getItem(KEY) ?? undefined;
  } catch {
    return undefined;
  }
}

export function clearTrialExpired(): void {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}
