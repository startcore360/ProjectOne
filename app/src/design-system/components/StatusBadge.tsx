import { Badge } from "../../components/ui";

export interface StatusBadgeProps {
  value?: string;
  status?: string;
  className?: string;
}

export function StatusBadge({ value, status, className = "" }: StatusBadgeProps) {
  const displayVal = value ?? status ?? "";
  const tone =
    displayVal === "ACTIVE" || displayVal === "Active" || displayVal === "Success" || displayVal === "Allowed"
      ? "success"
      : displayVal === "Trial" || displayVal === "Invited" || displayVal === "PENDING_ACTIVATION"
      ? "warning"
      : displayVal === "Denied" || displayVal === "Suspended" || displayVal === "Disallowed" || displayVal === "BLOCKED"
      ? "danger"
      : "neutral";

  return (
    <Badge tone={tone} className={className}>
      {displayVal}
    </Badge>
  );
}

// Alias for Status
export const Status = StatusBadge;
