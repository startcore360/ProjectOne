import { useCallback, useEffect, useState } from "react";
import {
  SUPPORT_SESSION_EXPIRY_KEY,
  SUPPORT_SESSION_PLATFORM_ORIGIN_KEY,
  SUPPORT_SESSION_TICKET_KEY,
  SUPPORT_SESSION_TOKEN_KEY,
} from "../../services/axios";

export type SupportSessionState = {
  token: string;
  tenantId: string | null;
  expiresAt: string | null;
  ticketNumber: string | null;
};

function readSession(): SupportSessionState | null {
  if (typeof window === "undefined") return null;
  const token = window.sessionStorage.getItem(SUPPORT_SESSION_TOKEN_KEY);
  if (!token) return null;
  let tenantId: string | null = null;
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    tenantId = JSON.parse(window.atob(payload)).tenant_id ?? null;
  } catch {
    return null;
  }
  return {
    token,
    tenantId,
    expiresAt: window.sessionStorage.getItem(SUPPORT_SESSION_EXPIRY_KEY),
    ticketNumber: window.sessionStorage.getItem(SUPPORT_SESSION_TICKET_KEY),
  };
}

export function useSupportSession() {
  const [session, setSession] = useState<SupportSessionState | null>(() => readSession());

  useEffect(() => {
    // The initial state already holds readSession(); only listen for the end event here.
    const onEnded = () => setSession(null);
    window.addEventListener("edu360:support-session-ended", onEnded);
    return () => window.removeEventListener("edu360:support-session-ended", onEnded);
  }, []);

  const endSupportSession = useCallback(() => {
    const platformOrigin = window.sessionStorage.getItem(SUPPORT_SESSION_PLATFORM_ORIGIN_KEY);
    if (window.opener && platformOrigin) {
      window.opener.postMessage({ type: "EDU360_SUPPORT_SESSION_END" }, platformOrigin);
    }
    window.sessionStorage.removeItem(SUPPORT_SESSION_TOKEN_KEY);
    window.sessionStorage.removeItem(SUPPORT_SESSION_EXPIRY_KEY);
    window.sessionStorage.removeItem(SUPPORT_SESSION_TICKET_KEY);
    window.sessionStorage.removeItem(SUPPORT_SESSION_PLATFORM_ORIGIN_KEY);
    setSession(null);
    window.dispatchEvent(new Event("edu360:support-session-ended"));
  }, []);

  // Read the clock in a timer, never during render: the timer fires when the session expires,
  // so the banner and hidden controls update at that moment without a page reload.
  const expiresAtMs = session?.expiresAt ? new Date(session.expiresAt).getTime() : null;
  const [expiredAtMs, setExpiredAtMs] = useState<number | null>(null);
  useEffect(() => {
    if (expiresAtMs === null) return undefined;
    const timer = window.setTimeout(() => setExpiredAtMs(expiresAtMs), Math.max(0, expiresAtMs - Date.now()));
    return () => window.clearTimeout(timer);
  }, [expiresAtMs]);
  const isExpired = expiresAtMs !== null && expiredAtMs === expiresAtMs;
  return {
    session,
    isSupportSession: !!session && !isExpired,
    tenantId: session?.tenantId ?? null,
    endSupportSession,
  };
}
