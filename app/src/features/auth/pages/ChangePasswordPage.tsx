import { type FormEvent, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { Button, Kicker, LinkButton, PasswordField } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { changePassword, idleLogout, logout } from "../../../services/authManager";
import { startInactivityTracker } from "../../../services/inactivityTracker";
// Used both when a new account's admin-set password must be replaced before anything else is
// usable (MustChangePasswordFilter enforces this server-side too, not just this redirect), and
// later as an ordinary "change my password" screen - same form, same endpoint either way.
export default function ChangePasswordPage() {
  const navigate = useNavigate();
  const mustChangePassword = useAppSelector((state) => state.auth.user?.mustChangePassword);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => startInactivityTracker(() => { void idleLogout(); }), []);

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError("");
    if (!currentPassword || !newPassword || !confirmPassword) {
      setError("Fill in all three fields.");
      return;
    }
    if (newPassword.length < 8) {
      setError("New password must be at least 8 characters.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("New password and confirmation don't match.");
      return;
    }
    setBusy(true);
    try {
      await changePassword(currentPassword, newPassword);
      navigate("/dashboard");
    } catch (err) {
      const backendMessage = axios.isAxiosError<{ error?: string }>(err)
        ? err.response?.data?.error
        : undefined;
      setError(backendMessage ?? "Could not change your password. Please try again.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-screen">
      <div className="auth-brand">
        <span className="auth-brand__mark">e</span>
        <span>
          EDU <strong>360</strong>
        </span>
      </div>
      <section className="auth-card" aria-labelledby="change-password-title">
        <Kicker>{mustChangePassword ? "ACTION REQUIRED" : "ACCOUNT"}</Kicker>
        <h1 id="change-password-title" className="auth-card__title">
          {mustChangePassword ? "Set a new password" : "Change your password"}
        </h1>
        <p className="auth-card__subtitle">
          {mustChangePassword
            ? "Your account was created with a temporary password. Choose a new one to continue."
            : "Enter your current password and choose a new one."}
        </p>
        {error && (
          <div className="auth-message auth-message--error" role="alert">
            {error}
          </div>
        )}
        <form onSubmit={submit} noValidate>
          <PasswordField
            id="current-password"
            label="Current password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
          />
          <PasswordField
            id="new-password"
            label="New password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="At least 8 characters"
            required
          />
          <PasswordField
            id="confirm-password"
            label="Confirm new password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
          />
          <Button type="submit" size="lg" block busy={busy} className="auth-submit">
            {busy ? "Saving…" : "Save new password"}
          </Button>
        </form>
        {!mustChangePassword && (
          <LinkButton className="auth-cancel-link" onClick={() => void logout()}>
            Cancel and sign out
          </LinkButton>
        )}
      </section>
      <footer className="auth-footer">
        EDU 360 <span>·</span> One Platform. Every Institution. One Connected Ecosystem.
      </footer>
    </main>
  );
}
