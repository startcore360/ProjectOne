/* eslint-disable react-refresh/only-export-components */
import type { BadgeTone } from "./Badge";
import { Badge } from "./Badge";

export interface StatusBadgeProps {
  value?: string;
  status?: string;
  label?: string;
  children?: React.ReactNode;
  className?: string;
}

export function humanizeStatus(status: string): string {
  if (!status) return "";
  const trimmed = status.trim();
  if (/^[A-Z0-9_]+$/.test(trimmed)) {
    return trimmed
      .replaceAll("_", " ")
      .toLowerCase()
      .replace(/\b\w/g, (char) => char.toUpperCase());
  }
  return trimmed;
}

const STATUS_TONES: Record<string, BadgeTone> = {
  // Success tones (active, completed, approved, paid, allowed)
  ACTIVE: "success",
  APPROVED: "success",
  PAID: "success",
  SUCCESS: "success",
  SUCCESSFUL: "success",
  ALLOWED: "success",
  COMPLETED: "success",
  EXECUTED: "success",
  AVAILABLE: "success",
  RELEASED: "success",
  OPEN: "success",
  HEALTHY: "success",

  // Warning tones (pending, trial, invited, overdue, draft)
  TRIAL: "warning",
  PENDING: "warning",
  PENDING_ACTIVATION: "warning",
  PENDING_APPROVAL: "warning",
  INVITED: "warning",
  OVERDUE: "warning",
  IN_PROGRESS: "warning",
  REQUESTED: "warning",
  DRAFT: "warning",
  SUBMITTED: "warning",
  SCHEDULED: "warning",
  DEGRADED: "warning",

  // Danger tones (failed, suspended, blocked, rejected, cancelled, rolled back, expired, deleted)
  SUSPENDED: "danger",
  BLOCKED: "danger",
  DENIED: "danger",
  DISALLOWED: "danger",
  FAILED: "danger",
  FAILURE: "danger",
  REJECTED: "danger",
  CANCELLED: "danger",
  CANCELED: "danger",
  ROLLED_BACK: "danger",
  ROLLBACK: "danger",
  EXPIRED: "danger",
  DELETED: "danger",
  LOCKED: "danger",
  TERMINATED: "danger",
  CRITICAL: "danger",
  HIGH: "danger",
};

export function getStatusTone(raw: string): BadgeTone {
  const normalized = raw.trim().toUpperCase().replaceAll(" ", "_");
  return STATUS_TONES[normalized] ?? "neutral";
}

export function StatusBadge({ value, status, label, children, className = "" }: StatusBadgeProps) {
  const rawValue = (value ?? status ?? "").trim();
  const displayVal = children ?? label ?? humanizeStatus(rawValue);
  const tone = getStatusTone(rawValue);

  return (
    <Badge tone={tone} className={className}>
      {displayVal}
    </Badge>
  );
}

// Alias for Status
export const Status = StatusBadge;
