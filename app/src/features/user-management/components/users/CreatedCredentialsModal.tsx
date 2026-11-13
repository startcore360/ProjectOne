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
              padding: "12px",
              backgroundColor: "var(--color-bg-muted, #f8fafc)",
              borderRadius: "6px",
              border: "1px dashed var(--color-border, #cbd5e1)",
            }}
          >
            <label htmlFor="created-temp-password" style={{ display: "block", fontSize: "0.75rem", color: "var(--color-text-muted, #64748b)", marginBottom: "4px" }}>
              ONE-TIME TEMPORARY PASSWORD
            </label>
            <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
              <input
                id="created-temp-password"
                readOnly
                value={user.temporaryPassword}
                onFocus={(e) => e.target.select()}
                style={{
                  fontFamily: "monospace",
                  fontSize: "1.1rem",
                  padding: "0.5rem 0.75rem",
                  borderRadius: "4px",
                  border: "1px solid var(--color-border, #cbd5e1)",
                  width: "100%",
                }}
              />
              <Button type="button" variant="secondary" onClick={copyToClipboard}>
                {copied ? "Copied!" : "Copy"}
              </Button>
            </div>
            <p style={{ marginTop: "0.5rem", fontSize: "0.8rem", color: "var(--color-text-secondary, #64748b)" }}>
              This temporary password will not be shown again. Securely share it with the user. They will be prompted to choose a new password on first sign-in.
            </p>
          </div>
        )}

        {user.twoStepLoginRequired && (
          <div className="platform-modal-note" style={{ fontSize: "0.8rem", color: "#e11d48" }}>
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
