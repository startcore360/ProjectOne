import { useEffect, useState, type CSSProperties, type ReactNode } from "react";
import { Icon, type IconName } from "../../components/ui/Icon";
import Button from "./Button";
import type { ToastAction, ToastVariant } from "./toastRegistry";

export type ToastProps = {
  variant?: ToastVariant;
  title?: string;
  description?: string;
  content?: ReactNode;
  width?: string;
  className?: string;
  style?: CSSProperties;
  action?: ToastAction;
  showAction?: boolean;
  alert?: boolean;
  alertDuration?: number;
  onDismiss?: () => void;
};

const VARIANT_CONFIG: Record<
  ToastVariant,
  {
    icon: IconName;
    bg: string;
    border: string;
    color: string;
  }
> = {
  info: {
    icon: "info",
    bg: "var(--color-status-info-bg, rgba(37, 99, 235, 0.08))",
    border: "var(--color-status-info-border, #3b82f6)",
    color: "var(--color-status-info-fg, #1d4ed8)",
  },
  success: {
    icon: "check-circle",
    bg: "var(--color-status-success-bg, rgba(16, 185, 129, 0.08))",
    border: "var(--color-status-success-border, #10b981)",
    color: "var(--color-status-success-fg, #047857)",
  },
  warning: {
    icon: "alert",
    bg: "var(--eds-color-warning-bg, rgba(245, 158, 11, 0.08))",
    border: "var(--eds-color-warning-border, #f59e0b)",
    color: "var(--eds-color-warning-text, #b45309)",
  },
  error: {
    icon: "error",
    bg: "var(--color-status-danger-bg, rgba(239, 68, 68, 0.08))",
    border: "var(--color-status-danger-border, #ef4444)",
    color: "var(--color-status-danger-fg, #b91c1c)",
  },
  neutral: {
    icon: "info",
    bg: "var(--color-bg-surface, #ffffff)",
    border: "var(--color-border-divider, #cbd5e1)",
    color: "var(--color-text, #0f172a)",
  },
};

export default function Toast({
  variant = "info",
  title,
  description,
  content,
  width = "100%",
  action,
  showAction = true,
  alert = false,
  alertDuration = 4000,
  onDismiss,
  className = "",
  style,
}: ToastProps) {
  const [visible, setVisible] = useState(true);
  const [animateIn, setAnimateIn] = useState(!alert);

  const dismiss = () => {
    setAnimateIn(false);
    setTimeout(() => {
      setVisible(false);
      onDismiss?.();
    }, 200);
  };

  useEffect(() => {
    if (!alert) return;

    // Trigger enter transition
    const enterTimer = setTimeout(() => setAnimateIn(true), 10);

    const timer = setTimeout(() => {
      setAnimateIn(false);
      const exitTimer = setTimeout(() => {
        setVisible(false);
        onDismiss?.();
      }, 200);
      return () => clearTimeout(exitTimer);
    }, alertDuration);

    return () => {
      clearTimeout(enterTimer);
      clearTimeout(timer);
    };
  }, [alert, alertDuration, onDismiss]);

  if (alert && !visible) return null;

  const config = VARIANT_CONFIG[variant] ?? VARIANT_CONFIG.info;

  const cardStyle: CSSProperties = {
    display: "flex",
    alignItems: "flex-start",
    gap: "var(--space-3, 12px)",
    padding: "var(--space-3, 12px) var(--space-4, 16px)",
    borderRadius: "var(--radius-md, 8px)",
    border: `1px solid ${config.border}`,
    background: alert ? "var(--color-bg-surface, #ffffff)" : config.bg,
    boxShadow: alert ? "var(--elevation-3, 0 10px 25px -5px rgba(0, 0, 0, 0.15))" : "none",
    width: alert ? "100%" : width,
    color: "var(--color-text)",
    ...style,
  };

  const card = (
    <div
      role="alert"
      aria-live="polite"
      data-testid="toast"
      className={`eds-toast eds-toast--${variant} ${className}`.trim()}
      style={cardStyle}
    >
      <div style={{ flexShrink: 0, marginTop: "2px", color: config.color }}>
        <Icon name={config.icon} size={18} />
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        {title && (
          <p
            style={{
              fontSize: "0.875rem",
              fontWeight: 600,
              lineHeight: "1.25rem",
              margin: "0 0 2px 0",
              color: config.color,
              wordBreak: "break-word",
            }}
          >
            {title}
          </p>
        )}

        {content ? (
          <div
            style={{
              fontSize: "0.875rem",
              lineHeight: "1.25rem",
              color: "var(--color-text-muted)",
              wordBreak: "break-word",
            }}
          >
            {content}
          </div>
        ) : description ? (
          <p
            style={{
              fontSize: "0.875rem",
              lineHeight: "1.25rem",
              margin: 0,
              color: "var(--color-text-muted)",
              wordBreak: "break-word",
            }}
          >
            {description}
          </p>
        ) : null}
      </div>

      {showAction && action && (
        <div style={{ display: "flex", alignItems: "center", paddingLeft: "var(--space-3, 12px)", flexShrink: 0 }}>
          <Button
            size="sm"
            variant="secondary"
            onClick={action.onClick}
          >
            {action.label}
          </Button>
        </div>
      )}

      {alert && (
        <button
          type="button"
          onClick={dismiss}
          aria-label="Dismiss"
          style={{
            background: "none",
            border: "none",
            cursor: "pointer",
            padding: "4px",
            marginLeft: "var(--space-2, 8px)",
            marginTop: "2px",
            color: "var(--color-text-muted)",
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            borderRadius: "var(--radius-sm, 4px)",
            opacity: 0.7,
            transition: "opacity 0.15s ease",
          }}
          onMouseEnter={(e) => (e.currentTarget.style.opacity = "1")}
          onMouseLeave={(e) => (e.currentTarget.style.opacity = "0.7")}
        >
          <Icon name="x" size={14} />
        </button>
      )}
    </div>
  );

  if (!alert) return card;

  return (
    <div
      style={{
        width: "100%",
        transition: "all 0.2s ease-out",
        opacity: animateIn ? 1 : 0,
        transform: animateIn ? "translateY(0)" : "translateY(-8px)",
      }}
    >
      {card}
    </div>
  );
}
