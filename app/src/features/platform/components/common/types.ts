import type { PlatformRole, PlatformUserItem } from "../../api/platformUserService";

export type Tenant = {
  id: string;
  name: string;
  type: string;
  plan: string;
  status: "Active" | "Trial" | "Suspended";
  gates: number;
  updated: string;
};

export type PlatformUser = {
  id: string;
  name: string;
  email: string;
  role: string;
  status: "Active" | "Invited" | "Suspended";
  lastActive: string;
};

export type AuditEvent = {
  id: string;
  when: string;
  actor: string;
  action: string;
  target: string;
  outcome: "Success" | "Denied";
};

export type FetchStatus = "loading" | "success" | "error";

export const initialTenants: Tenant[] = [];

export const initialUsers: PlatformUser[] = [];

export const initialAudit: AuditEvent[] = [];

export const PLATFORM_ROLE_LABELS: Record<PlatformRole, string> = {
  PRODUCT_OWNER: "Product Owner",
  PLATFORM_ADMIN: "Platform Admin",
  BILLING_MANAGER: "Billing Manager",
  SUPPORT_MANAGER: "Support Manager",
  RELEASE_MANAGER: "Release Manager",
  DATA_PROTECTION_OFFICER: "Data Protection Officer",
};

export function mapPlatformUser(item: PlatformUserItem): PlatformUser {
  return {
    id: item.platformUserId,
    name: item.fullName,
    email: item.email,
    role: PLATFORM_ROLE_LABELS[item.platformRole] ?? item.platformRole,
    status: item.status === "ACTIVE" ? "Active" : "Suspended",
    lastActive: item.lastLoginAt ? new Date(item.lastLoginAt).toLocaleString() : "Never logged in",
  };
}

export function formatTimestamp(value: string | null): string {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

export function titleCaseOutcome(outcome: string): string {
  const lower = outcome.toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

export function readErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === "object" && "response" in err) {
    const response = (err as { response?: { data?: { error?: string } } }).response;
    if (response?.data?.error) return response.data.error.replace("STEP_IN_REQUIRED: ", "");
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}

export type UsageMetric = { metricCode: string; value: number | null; asOfDate: string | null };

export type UsageCounts = {
  totalTenantCount: number;
  metrics: UsageMetric[];
  totalUserCountGap: string | null;
};

export const USAGE_METRIC_LABELS: Record<string, string> = {
  LOGINS: "Logins",
  ATTENDANCE_MARKED: "Attendance marked",
  FEES_PAID_ONLINE: "Fees paid online",
};

