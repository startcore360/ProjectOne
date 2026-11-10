import axios from "axios";
import { getAccessToken } from "./tokenStorage";

// Tenant identity is resolved from the hostname. In local Vite development the API
// runs on port 8080; deployed builds use the same-origin reverse-proxied /api path.
// Do not configure one fixed VITE_API_BASE_URL for a multi-tenant deployment.
const inferredBaseUrl = import.meta.env.DEV
  ? `${window.location.protocol}//${window.location.hostname}:8080/api`
  : `${window.location.origin}/api`;
const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL || inferredBaseUrl;
const api = axios.create({
  baseURL: configuredBaseUrl,
  // This application authenticates with explicit bearer headers, not ambient cookies.
  withCredentials: false,
});

const apiOrigin = new URL(configuredBaseUrl, window.location.origin).origin;

api.interceptors.request.use((config) => {
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

  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  } else {
    delete config.headers.Authorization;
  }
  return config;
});

export default api;
