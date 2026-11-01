// Re-exports authService (and its response type) so other files can import it
// from "./modules" instead of the exact file it lives in.
export { authService } from "./authService";
export type { AuthResponse } from "./authService";
