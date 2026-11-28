import { useEffect, useState, type CSSProperties, type ReactNode } from "react";
import Button from "./Button";
import { Icon, type IconName } from "../../components/ui/Icon";
import type { ToastAction, ToastPosition, ToastVariant } from "./toastRegistry";

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
  popup?: boolean;
  /**
   * Controls only the auto-dismiss timer, independent of whether this toast
   * renders its own fixed-position wrapper (`alert`/`popup`). Defaults to
   * whatever `alert`/`popup` resolve to, so existing callers are unaffected.
   * A host component that provides its own fixed-position container (e.g.
   * ToastHost) should pass `autoDismiss={true}` while keeping
   * `alert={false} popup={false}` to avoid a nested fixed wrapper.
   */
  autoDismiss?: boolean;
  alertDuration?: number;
  onDismiss?: () => void;
  position?: ToastPosition;
};

const VARIANT: Record<
  ToastVariant,
  {
    iconName: IconName;
    border: string;
    bg: string;
    alertBg: string;
    titleColor: string;
  }
> = {
  info: {
    iconName: "info",
    border: "var(--color-status-info-border)",
    bg: "var(--color-status-info-bg)",
    alertBg: "var(--color-bg-surface)",
    titleColor: "var(--color-status-info-fg)",
  },
  success: {
    iconName: "check-circle",
    border: "var(--color-status-success-border)",
    bg: "var(--color-status-success-bg)",
    alertBg: "var(--color-bg-surface)",
    titleColor: "var(--color-status-success-fg)",
  },
  warning: {
    iconName: "alert",
    border: "var(--color-status-warning-border)",
    bg: "var(--color-status-warning-bg)",
    alertBg: "var(--color-bg-surface)",
    titleColor: "var(--color-status-warning-fg)",
  },
  error: {
    iconName: "error",
    border: "var(--color-status-danger-border)",
    bg: "var(--color-status-danger-bg)",
    alertBg: "var(--color-bg-surface)",
    titleColor: "var(--color-status-danger-fg)",
  },
  neutral: {
    iconName: "info",
    border: "var(--color-status-neutral-border)",
    bg: "var(--color-status-neutral-bg)",
    alertBg: "var(--color-bg-surface)",
    titleColor: "var(--color-status-neutral-fg)",
  },
};

