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
    const response = (err as { response?: { data?: { error?: string; message?: string } } }).response;
    const raw = response?.data?.error ?? response?.data?.message;
    if (raw) {
      if (/does not hold action\s+[A-Z0-9_]+/i.test(raw) || /does not hold permission\s+[A-Z0-9_]+/i.test(raw)) {
        return "Your current role does not have permission to perform this action. Ask your administrator to review your access.";
      }
      const safe = raw
        .replace(/^STEP_IN_REQUIRED:\s*/i, "")
        .replace(/\s*(?:please contact support quoting\s*)?correlation\s*id\s*:\s*[a-f0-9-]{16,}/ig, "")
        .replace(/\s*\(?(?:code|error)\s*:\s*[A-Z][A-Z0-9_]{2,}\)?/g, "")
        .trim();
      if (!safe || /^[A-Z][A-Z0-9_]{2,}$/.test(safe) || /org\.springframework|java\./.test(safe)) return fallback;
      return safe;
    }
  }
  if (err instanceof Error && err.message && !err.message.startsWith("Request failed with status code")) {
    const safe = err.message.replace(/\s*(?:please contact support quoting\s*)?correlation\s*id\s*:\s*[a-f0-9-]{16,}/ig, "").trim();
    if (safe && !/^[A-Z][A-Z0-9_]{2,}$/.test(safe)) return safe;
  }
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

