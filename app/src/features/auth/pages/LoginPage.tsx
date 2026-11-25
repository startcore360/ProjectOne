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
import { buildSharedLandingUrl, isSharedLandingHost, isTunnelHost } from "../../../lib/tenantHost";

const LOGIN_DRAFT_KEY = "edu360_login_draft";

interface LoginDraft {
  email: string;
  password?: string;
  step: "password" | "code" | "enrol" | "backup" | "codes" | "locked" | "expired";
  enrolment?: PendingEnrolment | null;
  savedAt: number;
}

function loadLoginDraft(): LoginDraft | null {
  try {
    const raw = sessionStorage.getItem(LOGIN_DRAFT_KEY);
    if (!raw) return null;
    const parsed: LoginDraft = JSON.parse(raw);
    // Keep draft for up to 15 minutes (active login session duration)
    if (Date.now() - parsed.savedAt > 15 * 60 * 1000) {
      sessionStorage.removeItem(LOGIN_DRAFT_KEY);
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

function saveLoginDraft(draft: Partial<LoginDraft>) {
  try {
    const existing = loadLoginDraft() || { email: "", step: "password", savedAt: Date.now() };
    const merged: LoginDraft = {
      ...existing,
      ...draft,
      savedAt: Date.now(),
    };
    sessionStorage.setItem(LOGIN_DRAFT_KEY, JSON.stringify(merged));
  } catch {
    // Graceful fallback if storage disabled
  }
}

function clearLoginDraft() {
  try {
    sessionStorage.removeItem(LOGIN_DRAFT_KEY);
  } catch { /* sessionStorage may be unavailable in restricted browsing contexts. */ }
}

// The sign-in screen. Posts straight to this app's own /api/auth/login - Keycloak verifies
// the password behind the scenes (see docs/KEYCLOAK_MIGRATION_PLAN.md), but this screen is
// the only thing a user ever sees; there is no redirect to Keycloak's own page.
export default function LoginPage() {
  const navigate = useNavigate();
  const [draft] = useState<LoginDraft | null>(() => loadLoginDraft());
  const [email, setEmail] = useState(() => draft?.email ?? "");
  const [password, setPassword] = useState(() => draft?.password ?? "");
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
  const [step, setStep] = useState<"password" | "code" | "enrol" | "backup" | "codes" | "locked" | "expired">(() => draft?.step ?? "password");
  const [enrolment, setEnrolment] = useState<PendingEnrolment | null>(() => draft?.enrolment ?? null);
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
      clearLoginDraft();
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
      if (body?.code === "LOCKED" || body?.code === "ACCOUNT_LOCKED" || body?.code === "ACCOUNT_TEMPORARILY_LOCKED") {
        clearLoginDraft();
        setStep("locked");
        setError(body.error ?? "This account is temporarily locked after repeated failed sign-in attempts. Try again after 30 minutes or contact your administrator.");
        return;
      }
      if (body?.code === "PASSWORD_EXPIRED") {
        clearLoginDraft();
        setStep("expired");
        setError(body.error ?? "Your password has expired. Use password recovery or contact your administrator.");
        return;
      }
      if (body?.code === "MFA_REQUIRED") {
        // Password was right; ask for the authenticator code (REG-I01-1).
        setStep("code");
        setCode("");
        setBusy(false);
        saveLoginDraft({ email: email.trim(), password, step: "code" });
        return;
      }
      if (body?.code === "MFA_ENROLMENT_REQUIRED" && body.enrolmentId && body.qrCode && body.secret) {
        // No authenticator yet: set it up in this screen (QR from Keycloak; the secret stays there).
        if (step === "backup") {
          setNotice("Backup code accepted. Your old authenticator was removed - set up a new one now.");
        }
        const pending = { enrolmentId: body.enrolmentId, qrCode: body.qrCode, secret: body.secret };
        setEnrolment(pending);
        setBackupCode("");
        setStep("enrol");
        setBusy(false);
        saveLoginDraft({ email: email.trim(), password, step: "enrol", enrolment: pending });
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
      if (step === "backup" && body?.code === "INVALID_CREDENTIALS") {
        // The server checks the password before it looks at the backup code, so say which one failed:
        // otherwise a wrong password reads as "your backup code doesn't work".
        setError("Your email or password is wrong. Your backup code was not checked and can still be used.");
        return;
      }
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

  const switchToBackupCode = (typed = "") => {
    setStep("backup");
    setBackupCode(typed);
    setCode("");
    setShowCodeHelp(false);
    setError("");
    setNotice("");
  };

  // The authenticator box takes digits only. A letter or dash means the person is typing a backup
  // code (like K7QD4-9MXRT) into the wrong box, so move them to the backup box with what they typed
  // instead of silently deleting the letters.
  const changeCode = (value: string) => {
    if (/[A-Za-z-]/.test(value)) {
      switchToBackupCode(value.toUpperCase());
      return;
    }
    // maxLength on the box is 14 so a pasted backup code arrives whole; digits still stop at 6.
    setCode(value.replace(/\D/g, "").slice(0, 6));
  };

  const kicker = step === "enrol" ? "ONE-TIME SET-UP" : step === "codes" ? "SAVE YOUR BACKUP CODES" : step === "locked" ? "ACCOUNT TEMPORARILY LOCKED" : step === "expired" ? "PASSWORD EXPIRED" : "WELCOME BACK";
  const title = step === "enrol" ? "Set up two-step login." : step === "codes" ? "Save your backup codes." : step === "locked" ? "Please try again later." : step === "expired" ? "Update your password." : "Welcome back.";

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
            accountName={email.trim()}
            busy={busy}
            onConfirm={(setupCode) => void confirmEnrolment(setupCode)}
            onCancel={cancelEnrolment}
          />
        ) : step === "locked" || step === "expired" ? (
          <div className="auth-options">
            <Button type="button" block onClick={() => { setStep("password"); setPassword(""); setError(""); }}>
              Back to sign in
            </Button>
          </div>
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
              maxLength={14}
              value={code}
              onChange={(e) => changeCode(e.target.value)}
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
            // MFA-01: switching between the authenticator code and a backup code is a real (outlined)
            // button, always visible - a plain text link here was easy to miss.
            <div className="auth-options">
              {step === "code" ? (
                <>
                  <Button variant="secondary" size="sm" onClick={() => switchToBackupCode()}>
                    Use a backup code instead
                  </Button>
                  <LinkButton onClick={() => setShowCodeHelp((open) => !open)} aria-expanded={showCodeHelp}>
                    Problem with your code?
                  </LinkButton>
                </>
              ) : (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => { setStep("code"); setBackupCode(""); setError(""); }}
                >
                  Use the authenticator code instead
                </Button>
              )}
            </div>
          )}
          {step === "code" && showCodeHelp && (
            <div className="auth-message" role="region" aria-label="Help with your code">
              <ul className="auth-help-list">
                <li>Check that your phone shows the correct time (set it to automatic).</li>
                <li>Lost your phone or deleted the app? Choose “Use a backup code instead” above.</li>
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
        {typeof window !== "undefined" &&
          (isTunnelHost(window.location.hostname) || !isSharedLandingHost(window.location.hostname)) && (
          <div className="auth-options" style={{ justifyContent: "center", marginTop: "var(--space-3)" }}>
            <a href={buildSharedLandingUrl(window.location)} className="app-text-link">
              Not your institution? Switch institution
            </a>
          </div>
        )}
      </section>
      <footer className="auth-footer">
        EDU 360 <span>·</span> One Platform. Every Institution. One Connected Ecosystem.
      </footer>
    </main>
  );
}
