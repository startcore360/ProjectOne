import { Badge } from "../../components/ui";

export interface StatusBadgeProps {
  value?: string;
  status?: string;
  className?: string;
}

export function StatusBadge({ value, status, className = "" }: StatusBadgeProps) {
  const rawValue = value ?? status ?? "";
  const displayVal = /^[A-Z0-9_]+$/.test(rawValue)
    ? rawValue.replaceAll("_", " ").toLowerCase().replace(/^\w/, (letter) => letter.toUpperCase())
    : rawValue;
  const tone =
    rawValue === "ACTIVE" || rawValue === "Active" || rawValue === "Success" || rawValue === "Allowed"
      ? "success"
      : rawValue === "Trial" || rawValue === "Invited" || rawValue === "PENDING_ACTIVATION"
      ? "warning"
      : rawValue === "Denied" || rawValue === "Suspended" || rawValue === "Disallowed" || rawValue === "BLOCKED"
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
