import axios from "axios";
import { getAccessToken } from "./tokenStorage";

// One shared axios instance used for every API call in the app, so the base URL and auth
// header only need to be set up once, here. There is no cookie session - the backend hands
// back a bearer token from /api/auth/login, and we attach it ourselves on every request.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api",
});

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default api;
