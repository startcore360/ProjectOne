import type { SessionResponse } from "../api/sessionsService";

/** Display name for a signed-in device: its recorded label, else its type. */
export function deviceTitle(session: SessionResponse): string {
  if (session.deviceName) return session.deviceName;
  if (session.deviceType === "PHONE") return "Phone";
  if (session.deviceType === "COMPUTER") return "Computer";
  return "Unknown device";
}

export type ActivityKind = "live" | "recent" | "idle";

// lastActiveAt is the last API call made from that device (the server notes it at most once a
// minute per session), so it is accurate to about a minute. A device that called the API within
// LIVE_WINDOW is treated as in use right now.
const LIVE_WINDOW_MS = 5 * 60 * 1000;
const RECENT_WINDOW_MS = 60 * 60 * 1000;

/**
 * Live status of a device: the current device is always live; another device is live if it was
 * active within the last 5 minutes, "recent" within the hour, otherwise idle (signed in but
 * not in use - e.g. a closed tab whose session has not yet timed out).
 */
export function activityStatus(session: SessionResponse, now: number): { kind: ActivityKind; label: string } {
  if (session.current) return { kind: "live", label: "Active now" };
  const last = session.lastActiveAt ? Date.parse(session.lastActiveAt) : NaN;
  if (Number.isNaN(last)) return { kind: "idle", label: "Idle" };
  const age = Math.max(0, now - last);
  if (age <= LIVE_WINDOW_MS) return { kind: "live", label: "Active now" };
  if (age <= RECENT_WINDOW_MS) {
    const minutes = Math.round(age / 60000);
    return { kind: "recent", label: `Active ${minutes} min ago` };
  }
  return { kind: "idle", label: "Idle" };
}
