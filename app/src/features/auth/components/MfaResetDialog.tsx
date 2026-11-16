import { useState } from "react";
import { Button, Modal, Notice, TextAreaField } from "../../../design-system/components";

export interface MfaResetDialogProps {
  isOpen: boolean;
  /** Who is being reset, shown in the title. */
  personName: string;
  busy: boolean;
  /** Server error, if the last attempt failed. */
  error?: string;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

// MFA-01: an admin resets someone's two-step login. A reason is always required (it is audited),
// and the dialog says plainly what happens, so nobody confuses it with a password reset.
export function MfaResetDialog({ isOpen, personName, busy, error, onConfirm, onCancel }: MfaResetDialogProps) {
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);
  const trimmed = reason.trim();
  const invalid = trimmed.length < 5;

  const close = () => {
    setReason("");
    setTouched(false);
    onCancel();
  };

  const confirm = () => {
    setTouched(true);
    if (invalid) return;
    onConfirm(trimmed);
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={close}
      kicker="SECURITY"
      title={`Reset two-step login for ${personName}?`}
      titleId="mfa-reset-title"
      description="Use this when their authenticator app no longer works, for example a lost or new phone."
      size="sm"
      footer={
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)", width: "100%" }}>
          <Button variant="secondary" onClick={close} disabled={busy}>
            Cancel
          </Button>
          <Button variant="danger" onClick={confirm} busy={busy}>
            Reset two-step login
          </Button>
        </div>
      }
    >
      <ul style={{ margin: "0 0 var(--space-3)", paddingLeft: "var(--space-4)" }}>
        <li>Their authenticator and backup codes stop working. Their password does not change.</li>
        <li>They are signed out everywhere.</li>
        <li>At their next sign-in they scan a new QR code.</li>
        <li>They are told about the reset, and it is recorded with your reason.</li>
      </ul>
      <TextAreaField
        id="mfa-reset-reason"
        label="Reason"
        required
        rows={3}
        maxLength={500}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder="For example: lost phone, identity confirmed in person"
        error={touched && invalid ? "Give a reason of at least 5 characters." : undefined}
      />
      {error && <Notice tone="danger">{error}</Notice>}
    </Modal>
  );
}
