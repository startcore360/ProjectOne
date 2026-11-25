import { useEffect, useState, type CSSProperties } from "react";
import Toast from "./Toast";
import { toastRegistry, type ToastItem, type ToastPosition } from "./toastRegistry";

export type ToastHostProps = {
  /**
   * Screen placement of toasts. Defaults to "top" (centered top popup).
   * Supports: "top" | "top-center" | "top-right" | "top-left" | "bottom" | "bottom-center" | "bottom-right" | "bottom-left"
   */
  position?: ToastPosition;
  /**
   * When true (default), renders toasts as elevated popups floating upon the screen.
   */
  popup?: boolean;
  className?: string;
  style?: CSSProperties;
};

function getPositionStyles(position: ToastPosition = "top"): CSSProperties {
  switch (position) {
    case "top-right":
      return {
        top: "max(16px, env(safe-area-inset-top, 16px))",
        right: "max(16px, env(safe-area-inset-right, 16px))",
        alignItems: "flex-end",
      };
    case "top-left":
      return {
        top: "max(16px, env(safe-area-inset-top, 16px))",
        left: "max(16px, env(safe-area-inset-left, 16px))",
        alignItems: "flex-start",
      };
    case "bottom":
    case "bottom-center":
      return {
        bottom: "max(24px, env(safe-area-inset-bottom, 24px))",
        left: "50%",
        transform: "translateX(-50%)",
        alignItems: "center",
      };
    case "bottom-right":
      return {
        bottom: "max(24px, env(safe-area-inset-bottom, 24px))",
        right: "max(16px, env(safe-area-inset-right, 16px))",
        alignItems: "flex-end",
      };
    case "bottom-left":
      return {
        bottom: "max(24px, env(safe-area-inset-bottom, 24px))",
        left: "max(16px, env(safe-area-inset-left, 16px))",
        alignItems: "flex-start",
      };
    case "top":
    case "top-center":
    default:
      return {
        top: "max(16px, env(safe-area-inset-top, 16px))",
        left: "50%",
        transform: "translateX(-50%)",
        alignItems: "center",
      };
  }
}

export default function ToastHost({
  position = "top",
  popup: _popup = true,
  className = "",
  style,
}: ToastHostProps = {}) {
  const [toasts, setToasts] = useState<ToastItem[]>(toastRegistry.get());

  useEffect(() => {
    return toastRegistry.subscribe((nextToasts) => {
      setToasts(nextToasts);
    });
  }, []);

  if (toasts.length === 0) return null;

  const positionStyle = getPositionStyles(position);

  return (
    <aside
      aria-label="Notifications"
      data-testid="toast-host"
      data-position={position}
      className={`eds-toast-host eds-toast-host--${position} ${className}`.trim()}
      style={{
        position: "fixed",
        zIndex: "var(--z-toast, 9999)",
        display: "flex",
        flexDirection: "column",
        gap: "10px",
        width: "min(480px, calc(100vw - 32px))",
        pointerEvents: "none",
        ...positionStyle,
        ...style,
      }}
    >
      {toasts.map((item) => (
        <div key={item.id} style={{ pointerEvents: "auto", width: "100%", display: "flex", justifyContent: "center" }}>
          <Toast
            variant={item.variant}
            title={item.title}
            description={item.description}
            content={item.content}
            action={item.action}
            showAction={item.showAction}
            alert={false}
            popup={false}
            className="w-full shadow-lg"
            style={{
              boxShadow: "0 12px 32px -4px rgba(0, 0, 0, 0.18), 0 4px 12px -2px rgba(0, 0, 0, 0.08)",
            }}
            position={item.position ?? position}
            alertDuration={item.alertDuration ?? 4000}
            onDismiss={() => {
              item.onDismiss?.();
              toastRegistry.hide(item.id);
            }}
          />
        </div>
      ))}
    </aside>
  );
}
