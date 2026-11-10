/**
 * A-02 (story 6, PO-3): a user who holds several roles and has not chosen the active one gets
 * 409 with code ACTIVE_ROLE_REQUIRED from the server. The shared axios interceptor announces it
 * with this window event, and the header RoleSwitcher brings the user to the role choice.
 */
export const ACTIVE_ROLE_REQUIRED_EVENT = "edu360:active-role-required";
export const ACTIVE_ROLE_REQUIRED_CODE = "ACTIVE_ROLE_REQUIRED";

export function isActiveRoleRequired(status: number | undefined, data: unknown): boolean {
  if (status !== 409 || !data || typeof data !== "object") return false;
  return (data as { code?: unknown }).code === ACTIVE_ROLE_REQUIRED_CODE;
}

export function announceActiveRoleRequired(): void {
  window.dispatchEvent(new CustomEvent(ACTIVE_ROLE_REQUIRED_EVENT));
}
