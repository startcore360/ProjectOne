import { useSupportSession } from "./useSupportSession";

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
        gap: "16px",
        padding: "12px 16px",
        background: "#991b1b",
        color: "#ffffff",
        borderRadius: "8px",
        margin: "12px 0",
      }}
    >
      <div>
        <strong>READ-ONLY SUPPORT SESSION</strong>
        <div style={{ fontSize: "13px" }}>
          Ticket {session.ticketNumber ?? "support"} · Ends {session.expiresAt ? new Date(session.expiresAt).toLocaleString() : "within one hour"}
        </div>
      </div>
      <button
        type="button"
        onClick={endSupportSession}
        style={{
          border: "1px solid #ffffff",
          borderRadius: "6px",
          padding: "7px 12px",
          background: "transparent",
          color: "#ffffff",
          fontWeight: 600,
          cursor: "pointer",
        }}
      >
        End session
      </button>
    </div>
  );
}
