import { useEffect } from "react";
import { authService } from "../../services/modules/authService";
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "../../services/tokenStorage";
import { logout } from "../../services/authManager";
import { setLogoutReason } from "../../services/logoutNotice";
import { tenantPermissionsService } from "../profile/api/tenantPermissionsService";

export const LIVE_EVENT_NAME = "edu360:live-event";
export type LiveEnvelope = {
  type: string;
  id?: string;
  at?: string;
  payload?: Record<string, unknown>;
};

export function liveSocketUrl(): string {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  // Local development API is served on :8080; deployed environments use the page host /
  // reverse proxy. Tenant routing is hostname-based, never a fixed API override.
  const host = import.meta.env.DEV
    ? `${window.location.hostname}:8080`
    : window.location.host;
  return `${protocol}//${host}/v1/ws`;
}

function emitLiveEvent(event: LiveEnvelope) {
  window.dispatchEvent(new CustomEvent<LiveEnvelope>(LIVE_EVENT_NAME, { detail: event }));
}

export function useLiveSocket(tenantId: string | null | undefined, isPlatformUser: boolean): void {
  useEffect(() => {
    if (!tenantId || isPlatformUser) return undefined;
    let disposed = false;
    let socket: WebSocket | null = null;
    let reconnectTimer: number | null = null;
    let reconnectDelay = 1000;
    let refreshing = false;

    const authenticate = () => {
      const token = getAccessToken();
      if (socket?.readyState === WebSocket.OPEN && token) {
        socket.send(JSON.stringify({ type: "auth", token }));
      }
    };

    const reconnect = () => {
      if (disposed || reconnectTimer !== null) return;
      const jitter = 0.8 + Math.random() * 0.4;
      reconnectTimer = window.setTimeout(() => {
        reconnectTimer = null;
        connect();
      }, Math.min(30_000, reconnectDelay) * jitter);
      reconnectDelay = Math.min(30_000, reconnectDelay * 2);
    };

    const connect = () => {
      if (disposed) return;
      const token = getAccessToken();
      if (!token) return;
      socket = new WebSocket(liveSocketUrl());
      socket.onopen = () => {
        reconnectDelay = 1000;
        authenticate();
      };
      socket.onmessage = (message) => {
        let event: LiveEnvelope;
        try { event = JSON.parse(String(message.data)) as LiveEnvelope; }
        catch { return; }
        if (event.type === "ping") {
          if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "pong" }));
          return;
        }
        if (event.type === "reauth_required") {
          if (refreshing) return;
          refreshing = true;
          const refreshToken = getRefreshToken();
          if (!refreshToken) {
            setLogoutReason("Your session expired. Please sign in again.");
            void logout();
            return;
          }
          void authService.refresh(refreshToken).then(({ data }) => {
            setTokens(data.accessToken, data.refreshToken);
            authenticate();
          }).catch(() => {
            clearTokens();
            setLogoutReason("Your session expired. Please sign in again.");
            void logout();
          }).finally(() => { refreshing = false; });
          return;
        }
        emitLiveEvent(event);
        if (event.type === "PERMISSIONS_CHANGED") void tenantPermissionsService.reload();
        if (event.type === "SESSION_REVOKED") {
          setLogoutReason("Your session was revoked by an administrator.");
          void logout();
        }
      };
      socket.onclose = (event) => {
        socket = null;
        if (event.code === 4403) {
          setLogoutReason("This account cannot use the tenant live channel.");
          void logout();
          return;
        }
        if (event.code !== 4401 || getAccessToken()) reconnect();
      };
      socket.onerror = () => socket?.close();
    };

    const onTokenRefreshed = () => authenticate();
    window.addEventListener("edu360:token-refreshed", onTokenRefreshed);
    connect();
    return () => {
      disposed = true;
      window.removeEventListener("edu360:token-refreshed", onTokenRefreshed);
      if (reconnectTimer !== null) window.clearTimeout(reconnectTimer);
      socket?.close(1000, "logout");
    };
  }, [tenantId, isPlatformUser]);
}

export function useLiveEntity(entityType: string, handler: (event: LiveEnvelope) => void): void {
  useEffect(() => {
    const listener = (raw: Event) => {
      const event = (raw as CustomEvent<LiveEnvelope>).detail;
      if (event?.type === "ENTITY_CHANGED" && event.payload?.entityType === entityType) handler(event);
    };
    window.addEventListener(LIVE_EVENT_NAME, listener);
    return () => window.removeEventListener(LIVE_EVENT_NAME, listener);
  }, [entityType, handler]);
}
