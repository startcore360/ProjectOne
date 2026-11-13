import type { ReactNode } from "react";
import { Icon } from "../../components/ui/Icon";
import { cn } from "../../lib/utils";

export interface NoticeProps {
  tone?: "info" | "danger" | "success" | "warning";
  children: ReactNode;
  className?: string;
  style?: React.CSSProperties;
}

export function Notice({ tone = "info", children, className, style }: NoticeProps) {
  const iconName = tone === "danger" || tone === "warning" ? "alert" : tone === "success" ? "check-circle" : "info";
  return (
    <div className={cn("eds-notice", `eds-notice--${tone}`, className)} role={tone === "danger" ? "alert" : "status"} style={style}>
      <span className="eds-notice__icon" aria-hidden="true"><Icon name={iconName} size={18} /></span>
      <div className="eds-notice__content">{children}</div>
    </div>
  );
}
export default Notice;
