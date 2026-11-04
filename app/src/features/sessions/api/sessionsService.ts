import api from "../../../services/axios";

// Mirrors api/.../selfservice/dto/SessionResponse.java exactly. AUTH_DEVICES_MANAGE
// (Sign-in epic, Scope: Self) - Phase 1 scope only: "the session(s) belonging to the
// authenticated principal" means exactly the ONE session the current access token's Keycloak
// "sid" claim belongs to, never a real multi-device list (that is Phase 2 - see the backend
// DTO's own class comment). Do not render this as if it were a device list.
export type SessionResponse = {
  sessionId: string | null;
  current: boolean;
  revocable: boolean;
  issuedAt: string | null;
  expiresAt: string | null;
};

export const sessionsService = {
  list: () => api.get<SessionResponse[]>("/me/sessions"),
  revokeCurrent: () => api.delete<void>("/me/sessions/current"),
};
