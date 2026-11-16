import { type FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { Button, Kicker, LinkButton, PasswordField, TextField } from "../../../design-system/components";
import Edu360Logo from "../../../assets/Edu360Logo";
import { login } from "../../../services/authManager";
import { clearLogoutReason, getLogoutReason } from "../../../services/logoutNotice";
import { authService } from "../../../services/modules/authService";
import { AuthenticatorSetup, type PendingEnrolment } from "../components/AuthenticatorSetup";
import { BackupCodes } from "../components/BackupCodes";
// The sign-in screen. Posts straight to this app's own /api/auth/login - Keycloak verifies
// the password behind the scenes (see docs/KEYCLOAK_MIGRATION_PLAN.md), but this screen is
// the only thing a user ever sees; there is no redirect to Keycloak's own page.
export default function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  // I-10: why the last session ended (idle, expired, password changed), shown once.
  const [notice, setNotice] = useState(() => {
    const reason = getLogoutReason() ?? "";
    if (reason) clearLogoutReason();
    return reason;
  });
  // I-01: "code" = password accepted, waiting for the authenticator app code.
  // MFA-ENROL: "enrol" = password accepted, no authenticator yet: set one up right here.
  // MFA-01: "backup" = signing in with a one-time backup code; "codes" = new backup codes, shown once.
  const [step, setStep] = useState<"password" | "code" | "enrol" | "backup" | "codes">("password");
  const [enrolment, setEnrolment] = useState<PendingEnrolment | null>(null);
  const [code, setCode] = useState("");
  const [backupCode, setBackupCode] = useState("");
  const [showCodeHelp, setShowCodeHelp] = useState(false);
  const [newBackupCodes, setNewBackupCodes] = useState<string[]>([]);
  const [enrolmentUrl, setEnrolmentUrl] = useState("");
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError("");
    setNotice("");
    if (!email.trim() || !password) {
      setError("Enter your email and password.");
      return;
    }
    if (step === "code" && !/^[0-9]{6}$/.test(code)) {
      setError("Enter the 6-digit code from your authenticator app.");
      return;
    }
    if (step === "backup" && backupCode.replace(/[\s-]/g, "").length !== 10) {
      setError("Enter a backup code, like K7QD4-9MXRT.");
      return;
    }
    setBusy(true);
    try {
      if (step === "backup") {
        // Always answered with MFA_ENROLMENT_REQUIRED or an error (handled below).
        await authService.loginWithRecoveryCode(email.trim(), password, backupCode.trim());
        setError("Sign-in failed. Check your details and try again.");
        return;
      }
      const user = await login(email.trim(), password, step === "code" ? code : undefined);
      navigate(user.mustChangePassword ? "/change-password" : "/");
    } catch (err) {
      const body = axios.isAxiosError<{
        code?: string;
        error?: string;
        enrolmentUrl?: string;
        enrolmentId?: string;
        qrCode?: string;
        secret?: string;
      }>(err)
        ? err.response?.data
        : undefined;
      if (body?.code === "MFA_REQUIRED") {
        // Password was right; ask for the authenticator code (REG-I01-1).
        setStep("code");
        setCode("");
        setBusy(false);
        return;
      }
      if (body?.code === "MFA_ENROLMENT_REQUIRED" && body.enrolmentId && body.qrCode && body.secret) {
        // No authenticator yet: set it up in this screen (QR from Keycloak; the secret stays there).
        if (step === "backup") {
          setNotice("Backup code accepted. Your old authenticator was removed - set up a new one now.");
        }
        setEnrolment({ enrolmentId: body.enrolmentId, qrCode: body.qrCode, secret: body.secret });
        setBackupCode("");
        setStep("enrol");
        setBusy(false);
        return;
      }
      if (body?.code === "MFA_ENROLMENT_REQUIRED") {
        // Fallback when the server can't start an in-app set-up: Keycloak's own set-up page.
        setEnrolmentUrl(body.enrolmentUrl ?? "");
        setError(body.error ?? "Set up an authenticator app, then sign in again.");
        setBusy(false);
        return;
      }
      if (step === "code") setCode("");
      // The backend's GlobalExceptionHandler sends a real, human message in the body
      // ({error: "Invalid email or password"}) - show that, never axios's own generic
      // "Request failed with status code 401" text.
      const backendMessage = axios.isAxiosError<{ error?: string }>(err)
        ? err.response?.data?.error
        : undefined;
      setError(backendMessage ?? "Sign-in failed. Check your details and try again.");
    } finally {
      setBusy(false);
    }
  };
  // MFA-ENROL: confirm the first code. On success the authenticator is saved and the screen asks
  // for the NEXT code to sign in (Keycloak never accepts the same code twice).
  const confirmEnrolment = async (setupCode: string) => {
    if (!enrolment) return;
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const { data } = await authService.enrolMfa({
        email: email.trim(),
        password,
        enrolmentId: enrolment.enrolmentId,
        code: setupCode,
      });
      setEnrolment(null);
      setCode("");
      if (data.recoveryCodes && data.recoveryCodes.length > 0) {
        // MFA-01: show the new backup codes once, before asking for the next code.
        setNewBackupCodes(data.recoveryCodes);
        setStep("codes");
        setNotice("");
        return;
      }
      setStep("code");
      setNotice(data.message ?? "Authenticator added. Enter the next code from your app to sign in.");
    } catch (err) {
      const body = axios.isAxiosError<{ code?: string; error?: string }>(err) ? err.response?.data : undefined;
      if (body?.code === "ALREADY_ENROLLED") {
        setEnrolment(null);
        setStep("code");
        setNotice(body.error ?? "An authenticator is already set up. Enter its code to sign in.");
      } else if (body?.code === "ENROLMENT_EXPIRED") {
        setEnrolment(null);
        setStep("password");
        setError(body.error ?? "This set-up has expired. Sign in again to start over.");
      } else {
        setError(body?.error ?? "Could not check that code. Please try again.");
      }
    } finally {
      setBusy(false);
    }
  };

  const cancelEnrolment = () => {
    setEnrolment(null);
    setStep("password");
    setError("");
  };

  const finishBackupCodes = () => {
    setNewBackupCodes([]);
    setStep("code");
    setNotice("Codes saved. Enter the next code from your authenticator app to sign in.");
  };

  const useBackupCode = () => {
    setStep("backup");
    setShowCodeHelp(false);
    setError("");
    setNotice("");
  };

  const kicker = step === "enrol" ? "ONE-TIME SET-UP" : step === "codes" ? "SAVE YOUR BACKUP CODES" : "WELCOME BACK";
  const title = step === "enrol" ? "Set up two-step login." : step === "codes" ? "Save your backup codes." : "Welcome back.";

  return (
    <main className="auth-screen">
      <div className="auth-brand">
        <Edu360Logo className="auth-brand__logo" />
      </div>
      <section className="auth-card" aria-labelledby="login-title">
        <Kicker>{kicker}</Kicker>
        <h1 id="login-title" className="auth-card__title">
          {title}
        </h1>
        <p className="auth-card__subtitle">
          {step === "enrol"
            ? "Your role needs a code from an authenticator app each time you sign in. It takes about a minute."
            : step === "codes"
              ? "Your authenticator is set up. Keep these backup codes in case you lose your phone."
              : "Sign in to continue to your workspace."}
        </p>
        {error && (
          <div className="auth-message auth-message--error" role="alert">
            {error}
          </div>
        )}
        {enrolmentUrl && (
          <div className="auth-message" role="status">
            <a href={enrolmentUrl} target="_blank" rel="noopener noreferrer">
              Open the authenticator set-up page
            </a>
            , then come back and sign in again.
          </div>
        )}
        {notice && (
          <div className="auth-message" role="status">
            {notice}
          </div>
        )}
        {step === "codes" ? (
          <BackupCodes codes={newBackupCodes} continueLabel="Continue to sign in" onContinue={finishBackupCodes} />
        ) : step === "enrol" && enrolment ? (
          <AuthenticatorSetup
            enrolment={enrolment}
            busy={busy}
            onConfirm={(setupCode) => void confirmEnrolment(setupCode)}
            onCancel={cancelEnrolment}
          />
        ) : (
        <form onSubmit={submit} noValidate>
          <TextField
            id="login-email"
            label="Email"
            type="email"
            autoComplete="email"
            autoCapitalize="none"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
            required
          />
          <PasswordField
            id="login-password"
            label="Password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Enter your password"
            required
          />
          {step === "code" && (
            <TextField
              id="login-code"
              label="Authenticator code"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
              placeholder="6-digit code"
              required
              autoFocus
            />
          )}
          {step === "backup" && (
            <TextField
              id="login-backup-code"
              label="Backup code"
              autoComplete="off"
              autoCapitalize="characters"
              maxLength={14}
              value={backupCode}
              onChange={(e) => setBackupCode(e.target.value)}
              placeholder="K7QD4-9MXRT"
              hint="Each backup code works once. After it, you set up a new authenticator."
              required
              autoFocus
            />
          )}
          {(step === "code" || step === "backup") && (
            <div className="auth-options">
              {step === "code" ? (
                <LinkButton onClick={() => setShowCodeHelp((open) => !open)} aria-expanded={showCodeHelp}>
                  Problem with your code?
                </LinkButton>
              ) : (
                <LinkButton onClick={() => { setStep("code"); setBackupCode(""); setError(""); }}>
                  Use the authenticator code instead
                </LinkButton>
              )}
            </div>
          )}
          {step === "code" && showCodeHelp && (
            <div className="auth-message" role="region" aria-label="Help with your code">
              <ul className="auth-help-list">
                <li>Check that your phone shows the correct time (set it to automatic).</li>
                <li>
                  Lost your phone or deleted the app?{" "}
                  <LinkButton onClick={useBackupCode}>Use a backup code</LinkButton>
                </li>
                <li>No backup codes? Ask your administrator to reset your two-step login.</li>
              </ul>
            </div>
          )}
          <div className="auth-options -mb-3">
            <LinkButton
              onClick={() =>
                setNotice(
                  "Password recovery is managed by your institution. Please contact your school administrator.",
                )
              }
            >
              Forgot password?
            </LinkButton>
          </div>
          <Button type="submit" size="lg" block busy={busy} className="auth-submit">
            {busy ? "Signing in…" : "Sign in"}
          </Button>
        </form>
        )}
        <p className="auth-security">
          <span aria-hidden="true">◆</span>Your access follows your institution’s roles and
          permissions. Never share your password.
        </p>
      </section>
      <footer className="auth-footer">
        EDU 360 <span>·</span> One Platform. Every Institution. One Connected Ecosystem.
      </footer>
    </main>
  );
}
