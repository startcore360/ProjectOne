import { useEffect, useRef, useState } from "react";
import Button, { type ButtonVariant, type ButtonSize } from "./Button";

export interface ShareMenuProps {
  /** Plain-text message sent to WhatsApp/Telegram/email and copied to the clipboard. */
  text: string;
  /** Used as the native share-sheet title and the email subject, if no emailSubject is given. */
  title?: string;
  emailSubject?: string;
  /** Pre-fills the email "to" field when known. */
  emailTo?: string;
  /** Accessible label and tooltip for the trigger icon. */
  label?: string;
  /** Whether to render as an EDS Button component instead of a round icon button. */
  asButton?: boolean;
  buttonText?: string;
  buttonVariant?: ButtonVariant;
  size?: ButtonSize;
}

const ICON_BUTTON_SIZE = 28;

function MenuItem({ onClick, label, swatch, children }: { onClick: () => void; label: string; swatch: string; children: React.ReactNode }) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      style={{
        display: "flex",
        alignItems: "center",
        gap: "var(--space-2)",
        width: "100%",
        padding: "var(--space-2) var(--space-2)",
        background: "transparent",
        border: "none",
        borderRadius: "var(--radius-sm)",
        cursor: "pointer",
        textAlign: "left",
        font: "inherit",
        fontSize: "13px",
        color: "var(--color-text)",
      }}
      onMouseEnter={(e) => (e.currentTarget.style.background = "var(--color-bg-subtle)")}
      onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
    >
      <span
        aria-hidden="true"
        style={{
          width: "22px",
          height: "22px",
          borderRadius: "50%",
          background: swatch,
          color: "#fff",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          flexShrink: 0,
        }}
      >
        {children}
      </span>
      {label}
    </button>
  );
}

// A single "where do you want to send this" icon: click opens a small destination menu
// (WhatsApp, Telegram, email, copy, device share) instead of a row of separate buttons.
export function ShareMenu({
  text,
  title,
  emailSubject,
  emailTo,
  label = "Share",
  asButton = false,
  buttonText,
  buttonVariant = "secondary",
  size = "default",
}: ShareMenuProps) {
  const [open, setOpen] = useState(false);
  const [copyLabel, setCopyLabel] = useState("Copy to clipboard");
  const rootRef = useRef<HTMLDivElement>(null);
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!open) return;
    const handleClick = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", handleClick);
    document.addEventListener("keydown", handleKey);
    return () => {
      document.removeEventListener("mousedown", handleClick);
      document.removeEventListener("keydown", handleKey);
    };
  }, [open]);

  useEffect(() => () => {
    if (closeTimer.current) clearTimeout(closeTimer.current);
  }, []);

  const close = () => setOpen(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopyLabel("Copied!");
    } catch {
      setCopyLabel("Couldn't copy — select and copy manually");
    }
    closeTimer.current = setTimeout(() => {
      setCopyLabel("Copy to clipboard");
      close();
    }, 1200);
  };

  const handleWhatsApp = () => {
    window.open(`https://api.whatsapp.com/send?text=${encodeURIComponent(text)}`, "_blank", "noopener,noreferrer");
    close();
  };

  const handleTelegram = () => {
    window.open(
      `https://t.me/share/url?url=${encodeURIComponent(window.location.origin)}&text=${encodeURIComponent(text)}`,
      "_blank",
      "noopener,noreferrer",
    );
    close();
  };

  const handleEmail = () => {
    const subject = encodeURIComponent(emailSubject || title || "");
    const body = encodeURIComponent(text);
    window.location.href = `mailto:${emailTo ? encodeURIComponent(emailTo) : ""}?subject=${subject}&body=${body}`;
    close();
  };

  const canNativeShare = typeof navigator !== "undefined" && typeof navigator.share === "function";

  const handleNativeShare = async () => {
    try {
      await navigator.share({ title, text });
    } catch {
      // user cancelled, or unsupported despite the feature check; nothing to surface
    }
    close();
  };

  return (
    <div ref={rootRef} style={{ position: "relative", display: "inline-block" }}>
      {asButton ? (
        <Button
          type="button"
          variant={buttonVariant}
          size={size}
          onClick={() => setOpen((v) => !v)}
          aria-haspopup="menu"
          aria-expanded={open}
          aria-label={label}
          title={label}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px", whiteSpace: "nowrap" }}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="18" cy="5" r="3" />
            <circle cx="6" cy="12" r="3" />
            <circle cx="18" cy="19" r="3" />
            <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
            <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
          </svg>
          <span>{buttonText || label || "Share"}</span>
        </Button>
      ) : (
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-haspopup="menu"
          aria-expanded={open}
          aria-label={label}
          title={label}
          style={{
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            width: `${ICON_BUTTON_SIZE}px`,
            height: `${ICON_BUTTON_SIZE}px`,
            borderRadius: "var(--radius-pill)",
            border: "1px solid var(--color-border-divider)",
            background: "var(--color-bg-surface)",
            color: "var(--color-text)",
            cursor: "pointer",
            padding: 0,
          }}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="18" cy="5" r="3" />
            <circle cx="6" cy="12" r="3" />
            <circle cx="18" cy="19" r="3" />
            <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
            <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
          </svg>
        </button>
      )}

      {open && (
        <div
          role="menu"
          aria-label={`${label} destinations`}
          style={{
            position: "absolute",
            top: `calc(100% + 6px)`,
            right: 0,
            zIndex: 20,
            minWidth: "200px",
            background: "var(--color-bg-surface)",
            border: "1px solid var(--color-border-divider)",
            borderRadius: "var(--radius-md)",
            boxShadow: "0 8px 24px rgba(0,0,0,0.18)",
            padding: "var(--space-1)",
            display: "grid",
          }}
        >
          <MenuItem onClick={() => void handleCopy()} label={copyLabel} swatch="#475569">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
              <rect x="8" y="2" width="8" height="4" rx="1" ry="1" />
            </svg>
          </MenuItem>
          <MenuItem onClick={handleWhatsApp} label="WhatsApp" swatch="#25D366">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z" />
            </svg>
          </MenuItem>
          <MenuItem onClick={handleTelegram} label="Telegram" swatch="#0088cc">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M12 0C5.37 0 0 5.37 0 12s5.37 12 12 12 12-5.37 12-12S18.63 0 12 0zm5.894 8.221-1.97 9.28c-.145.658-.537.818-1.084.508l-3-2.21-1.446 1.394c-.16.16-.295.295-.605.295l.213-3.053 5.56-5.023c.242-.213-.054-.333-.373-.121l-6.871 4.326-2.962-.924c-.643-.204-.657-.643.136-.953l11.57-4.458c.536-.196 1.006.128.832.943z" />
            </svg>
          </MenuItem>
          <MenuItem onClick={handleEmail} label="Email" swatch="#0284c7">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
              <polyline points="22,6 12,13 2,6" />
            </svg>
          </MenuItem>
          {canNativeShare && (
            <MenuItem onClick={() => void handleNativeShare()} label="More..." swatch="#6366f1">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <circle cx="18" cy="5" r="3" />
                <circle cx="6" cy="12" r="3" />
                <circle cx="18" cy="19" r="3" />
                <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
                <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
              </svg>
            </MenuItem>
          )}
        </div>
      )}
    </div>
  );
}

export default ShareMenu;
