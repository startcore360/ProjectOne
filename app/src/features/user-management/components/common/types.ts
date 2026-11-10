import type { UserAccountStatus } from "../../api/userManagementService";

export type LoadState = "idle" | "loading" | "loaded" | "denied" | "error";

export function statusTone(status: UserAccountStatus): "success" | "warning" | "danger" {
  if (status === "ACTIVE") return "success";
  if (status === "PENDING_ACTIVATION") return "warning";
  return "danger";
}
