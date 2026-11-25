import type { PlatformRole, PlatformUserItem } from "../../api/platformUserService";

export type Tenant = {
  id: string;
  name: string;
  type: string;
  plan: string;
  planId?: string;
  status: "Active" | "Trial" | "Suspended" | "Archived" | "Setup failed";
  subdomain?: string;
  trialEndsAt?: string;
  gates: number;
  updated: string;
  suspendedAt?: string;
  archivedAt?: string;
  /** E8-S5: row version from the server, sent back as If-Match on activate / suspend / archive. */
  rowVersion?: number;
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
    const response = (err as { response?: { data?: { error?: string; message?: string; code?: string } } }).response;
    const raw = response?.data?.error ?? response?.data?.message ?? response?.data?.code;
    if (raw) {
      if (/does not hold action\s+[A-Z0-9_]+/i.test(raw) || /does not hold permission\s+[A-Z0-9_]+/i.test(raw)) {
        return "Your current role does not have permission to perform this action. Ask your administrator to review your access.";
      }
      if (/BUSINESS_RULE_VIOLATION/i.test(raw) || /violates a business rule constraint/i.test(raw)) {
        return "The requested operation violates a business rule constraint. Please contact your Platform Administrator to review system constraints.";
      }
      if (/SUPPORT_TICKET_CLOSED/i.test(raw)) {
        return "This support ticket is already closed and cannot be updated or escalated. If further action is needed, please open a new ticket.";
      }
      if (/INVALID_SUPPORT_TICKET_ESCALATION_TARGET/i.test(raw)) {
        return "Invalid escalation target. Tickets can only be escalated to Engineering or Release Manager.";
      }
      if (/SUPPORT_TICKET_ESCALATION_NOTE_REQUIRED/i.test(raw)) {
        return "An escalation note explaining why this ticket needs escalation is required. Provide details on the issue and reason for routing.";
      }
      if (/ck_support_ticket_r1|resolution_note is required/i.test(raw)) {
        return "A resolution or closing note is required when marking a ticket as Resolved or Closed (Rule ck_support_ticket_r1). Please enter a note before submitting.";
      }
      if (/ck_platform_notification_priority/i.test(raw)) {
        return "Escalation notification failed due to an invalid priority constraint. System priority has been adjusted to normal. If this persists, contact Engineering.";
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
    if (/BUSINESS_RULE_VIOLATION/i.test(err.message) || /violates a business rule/i.test(err.message)) {
      return "The requested operation violates a business rule constraint. If you believe this is in error or need an exception, please escalate this ticket to Engineering or contact your Platform Administrator.";
    }
    const safe = err.message.replace(/\s*(?:please contact support quoting\s*)?correlation\s*id\s*:\s*[a-f0-9-]{16,}/ig, "").trim();
    if (safe && !/^[A-Z][A-Z0-9_]{2,}$/.test(safe)) return safe;
  }
  return fallback;
}

export type UsageMetric = { metricCode: string; value: number | null; asOfDate: string | null };

export type UsageCounts = {
  totalTenantCount: number;
  tenantsInAnalytics: number;
  tenantsOptedOut: number;
  metrics: UsageMetric[];
};

export const USAGE_METRIC_LABELS: Record<string, string> = {
  LOGINS: "Logins",
  ATTENDANCE_MARKED: "Attendance marked",
  FEES_PAID_ONLINE: "Fees paid online",
  ACTIVE_USERS: "Active users",
};

