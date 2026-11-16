import { type FormEvent, useState } from "react";
import { Button, Modal, Notice, PasswordField } from "../../../design-system/components";
import { serverMessage } from "../serverMessage";
import { authService } from "../../../services/modules/authService";
import { BackupCodes } from "./BackupCodes";

// MFA-01: on "My profile", a signed-in user gets a fresh set of backup codes. The current password
// is asked again, so an unattended open session alone cannot produce codes that bypass the
// authenticator. New codes replace the old ones.
export function BackupCodesSection() {
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [codes, setCodes] = useState<string[]>([]);
  const [done, setDone] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setDone(false);
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
      setError(serverMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="app-panel" aria-labelledby="backup-codes-title">
      <div className="app-panel__header">
        <div>
          <h2 id="backup-codes-title" className="app-panel__title">Two-step login backup codes</h2>
          <p className="app-panel__description">
            Backup codes let you sign in if you lose your phone. Getting new codes makes your old codes stop working.
          </p>
        </div>
      </div>
      <div style={{ padding: "0 var(--space-5) var(--space-5)" }}>
        {done && <Notice tone="success">Your new backup codes are active. Your old codes no longer work.</Notice>}
        {error && <Notice tone="danger">{error}</Notice>}
        <form onSubmit={submit} noValidate className="app-form-grid">
          <PasswordField
            id="backup-codes-password"
            label="Current password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <div style={{ gridColumn: "1 / -1" }}>
            <Button type="submit" variant="secondary" busy={busy}>
              Get new backup codes
            </Button>
          </div>
        </form>
      </div>
      <Modal
        isOpen={codes.length > 0}
        onClose={() => {
          setCodes([]);
          setDone(true);
        }}
        closeOnBackdrop={false}
        kicker="ONE-TIME DISPLAY"
        title="Save your new backup codes"
        titleId="new-backup-codes-title"
        size="sm"
      >
        <BackupCodes
          codes={codes}
          continueLabel="Done"
          onContinue={() => {
            setCodes([]);
            setDone(true);
          }}
        />
      </Modal>
    </section>
  );
}
