import axios from "axios";
import { getAccessToken } from "./tokenStorage";

export const SUPPORT_SESSION_TOKEN_KEY = "edu360.support-session.token";
export const SUPPORT_SESSION_EXPIRY_KEY = "edu360.support-session.expires-at";
export const SUPPORT_SESSION_TICKET_KEY = "edu360.support-session.ticket-number";
export const SUPPORT_SESSION_PLATFORM_ORIGIN_KEY = "edu360.support-session.platform-origin";
export const SUPPORT_SESSION_OPENED_TENANT_ORIGIN_KEY = "edu360.support-session.opened-tenant-origin";

// A platform tab hands the short-lived token to this tenant tab in the URL fragment.
// Fragments are not sent to the web server or included in Referer headers.
if (typeof window !== "undefined" && window.location.hash.includes("support_session=")) {
  const fragment = new URLSearchParams(window.location.hash.slice(1));
  const supportToken = fragment.get("support_session");
  if (supportToken) {
    window.sessionStorage.setItem(SUPPORT_SESSION_TOKEN_KEY, supportToken);
    const expiresAt = fragment.get("expires_at");
    const ticketNumber = fragment.get("ticket_number");
    if (expiresAt) window.sessionStorage.setItem(SUPPORT_SESSION_EXPIRY_KEY, expiresAt);
    if (ticketNumber) window.sessionStorage.setItem(SUPPORT_SESSION_TICKET_KEY, ticketNumber);
    const platformOrigin = fragment.get("platform_origin");
    if (platformOrigin) window.sessionStorage.setItem(SUPPORT_SESSION_PLATFORM_ORIGIN_KEY, platformOrigin);
    window.history.replaceState(null, "", window.location.pathname + window.location.search);
  }
}

// Tenant identity is resolved by the request hostname. In development the API listens on
// port 8080 of that hostname; in production the reverse proxy serves /api on the same origin.
// A production VITE_API_BASE_URL override is deliberately ignored: one fixed API host would
// collapse every tenant onto the same Host header and break tenant resolution (P3).
const inferredBaseUrl = import.meta.env.DEV
  ? `${window.location.protocol}//${window.location.hostname}:8080/api`
  : `${window.location.origin}/api`;
// Keep the explicit override as a local-development escape hatch only. Resolve relative values
// against the page origin so the origin check below always compares absolute URLs.
const configuredBaseUrl = new URL(
  import.meta.env.DEV ? (import.meta.env.VITE_API_BASE_URL || inferredBaseUrl) : inferredBaseUrl,
  window.location.origin,
)
  .toString()
  .replace(/\/$/, "");
const api = axios.create({
  baseURL: configuredBaseUrl,
  // This application authenticates with explicit bearer headers, not ambient cookies.
  withCredentials: false,
});

const apiOrigin = new URL(configuredBaseUrl, window.location.origin).origin;

api.interceptors.request.use((config) => {
  // Normalize URL path to prevent duplicate /api/api or /api/v1 prefixes when combined with baseURL
  if (config.url) {
    if (config.url.startsWith("/api/")) {
      config.url = config.url.substring(4);
    } else if (config.url.startsWith("/v1/")) {
      config.url = config.url.substring(3);
    }
  }

  // Never attach an EDU 360 bearer token to an absolute URL on another origin.
  // Axios accepts absolute request URLs even when a baseURL is configured.
  const requestUrl = new URL(config.url ?? "", config.baseURL ?? configuredBaseUrl);
  if (requestUrl.origin !== apiOrigin) {
    throw new Error("Blocked API request to an untrusted origin.");
  }

  // OD-09 correlation tracing: assign UUID to each client request if not already present
  if (!config.headers["X-Correlation-ID"]) {
    config.headers["X-Correlation-ID"] = typeof crypto !== "undefined" && crypto.randomUUID
      ? crypto.randomUUID()
      : `req-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
  }

  // Login and refresh authenticate using their request bodies; neither needs an
  // existing access token. In particular, do not forward an expired token to refresh.
  const requestPath = requestUrl.pathname.replace(/\/+$/, "");
  const isAuthBootstrapRequest =
    requestPath.endsWith("/auth/login") || requestPath.endsWith("/auth/refresh");
  if (isAuthBootstrapRequest) {
    delete config.headers.Authorization;
    return config;
  }

  const supportToken = window.sessionStorage.getItem(SUPPORT_SESSION_TOKEN_KEY);
  const supportExpiry = window.sessionStorage.getItem(SUPPORT_SESSION_EXPIRY_KEY);
  const supportSessionActive = !!supportToken
    && (!supportExpiry || new Date(supportExpiry).getTime() > Date.now());
  if (supportToken && supportSessionActive) {
    if (/^\/api\/tenants\/[^/]+(?:\/|$)/.test(requestUrl.pathname)) {
      config.headers.Authorization = `Bearer ${supportToken}`;
    } else {
      delete config.headers.Authorization;
    }
    return config;
  }
  if (supportToken && !supportSessionActive) {
    window.sessionStorage.removeItem(SUPPORT_SESSION_TOKEN_KEY);
    window.sessionStorage.removeItem(SUPPORT_SESSION_EXPIRY_KEY);
    window.sessionStorage.removeItem(SUPPORT_SESSION_TICKET_KEY);
    window.sessionStorage.removeItem(SUPPORT_SESSION_PLATFORM_ORIGIN_KEY);
  }

  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  } else {
    delete config.headers.Authorization;
  }
  return config;
});

export default api;
