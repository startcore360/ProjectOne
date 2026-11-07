import type { ReactNode } from "react";
import { Icon } from "../../components/ui/Icon";

export interface NoticeProps {
  tone?: "info" | "danger" | "success" | "warning";
  children: ReactNode;
  className?: string;
  style?: React.CSSProperties;
}

export function Notice({ tone = "info", children, className = "", style: customStyle }: NoticeProps) {
  const toneStyle =
    tone === "danger"
      ? {
          background: "var(--color-status-danger-bg)",
          borderColor: "var(--color-status-danger-border)",
          color: "var(--color-status-danger-fg)",
        }
      : tone === "warning"
      ? {
          background: "var(--eds-color-warning-bg, #fffbeb)",
          borderColor: "var(--eds-color-warning-border, #f59e0b)",
          color: "var(--eds-color-warning-text, #92400e)",
        }
      : tone === "success"
      ? {
          background: "var(--color-status-success-bg)",
          borderColor: "var(--color-status-success-border)",
          color: "var(--color-status-success-fg)",
        }
      : {
          background: "var(--color-bg-subtle)",
          borderColor: "var(--color-border-divider)",
          color: "var(--color-text)",
        };

  const iconName = tone === "danger" ? "alert" : tone === "success" ? "check-circle" : tone === "warning" ? "alert" : "info";

  return (
    <div
      className={`platform-inline-notice ${className}`.trim()}
      role={tone === "danger" ? "alert" : "status"}
      style={{
        display: "flex",
        alignItems: "flex-start",
        gap: "var(--space-3)",
        lineHeight: "1.5",
        ...toneStyle,
        ...customStyle,
      }}
    >
      <span style={{ flexShrink: 0, marginTop: "2px" }} aria-hidden="true">
        <Icon name={iconName} size={16} />
      </span>
      <div style={{ flex: 1 }}>{children}</div>
    </div>
  );
}
