import api from "../../../services/axios";

// Mirrors api/.../selfservice/dto/SessionResponse.java. AUTH_DEVICES_MANAGE with SES-01: one
// entry per live sign-in of the caller, on any number of devices (one per browser). The current
// one (this browser) comes first.
export type DeviceType = "PHONE" | "COMPUTER" | "UNKNOWN";

export type SessionResponse = {
  sessionId: string | null;
  current: boolean;
  revocable: boolean;
  issuedAt: string | null;
  // Expiry of this browser's access token; null for other devices.
  expiresAt: string | null;
  // UNKNOWN = signed in before device details were recorded (no name/IP then).
  deviceType?: DeviceType;
  deviceName?: string | null;
  ipAddress?: string | null;
  lastActiveAt?: string | null;
};

export const sessionsService = {
  list: () => api.get<SessionResponse[]>("/me/sessions"),
  revokeCurrent: () => api.delete<void>("/me/sessions/current"),
  // Sign out another of the caller's own devices; the server answers 404 if it isn't theirs.
  revoke: (sessionId: string) => api.delete<void>(`/me/sessions/${encodeURIComponent(sessionId)}`),
};
