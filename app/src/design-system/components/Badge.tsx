import type { HTMLAttributes } from "react";
import { cn } from "../../lib/utils";

export type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info";

const tones: Record<BadgeTone, string> = {
  neutral: "eds-badge--neutral",
  success: "eds-badge--success",
  warning: "eds-badge--warning",
  danger: "eds-badge--danger",
  info: "eds-badge--info",
};

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
}

export function Badge({
  tone = "neutral",
  className,
  ...props
}: BadgeProps) {
  return <span {...props} className={cn("eds-badge", tones[tone], className)} />;
}

export default Badge;
