import { type ReactNode } from "react";
import { Icon } from "../../components/ui/Icon";
import { cn } from "../../lib/utils";
import Toast from "./Toast";
import type { ToastPosition, ToastVariant } from "./toastRegistry";

export interface NoticeProps {
  tone?: "info" | "danger" | "success" | "warning";
  children: ReactNode;
  className?: string;
  style?: React.CSSProperties;
  /**
   * When true (default for tone="success"), renders as an elevated top popup toast message floating upon the screen.
   */
  popup?: boolean;
  /**
   * Alias for popup / toast alert mode.
   */
  alert?: boolean;
  /**
   * Explicitly keep the notice inline within the page instead of popping up.
   */
  inline?: boolean;
  /**
   * Alias for popup.
   */
  asToast?: boolean;
  /**
   * Placement on screen when popup is true. Defaults to "top".
   */
  position?: ToastPosition;
  onDismiss?: () => void;
  /**
   * Automatically dismiss popup notice after duration. Defaults to true when popup.
   */
  autoDismiss?: boolean;
  /**
   * Duration in ms before auto-dismissing. Defaults to 4000ms.
   */
  duration?: number;
}

// Literal class names: Tailwind removes rules from components.css whose class it cannot find
// as a plain string in the source, so a dynamic `eds-notice--${tone}` lost the tone colours.
const TONE_CLASS: Record<NonNullable<NoticeProps["tone"]>, string> = {
  info: "eds-notice--info",
  success: "eds-notice--success",
  warning: "eds-notice--warning",
  danger: "eds-notice--danger",
};

const VARIANT_MAP: Record<NonNullable<NoticeProps["tone"]>, ToastVariant> = {
  info: "info",
  success: "success",
  warning: "warning",
  danger: "error",
};

export function Notice({
  tone = "info",
  children,
  className,
  style,
  popup,
  alert,
  inline = false,
  asToast = false,
  position = "top",
  onDismiss,
  autoDismiss = true,
  duration = 4000,
}: NoticeProps) {
  const isPopup = !inline && Boolean(alert || popup || asToast);

  if (isPopup) {
    return (
      <Toast
        alert
        variant={VARIANT_MAP[tone] ?? "info"}
        content={children}
        position={position}
        alertDuration={autoDismiss ? duration : undefined}
        onDismiss={onDismiss}
        className={className}
        style={style}
      />
    );
  }

  const iconName = tone === "danger" || tone === "warning" ? "alert" : tone === "success" ? "check-circle" : "info";

  return (
    <div
      role={tone === "danger" ? "alert" : "status"}
      aria-live={tone === "danger" ? "assertive" : "polite"}
      className={cn("eds-notice", TONE_CLASS[tone], className)}
      style={style}
    >
      <span className="eds-notice__icon" aria-hidden="true">
        <Icon name={iconName} size={18} />
      </span>
      <div className="eds-notice__content">{children}</div>
    </div>
  );
}

export default Notice;
