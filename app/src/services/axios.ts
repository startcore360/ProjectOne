import axios from "axios";
import { getAccessToken } from "./tokenStorage";

// WHY the base URL is derived from window.location, not just VITE_API_BASE_URL: the backend
// tells tenants apart by the Host header on each request (LoginTenantResolver -
// abc.edu360.local / xyz.edu360.local / platform.edu360.local each resolve to a different
// tenant or the platform realm). A single fixed VITE_API_BASE_URL would send every request to
// the same host regardless of which tenant hostname the page was actually loaded from, which is
// exactly backwards - found by actually loading the app at abc.edu360.local and getting
// "Unknown sign-in address" because every call was still going to plain localhost. The backend
// always runs on port 8080 in dev regardless of which tenant hostname is used, so only the
// hostname needs to change, not the port. VITE_API_BASE_URL remains available as an explicit
// override (e.g. a deployed environment with a different port/origin entirely).
const inferredBaseUrl = `${window.location.protocol}//${window.location.hostname}:8080/api`;
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || inferredBaseUrl,
});

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default api;
