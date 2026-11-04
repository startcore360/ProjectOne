import { useEffect, useState } from "react";
import axios from "axios";
import { Button, Kicker } from "../../../design-system/components";
import { Badge, Spinner } from "../../../components/ui";
import { PageHeader } from "../../../components/layout/PageHeader";
import { EmptyState } from "../../../components/feedback/EmptyState";
import { ErrorState } from "../../../components/feedback/ErrorState";
import { clearTokens } from "../../../services/tokenStorage";
import { useAppDispatch } from "../../../store/hooks";
import { clearUser } from "../../../store/slices/authSlice";
import { sessionsService, type SessionResponse } from "../api/sessionsService";
import "./SessionsPage.css";

// AUTH_DEVICES_MANAGE (Sign-in epic, Scope: Self): "see own active devices and log them out."
// Phase 1 only - see sessionsService.ts. This screen says so honestly rather than presenting
// a device list that does not exist yet.
function formatInstant(value: string | null): string {
  if (!value) return "Unknown";
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

function backendMessage(err: unknown): string | null {
  if (axios.isAxiosError<{ error?: string }>(err)) {
    return err.response?.data?.error ?? null;
  }
  return null;
}

export default function SessionsPage() {
  const dispatch = useAppDispatch();
  const [sessions, setSessions] = useState<SessionResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [revokeError, setRevokeError] = useState<string | null>(null);
  const [revoking, setRevoking] = useState(false);

  // No synchronous setState call sits directly in this function's body - only inside the
  // promise callbacks below - so it is safe to call from the mount effect as-is; loading/error
  // start correctly set via useState's initial values above, and the "Retry" button resets
  // them itself before calling this again.
  const fetchSessions = () => {
    sessionsService
      .list()
      .then(({ data }) => {
        setSessions(data);
        setLoadError(null);
      })
      .catch((err) => {
        setLoadError(backendMessage(err) ?? "Could not load your session. Please try again.");
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchSessions();
  }, []);

  const retry = () => {
    setLoading(true);
    setLoadError(null);
    fetchSessions();
  };

  // Mirrors authManager.logout()'s own redirect pattern (clear local state, then hard-redirect
  // to /login even if something above failed) - but calling the session-revoke endpoint this
  // screen is actually about, not /api/auth/logout. The server has already revoked the Keycloak
  // session by the time this runs; clearing the local tokens/store here just keeps this tab's
  // own UI from continuing to claim "signed in" with a token whose session is gone.
  const revokeAndSignOut = async () => {
    setRevokeError(null);
    setRevoking(true);
    try {
      await sessionsService.revokeCurrent();
    } catch (err) {
      setRevokeError(backendMessage(err) ?? "Could not sign out this session. Please try again.");
      setRevoking(false);
      return;
    }
    clearTokens();
    dispatch(clearUser());
    window.location.replace("/login");
  };

  const current = sessions?.find((s) => s.current) ?? sessions?.[0] ?? null;

  return (
    <div className="sessions-page">
      <PageHeader
        title="My sessions"
        description="See your current sign-in session and sign it out. This is a single-session view today, not a list of every device you've signed in from."
      />

      {loading && (
        <section className="sessions-card">
          <Spinner label="Loading your session…" />
        </section>
      )}

      {!loading && loadError && (
        <ErrorState
          message={loadError}
          action={
            <Button variant="secondary" onClick={retry}>
              Retry
            </Button>
          }
        />
      )}

      {!loading && !loadError && sessions && sessions.length === 0 && (
        <EmptyState
          title="No active session found"
          description="Nothing to show for this account right now."
        />
      )}

      {!loading && !loadError && current && (
        <section className="sessions-card" aria-labelledby="sessions-title">
          <Kicker>CURRENT SESSION</Kicker>
          <h2 id="sessions-title" className="sessions-card__title">
            This device
          </h2>

          <dl className="sessions-readonly">
            <div>
              <dt>Status</dt>
              <dd>
                <Badge tone="success">Active</Badge>
              </dd>
            </div>
            <div>
              <dt>Signed in</dt>
              <dd>{formatInstant(current.issuedAt)}</dd>
            </div>
            <div>
              <dt>Expires</dt>
              <dd>{formatInstant(current.expiresAt)}</dd>
            </div>
          </dl>

          {revokeError && (
            <div className="sessions-message sessions-message--error" role="alert">
              {revokeError}
            </div>
          )}

          {!current.revocable && (
            <p className="sessions-notice">
              This session cannot be revoked from here (no session id is present on your current
              sign-in token).
            </p>
          )}

          <Button
            variant="danger"
            busy={revoking}
            disabled={!current.revocable}
            onClick={() => void revokeAndSignOut()}
          >
            {revoking ? "Signing out…" : "Sign out this session"}
          </Button>
        </section>
      )}
    </div>
  );
}
