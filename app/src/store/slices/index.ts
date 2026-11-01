// Re-exports the auth slice's pieces so other files can import from "./slices"
// instead of the exact file each piece lives in.
export { default as authReducer } from "./authSlice";
export { clearUser, setLoading, setUser } from "./authSlice";
export type { AuthUser, Role } from "./authSlice";