const Toast = ({
  variant = "info",
  title,
  description,
  content,
  width = "w-full",
  action,
  showAction = true,
  alert = false,
  popup = false,
  autoDismiss,
  alertDuration = 4000,
  onDismiss,
  className,
  style,
}: ToastProps) => {
  const isAlert = alert || popup;
  const shouldAutoDismiss = autoDismiss ?? isAlert;
  const [visible, setVisible] = useState(true);
  const [animateIn, setAnimateIn] = useState(!isAlert);

  const dismiss = () => {
    setAnimateIn(false);
    setTimeout(() => {
      setVisible(false);
      onDismiss?.();
    }, 200);
  };

  useEffect(() => {
    if (!shouldAutoDismiss) return;

    // Trigger enter transition
    const enterTimer = setTimeout(() => setAnimateIn(true), 10);

    let exitTimer: ReturnType<typeof setTimeout> | undefined;
    const timer = setTimeout(() => {
      setAnimateIn(false);

      exitTimer = setTimeout(() => {
        setVisible(false);
        onDismiss?.();
      }, 200);
    }, alertDuration);

    return () => {
      clearTimeout(enterTimer);
      clearTimeout(timer);
      if (exitTimer) clearTimeout(exitTimer);
    };
  }, [shouldAutoDismiss, alertDuration, onDismiss]);

  if (shouldAutoDismiss && !visible) return null;

  const v = VARIANT[variant] ?? VARIANT.info;

  const card = (
    <div
      role="alert"
      aria-live="polite"
      data-testid="toast"
      onClick={onDismiss}
      className={`
        eds-toast eds-toast--${variant} ${isAlert ? "eds-toast--popup" : ""}
        flex items-start gap-[12px] px-[16px] py-[14px]
        border rounded-[8px]
        ${isAlert ? "w-full shadow-lg" : width}
        ${className ?? ""}
      `.trim()}
      style={{
        display: "flex",
        alignItems: "flex-start",
        gap: "var(--space-3)",
        padding: "var(--space-3) var(--space-4)",
        borderRadius: "8px",
        border: `1px solid ${v.border}`,
        background: isAlert ? v.alertBg : v.bg,
        boxShadow: isAlert
          ? "var(--elevation-2, 0 8px 24px rgba(0, 0, 0, 0.12))"
          : "none",
        width: isAlert ? "100%" : width,
        cursor: onDismiss ? "pointer" : "default",
        color: "var(--color-text)",
        ...style,
      }}
    >
      <div className="mt-[2px] shrink-0" style={{ flexShrink: 0, marginTop: "var(--space-1)", color: v.titleColor }}>
        <Icon name={v.iconName} size={18} />
      </div>

      <div className="flex-1 min-w-0" style={{ flex: 1, minWidth: 0 }}>
        {title && (
          <p
            className="text-[14px] font-semibold leading-[20px] mb-[2px] break-words"
            style={{
              fontSize: "14px",
              fontWeight: 650,
              lineHeight: "20px",
              marginBottom: description || content ? "2px" : "0",
              color: v.titleColor,
              wordBreak: "break-word",
            }}
          >
            {title}
          </p>
        )}

        {content ? (
          <div
            className="text-[12px] sm:text-[14px] font-normal leading-[18px] break-words"
            style={{
              fontSize: "13px",
              lineHeight: "18px",
              color: "var(--color-text-muted)",
              wordBreak: "break-word",
            }}
          >
            {content}
          </div>
        ) : description ? (
          <p
            className="text-[14px] font-normal leading-[18px] break-words"
            style={{
              fontSize: "13px",
              lineHeight: "18px",
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
        <div className="flex items-center pl-[12px]" style={{ display: "flex", alignItems: "center", paddingLeft: "var(--space-3)", flexShrink: 0 }}>
          <Button
            onClick={(e) => {
              e.stopPropagation();
              action.onClick();
            }}
            size="sm"
            variant="primary"
          >
            {action.label}
          </Button>
        </div>
      )}

      {(isAlert || onDismiss) && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            dismiss();
          }}
          aria-label="Dismiss"
          className="ml-2 shrink-0 mt-[2px] opacity-60 hover:opacity-100 transition-opacity"
          style={{
            background: "none",
            border: "none",
            cursor: "pointer",
            padding: "var(--space-1)",
            marginLeft: "var(--space-2)",
            marginTop: "var(--space-1)",
            color: "var(--color-text-muted)",
            opacity: 0.6,
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            borderRadius: "4px",
            flexShrink: 0,
          }}
          onMouseEnter={(e) => (e.currentTarget.style.opacity = "1")}
          onMouseLeave={(e) => (e.currentTarget.style.opacity = "0.6")}
        >
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
            <path
              d="M1 1l12 12M13 1L1 13"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
            />
          </svg>
        </button>
      )}
    </div>
  );

  if (!isAlert) return card;

  return (
    <div
      data-testid="toast-alert-wrapper"
      className={`
        fixed top-[60px] md:top-4 left-1/2 -translate-x-1/2
        z-[9999]
        w-[min(480px,calc(100vw-32px))]
        transition-all duration-200 ease-out
        ${animateIn ? "opacity-100 translate-y-0" : "opacity-0 -translate-y-2"}
      `}
      style={{
        position: "fixed",
        top: "clamp(16px, 5vw, 60px)",
        left: "50%",
        transform: animateIn ? "translateX(-50%) translateY(0)" : "translateX(-50%) translateY(-8px)",
        zIndex: 9999,
        width: "min(480px, calc(100vw - 32px))",
        opacity: animateIn ? 1 : 0,
        transition: "all 0.2s ease-out",
        pointerEvents: "auto",
      }}
    >
      {card}
    </div>
  );
};

export default Toast;
