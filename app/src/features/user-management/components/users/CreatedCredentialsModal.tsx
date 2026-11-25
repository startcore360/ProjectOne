import { useState } from "react";
import { Button, Modal } from "../../../../design-system/components";

export interface ProvisionedUserCredentials {
  displayName: string;
  email?: string;
  identifier?: { label: string; value: string };
  temporaryPassword?: string;
  message?: string;
  twoStepLoginRequired?: boolean;
}

export interface CreatedCredentialsModalProps {
  user: ProvisionedUserCredentials;
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  kicker?: string;
  description?: string;
}

export function CreatedCredentialsModal({
  user,
  isOpen,
  onClose,
  title = "User created successfully",
  kicker = "CREDENTIALS ISSUED",
  description = "Share this temporary password securely with the user. It will only be shown once.",
}: CreatedCredentialsModalProps) {
  const [copied, setCopied] = useState(false);

  const copyToClipboard = () => {
    if (user.temporaryPassword) {
      void navigator.clipboard.writeText(user.temporaryPassword);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker={kicker}
      title={title}
      titleId="created-cred-title"
      description={description}
      size="sm"
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
        <p style={{ margin: 0, fontSize: "0.875rem" }}>
          {user.identifier ? (
            <>Student profile <strong>{user.displayName}</strong> has been created.</>
          ) : (
            <>User <strong>{user.displayName}</strong>{user.email ? ` (${user.email})` : ""} has been provisioned.</>
          )}
        </p>
        {user.identifier && (
          <p style={{ margin: 0, fontSize: "0.875rem" }}>
            <strong>{user.identifier.label}:</strong> <code>{user.identifier.value}</code>
          </p>
        )}
        {user.message && <p style={{ margin: 0, fontSize: "0.875rem" }}>{user.message}</p>}
        {user.temporaryPassword && (
          <div
            style={{
              padding: "14px 16px",
              backgroundColor: "var(--color-bg-subtle)",
              borderRadius: "var(--radius-md)",
              border: "1px dashed var(--color-border-divider)",
            }}
          >
            <label
              htmlFor="created-temp-password"
              style={{
                display: "block",
                fontSize: "0.75rem",
                fontWeight: "var(--fw-semibold)",
                color: "var(--color-text-muted)",
                marginBottom: "6px",
                letterSpacing: "0.04em",
              }}
            >
              ONE-TIME TEMPORARY PASSWORD
            </label>
            <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
              <input
                id="created-temp-password"
                readOnly
                value={user.temporaryPassword}
                onFocus={(e) => e.target.select()}
                style={{
                  fontFamily: "var(--font-mono)",
                  fontSize: "1.1rem",
                  padding: "0.5rem 0.75rem",
                  borderRadius: "var(--radius-sm)",
                  border: "1px solid var(--color-border-divider)",
                  backgroundColor: "var(--color-bg-surface)",
                  color: "var(--color-text)",
                  flex: 1,
                  minWidth: 0,
                }}
              />
              <Button
                type="button"
                variant="secondary"
                onClick={copyToClipboard}
                style={{ whiteSpace: "nowrap", flexShrink: 0, minWidth: "76px" }}
              >
                {copied ? "Copied!" : "Copy"}
              </Button>
            </div>
            <p style={{ marginTop: "8px", marginBottom: 0, fontSize: "0.8rem", color: "var(--color-text-muted)", lineHeight: 1.4 }}>
              This temporary password will not be shown again. Securely share it with the user. They will be prompted to choose a new password on first sign-in.
            </p>
          </div>
        )}

        {user.twoStepLoginRequired && (
          <div className="app-note" style={{ fontSize: "0.8rem", color: "var(--color-status-danger-fg)" }}>
            Two-step login (MFA) will be required at the user's first sign-in.
          </div>
        )}

        <footer style={{ marginTop: "16px", display: "flex", justifyContent: "flex-end", gap: "8px" }}>
          <Button type="button" variant="primary" onClick={onClose}>
            I've copied it, close
          </Button>
        </footer>
      </div>
    </Modal>
  );
}

export default CreatedCredentialsModal;
