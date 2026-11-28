import { useSupportSession } from "./useSupportSession";
import { Button } from "../../design-system/components";

export function SupportSessionBanner() {
  const { session, isSupportSession, endSupportSession } = useSupportSession();
  if (!isSupportSession || !session) return null;

  return (
    <div
      role="alert"
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: "var(--space-4)",
        padding: "var(--space-3) var(--space-4)",
        background: "var(--color-status-danger-fg)",
        color: "var(--color-text-on-danger)",
        borderRadius: "var(--radius-sm)",
        margin: "var(--space-3) 0",
      }}
    >
      <div>
        <strong>READ-ONLY SUPPORT SESSION</strong>
        <div style={{ fontSize: "var(--fs-body-sm)" }}>
          Ticket {session.ticketNumber ?? "support"} · Ends {session.expiresAt ? new Date(session.expiresAt).toLocaleString() : "within one hour"}
        </div>
      </div>
      <Button
        type="button"
        variant="secondary"
        onClick={endSupportSession}
        style={{
          border: "1px solid var(--color-text-on-danger)",
          color: "var(--color-text-on-danger)",
          background: "transparent",
        }}
      >
        End session
      </Button>
    </div>
  );
}
