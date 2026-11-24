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
  alertDuration?: number;
  onDismiss?: () => void;
  position?: ToastPosition;
};

const VARIANT: Record<
  ToastVariant,
  {
    iconName: IconName;
    container: string;
    alertContainer: string;
    titleClass: string;
    descClass: string;
    border: string;
    bg: string;
    alertBg: string;
    titleColor: string;
  }
> = {
  info: {
    iconName: "info",
    container: "bg-[rgba(37,99,235,0.1)] border-[#2563EB]",
    alertContainer: "bg-white border-[#2563EB]",
    titleClass: "text-[#2563EB]",
    descClass: "text-[var(--color-text-secondary)]",
    border: "#2563EB",
    bg: "rgba(37, 99, 235, 0.1)",
    alertBg: "var(--color-bg-surface, #ffffff)",
    titleColor: "#2563EB",
  },
  success: {
    iconName: "check-circle",
    container: "bg-[#ECFDF3] border-[#16A34A]",
    alertContainer: "bg-white border-[#16A34A]",
    titleClass: "text-[#15803D]",
    descClass: "text-[var(--color-text-secondary)]",
    border: "#16A34A",
    bg: "#ECFDF3",
    alertBg: "var(--color-bg-surface, #ffffff)",
    titleColor: "#15803D",
  },
  warning: {
    iconName: "alert",
    container: "bg-[#F59E0B]/10 border border-[#F59E0B]",
    alertContainer: "bg-white border-[#F59E0B]",
    titleClass: "text-[#F59E0B]",
    descClass: "text-[var(--color-text-secondary)]",
    border: "#F59E0B",
    bg: "rgba(245, 158, 11, 0.1)",
    alertBg: "var(--color-bg-surface, #ffffff)",
    titleColor: "#F59E0B",
  },
  error: {
    iconName: "error",
    container: "bg-white border-[#D1D1D6]",
    alertContainer: "bg-white border-[#D1D1D6]",
    titleClass: "text-[#DC2626]",
    descClass: "text-[#DC2626]",
    border: "#DC2626",
    bg: "var(--color-bg-surface, #ffffff)",
    alertBg: "var(--color-bg-surface, #ffffff)",
    titleColor: "#DC2626",
  },
  neutral: {
    iconName: "info",
    container: "bg-white border-[#D1D1D6]",
    alertContainer: "bg-white border-[#D1D1D6]",
    titleClass: "text-[var(--color-text-primary)]",
    descClass: "text-[var(--color-text-secondary)]",
    border: "#D1D1D6",
    bg: "var(--color-bg-surface, #ffffff)",
    alertBg: "var(--color-bg-surface, #ffffff)",
    titleColor: "var(--color-text, #0f172a)",
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
  alertDuration = 4000,
  onDismiss,
  className,
  style,
}: ToastProps) => {
  const isAlert = alert || popup;
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
    if (!isAlert) return;

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
  }, [isAlert, alertDuration, onDismiss]);

  if (isAlert && !visible) return null;

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
        ${isAlert ? v.alertContainer : v.container}
        ${className ?? ""}
      `.trim()}
      style={{
        display: "flex",
        alignItems: "flex-start",
        gap: "12px",
        padding: "14px 16px",
        borderRadius: "8px",
        border: `1px solid ${v.border}`,
        background: isAlert ? v.alertBg : v.bg,
        boxShadow: isAlert
          ? "0 12px 32px -4px rgba(0, 0, 0, 0.18), 0 4px 12px -2px rgba(0, 0, 0, 0.08)"
          : "none",
        width: isAlert ? "100%" : width,
        cursor: onDismiss ? "pointer" : "default",
        color: "var(--color-text, #0f172a)",
        ...style,
      }}
    >
      <div className="mt-[2px] shrink-0" style={{ flexShrink: 0, marginTop: "2px", color: v.titleColor }}>
        <Icon name={v.iconName} size={18} />
      </div>

      <div className="flex-1 min-w-0" style={{ flex: 1, minWidth: 0 }}>
        {title && (
          <p
            className={`text-[14px] font-semibold leading-[20px] mb-[2px] break-words ${v.titleClass}`}
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
            className={`text-[12px] sm:text-[14px] font-normal leading-[18px] break-words ${v.descClass}`}
            style={{
              fontSize: "13px",
              lineHeight: "18px",
              color: "var(--color-text-muted, #64748b)",
              wordBreak: "break-word",
            }}
          >
            {content}
          </div>
        ) : description ? (
          <p
            className={`text-[14px] font-normal leading-[18px] break-words ${v.descClass}`}
            style={{
              fontSize: "13px",
              lineHeight: "18px",
              margin: 0,
              color: "var(--color-text-muted, #64748b)",
              wordBreak: "break-word",
            }}
          >
            {description}
          </p>
        ) : null}
      </div>

      {showAction && action && (
        <div className="flex items-center pl-[12px]" style={{ display: "flex", alignItems: "center", paddingLeft: "12px", flexShrink: 0 }}>
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
          className="ml-2 shrink-0 mt-[2px] opacity-40 hover:opacity-80 transition-opacity"
          style={{
            background: "none",
            border: "none",
            cursor: "pointer",
            padding: "4px",
            marginLeft: "8px",
            marginTop: "2px",
            color: "currentColor",
            opacity: 0.5,
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            borderRadius: "4px",
            flexShrink: 0,
          }}
          onMouseEnter={(e) => (e.currentTarget.style.opacity = "0.9")}
          onMouseLeave={(e) => (e.currentTarget.style.opacity = "0.5")}
        >
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
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
