import { useCallback } from "react";
import api from "../../services/axios";
import type { AppDispatch } from "../../store";
import { useAppSelector } from "../../store/hooks";
import { setPlatformPermissions } from "../../store/slices/platformPermissionsSlice";

// These gates only decide what the screen shows. The backend enforces the same rules on every call.
const PLATFORM_ROLES = [
  "PRODUCT_OWNER",
  "PLATFORM_ADMIN",
  "BILLING_MANAGER",
  "SUPPORT_MANAGER",
  "RELEASE_MANAGER",
  "DATA_PROTECTION_OFFICER",
];

export function canOpenPlatformConsole(platformRole: string | null | undefined): boolean {
  return !!platformRole && PLATFORM_ROLES.includes(platformRole);
}

// The standard rights, used until /platform/me/permissions has answered. Mirrors
// PlatformPermissionService on the backend.
const PLATFORM_ADMIN_ACTIONS = [
  "TENANT_CREATE",
  "TENANT_SETTINGS",
  "FEATURE_TOGGLE",
  "PLAN_MANAGE",
  "INTEGRATION_TYPES_MANAGE",
  "PLATFORM_AUDIT_VIEW",
  "USAGE_COUNTS_VIEW",
  "BACKUP_STATUS_VIEW",
  "SUPPORT_TICKET_MANAGE",
  "RELEASE_MANAGE",
];
const STANDARD_ACTIONS: Record<string, string[]> = {
  PRODUCT_OWNER: [
    ...PLATFORM_ADMIN_ACTIONS,
    "TENANT_ACTIVATE",
    "TENANT_SUSPEND_ARCHIVE",
    "PLATFORM_USER_MANAGE",
    "SECURITY_POLICY_SET",
    "DISCOUNT_APPLY",
    "INVOICE_MANAGE",
    "OVERDUE_FLAG",
    "SUPPORT_ACCESS_USE",
    "PLATFORM_REPORTS_VIEW",
    "TENANT_EXPORT_REQUEST",
    "TENANT_DELETION_REQUEST",
    "TENANT_DELETION_CONFIRM",
    "RBAC_MAPPING_CHECK",
  ],
  PLATFORM_ADMIN: PLATFORM_ADMIN_ACTIONS,
  BILLING_MANAGER: ["INVOICE_MANAGE", "OVERDUE_FLAG", "PLATFORM_REPORTS_VIEW"],
  SUPPORT_MANAGER: ["SUPPORT_TICKET_MANAGE", "SUPPORT_ACCESS_USE", "PLATFORM_REPORTS_VIEW"],
  RELEASE_MANAGER: ["RELEASE_MANAGE"],
  DATA_PROTECTION_OFFICER: [
    "TENANT_EXPORT_APPROVE",
    "TENANT_DELETION_APPROVE",
    "RETENTION_CHANGE_APPROVE",
    "APPROVAL_DELEGATE",
    "RBAC_MAPPING_CHECK",
  ],
};

export const PLATFORM_PAGE_ACTIONS = {
  tenants: [
    "TENANT_CREATE",
    "TENANT_SETTINGS",
    "TENANT_ACTIVATE",
    "TENANT_SUSPEND_ARCHIVE",
    "INVOICE_MANAGE",
    "SUPPORT_TICKET_MANAGE",
  ],
  plans: ["PLAN_MANAGE", "TENANT_CREATE", "INVOICE_MANAGE"],
  staff: ["PLATFORM_USER_MANAGE"],
  support: ["SUPPORT_ACCESS_USE", "SUPPORT_TICKET_MANAGE"],
  audit: ["PLATFORM_AUDIT_VIEW"],
  usage: ["USAGE_COUNTS_VIEW"],
  backup: ["BACKUP_STATUS_VIEW"],
  integrations: ["INTEGRATION_TYPES_MANAGE"],
  invoices: ["INVOICE_MANAGE", "OVERDUE_FLAG", "DISCOUNT_APPLY"],
  releases: ["RELEASE_MANAGE"],
  reports: ["PLATFORM_REPORTS_VIEW"],
  security: ["SECURITY_POLICY_SET"],
  tickets: ["SUPPORT_TICKET_MANAGE", "SUPPORT_ACCESS_USE"],
};

export function usePlatformAccess() {
  const user = useAppSelector((state) => state.auth.user);
  const stored = useAppSelector((state) => state.platformPermissions?.current);
  const current = stored && stored.userId === user?.id ? stored : null;
  const actions = current?.actions ?? STANDARD_ACTIONS[user?.platformRole ?? ""] ?? null;
  const actionsKey = actions?.join(",") ?? "";

  const can = useCallback((action: string) => actionsKey.split(",").includes(action), [actionsKey]);
  const canAny = useCallback(
    (list: string[]) => list.some((action) => actionsKey.split(",").includes(action)),
    [actionsKey],
  );

  return {
    role: user?.platformRole ?? null,
    can,
    canAny,
    customRoleName: current?.customRoleName ?? null,
    stepInRequired: current?.stepInRequired ?? [],
    steppedIn: current?.steppedIn ?? [],
  };
}

type MyPermissions = {
  actions?: string[];
  customRoleName?: string | null;
  stepInRequired?: string[];
  steppedIn?: string[];
};

// Fails quietly: the screen keeps the standard rights and the server still checks every call.
export async function loadPlatformPermissions(dispatch: AppDispatch, userId: string) {
  try {
    const res = await api.get<MyPermissions>("/platform/me/permissions");
    if (Array.isArray(res?.data?.actions)) {
      dispatch(
        setPlatformPermissions({
          userId,
          actions: res.data.actions,
          customRoleName: res.data.customRoleName ?? null,
          stepInRequired: res.data.stepInRequired ?? [],
          steppedIn: res.data.steppedIn ?? [],
        }),
      );
    }
  } catch {
    // Nothing to show the user here: every page reports its own errors.
  }
}
