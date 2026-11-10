import type { ReactNode } from "react";
import { Icon, type IconName } from "../../components/ui/Icon";

export interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: IconName;
  action?: ReactNode;
  className?: string;
}

export function EmptyState({
  title,
  description,
  icon = "search",
  action,
  className = "",
}: EmptyStateProps) {
  return (
    <div
      className={`platform-empty-state ${className}`.trim()}
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "var(--space-8, 32px) var(--space-4, 16px)",
        textAlign: "center",
        borderRadius: "var(--radius-lg, 8px)",
        border: "1px dashed var(--color-border, #E2E8F0)",
        background: "var(--color-bg-subtle, #F8FAFC)",
        margin: "var(--space-4, 16px) 0",
      }}
    >
      <div
        style={{
          width: "44px",
          height: "44px",
          borderRadius: "50%",
          background: "var(--color-bg-muted, #EDF2F7)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          marginBottom: "var(--space-3, 12px)",
          color: "var(--color-text-muted, #718096)",
        }}
      >
        <Icon name={icon} size={22} />
      </div>
      <h3
        style={{
          fontSize: "1rem",
          fontWeight: 600,
          marginBottom: description ? "var(--space-1, 4px)" : "var(--space-3, 12px)",
          color: "var(--color-text, #1A202C)",
        }}
      >
        {title}
      </h3>
      {description && (
        <p
          style={{
            fontSize: "0.875rem",
            color: "var(--color-text-muted, #718096)",
            maxWidth: "380px",
            marginBottom: action ? "var(--space-4, 16px)" : 0,
            lineHeight: 1.45,
          }}
        >
          {description}
        </p>
      )}
      {action && <div>{action}</div>}
    </div>
  );
}

export default EmptyState;
