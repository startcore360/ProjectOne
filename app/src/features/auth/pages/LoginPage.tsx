import { type FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { Button, Kicker, LinkButton, PasswordField, TextField } from "../../../design-system/components";
import Edu360Logo from "../../../assets/Edu360Logo";
import { login } from "../../../services/authManager";
import { clearLogoutReason, getLogoutReason } from "../../../services/logoutNotice";
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
  const [step, setStep] = useState<"password" | "code">("password");
  const [code, setCode] = useState("");
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
    setBusy(true);
    try {
      const user = await login(email.trim(), password, step === "code" ? code : undefined);
      navigate(user.mustChangePassword ? "/change-password" : "/");
    } catch (err) {
      const body = axios.isAxiosError<{ code?: string; error?: string; enrolmentUrl?: string }>(err)
        ? err.response?.data
        : undefined;
      if (body?.code === "MFA_REQUIRED") {
        // Password was right; ask for the authenticator code (REG-I01-1).
        setStep("code");
        setCode("");
        setBusy(false);
        return;
      }
      if (body?.code === "MFA_ENROLMENT_REQUIRED") {
        // No authenticator set up yet: hand off to Keycloak's own set-up page, then sign in again.
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
  return (
    <main className="auth-screen">
      <div className="auth-brand">
        <Edu360Logo className="auth-brand__logo" />
      </div>
      <section className="auth-card" aria-labelledby="login-title">
        <Kicker>WELCOME BACK</Kicker>
        <h1 id="login-title" className="auth-card__title">
          Welcome back.
        </h1>
        <p className="auth-card__subtitle">
          Sign in to continue to your workspace.
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
