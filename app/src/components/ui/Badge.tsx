import type { HTMLAttributes } from "react";
import { cn } from "../../lib/utils";

// A small colored label pill (e.g. "Active", "Failed"). The color is picked
// by "tone" instead of writing CSS classes everywhere it's used.
type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info";

const tones: Record<BadgeTone, string> = {
  neutral: "eds-badge--neutral",
  success: "eds-badge--success",
  warning: "eds-badge--warning",
  danger: "eds-badge--danger",
  info: "eds-badge--info",
};

export function Badge({
  tone = "neutral",
  className,
  ...props
}: HTMLAttributes<HTMLSpanElement> & { tone?: BadgeTone }) {
  return <span {...props} className={cn("eds-badge", tones[tone], className)} />;
}
