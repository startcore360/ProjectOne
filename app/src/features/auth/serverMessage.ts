import axios from "axios";
import { getApiErrorMessage } from "../../services/apiHelper";

// The shared helper turns 401/403/409 into generic text ("session expired", "record changed"),
// which would be wrong for the two-step login screens (wrong password, cannot reset your own,
// no authenticator). Prefer the server's own sentence; fall back to the shared helper.
export const serverMessage = (err: unknown): string =>
  (axios.isAxiosError<{ error?: string }>(err) ? err.response?.data?.error : undefined) ?? getApiErrorMessage(err);
