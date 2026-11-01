import axios from "axios";
// One shared axios instance used for every API call in the app, so the base
// URL and cookie settings only need to be set up once, here.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api",
  // Required so the browser sends the login session cookie with every
  // request — without this, the backend would treat every call as signed out.
  withCredentials: true,
});
export default api;
