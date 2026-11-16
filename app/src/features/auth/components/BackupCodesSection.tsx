import { type FormEvent, type ReactNode, useState } from "react";
import axios from "axios";
import { Button, Modal, Notice, PasswordField } from "../../../design-system/components";
import { serverMessage } from "../serverMessage";
import { authService } from "../../../services/modules/authService";
import { BackupCodes } from "./BackupCodes";

// A missing route (404) or a server fault (5xx) carries a technical sentence such as
// "Resource not found: api/auth/mfa/recovery-codes" - never show that to a person.
function friendlyMessage(err: unknown): string {
  const status = axios.isAxiosError(err) ? err.response?.status : undefined;
  if (status === 404 || (status !== undefined && status >= 500)) {
    return "Backup codes are not available right now. Please try again later.";
  }
  return serverMessage(err);
}

export interface BackupCodesSectionProps {
  /** Optional icon shown at the start of the row (the profile Security tab passes one). */
  icon?: ReactNode;
}

// MFA-01: a signed-in user gets a fresh set of backup codes. The current password is asked again
// (in a dialog, not a field left open on the page), so an unattended open session alone cannot
// produce codes that bypass the authenticator. New codes replace the old ones.
export function BackupCodesSection({ icon }: BackupCodesSectionProps) {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [codes, setCodes] = useState<string[]>([]);
  const [done, setDone] = useState(false);

  const openDialog = () => {
    setPassword("");
    setError("");
    setCodes([]);
    setDone(false);
    setOpen(true);
  };

  const close = () => {
    const issued = codes.length > 0;
    setOpen(false);
    setCodes([]);
    setPassword("");
    setError("");
    if (issued) setDone(true);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    if (!password) {
      setError("Enter your current password.");
      return;
    }
    setBusy(true);
    try {
      const { data } = await authService.issueRecoveryCodes(password);
      setCodes(data.recoveryCodes);
      setPassword("");
    } catch (err) {
      setError(friendlyMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const showingCodes = codes.length > 0;

  return (
    <div className="app-profile-row" aria-labelledby="backup-codes-title">
      {icon && <span className="app-profile-row__icon">{icon}</span>}
      <div className="app-profile-row__body">
        <h4 id="backup-codes-title" className="app-profile-row__title">Backup codes</h4>
        <p className="app-profile-row__text">
          One-time codes that let you sign in if you lose your phone. New codes replace your old ones.
        </p>
        {done && (
          <p className="app-status-text app-status-text--success" role="status">
            Your new backup codes are active. Your old codes no longer work.
          </p>
        )}
      </div>
      <div className="app-profile-row__action">
        <Button variant="secondary" onClick={openDialog}>
          Generate new codes
        </Button>
      </div>

      <Modal
        isOpen={open}
        onClose={close}
        closeOnBackdrop={false}
        kicker={showingCodes ? "ONE-TIME DISPLAY" : "TWO-STEP LOGIN"}
        title={showingCodes ? "Save your new backup codes" : "Generate new backup codes"}
        titleId="new-backup-codes-title"
        size="sm"
      >
        {showingCodes ? (
          <BackupCodes codes={codes} continueLabel="Done" onContinue={close} />
        ) : (
          <form onSubmit={submit} noValidate className="app-profile-dialog-form">
            <Notice tone="warning">Your current backup codes will stop working as soon as new ones are made.</Notice>
            {error && <Notice tone="danger">{error}</Notice>}
            <PasswordField
              id="backup-codes-password"
              label="Current password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoFocus
            />
            <div className="app-profile-dialog-form__actions">
              <Button type="button" variant="tertiary" onClick={close} disabled={busy}>
                Cancel
              </Button>
              <Button type="submit" busy={busy}>
                Get new backup codes
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
}
