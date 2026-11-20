import { useEffect, useState } from "react";
import Toast from "./Toast";
import { toastRegistry, type ToastItem } from "./toastRegistry";

export default function ToastHost() {
  const [toasts, setToasts] = useState<ToastItem[]>(toastRegistry.get());

  useEffect(() => {
    return toastRegistry.subscribe((nextToasts) => {
      setToasts(nextToasts);
    });
  }, []);

  if (toasts.length === 0) return null;

  return (
    <aside
      aria-label="Notifications"
      style={{
        position: "fixed",
        top: "var(--space-4, 16px)",
        right: "var(--space-4, 16px)",
        zIndex: "var(--z-toast, 1100)",
        display: "flex",
        flexDirection: "column",
        gap: "var(--space-2, 8px)",
        width: "min(420px, calc(100vw - 32px))",
        pointerEvents: "none",
      }}
    >
      {toasts.map((item) => (
        <div key={item.id} style={{ pointerEvents: "auto", width: "100%" }}>
          <Toast
            variant={item.variant}
            title={item.title}
            description={item.description}
            content={item.content}
            action={item.action}
            showAction={item.showAction}
            alert={true}
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
