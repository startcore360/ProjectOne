import { useEffect, useState } from "react";
import axios from "axios";
import {
  Button,
  ConfirmDialog,
  Spinner,
  PageHeader,
  EmptyState,
  ErrorState,
} from "../../../design-system/components";
import { clearTokens } from "../../../services/tokenStorage";
import { useAppDispatch } from "../../../store/hooks";
import { clearUser } from "../../../store/slices/authSlice";
import { sessionsService, type SessionResponse } from "../api/sessionsService";
import { SessionDeviceCard, activityStatus, deviceTitle } from "../components";
import "./SessionsPage.css";

// AUTH_DEVICES_MANAGE (Sign-in epic, Scope: Self): "see own active devices and log them out."
// SES-01: every device the user is signed in on. Layout: a summary of active devices, then
// "This device" as a highlighted card, then the other active devices as a card grid. Signing out
// THIS device returns to /login; signing out OTHER devices ends them remotely.
function formatInstant(value: string | null | undefined): string {
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

type ConfirmTarget = SessionResponse | "all-others" | null;

export default function SessionsPage() {
  const dispatch = useAppDispatch();
  const [sessions, setSessions] = useState<SessionResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<ConfirmTarget>(null);

  // When the list was last fetched: "Active now / N min ago" is computed against this, so the
  // statuses stay consistent with the data they were computed from.
  const [fetchedAt, setFetchedAt] = useState(() => Date.now());

  // No synchronous setState sits directly in this function's body - only inside the promise
  // callbacks - so it is safe to call from the mount effect as-is. The list is fetched when the
  // page opens and when the user presses Refresh (no background polling, by design).
  const fetchSessions = () => {
    sessionsService
      .list()
      .then(({ data }) => {
        setSessions(data);
        setFetchedAt(Date.now());
        setLoadError(null);
      })
      .catch((err) => {
        setLoadError(backendMessage(err) ?? "Could not load your sessions. Please try again.");
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchSessions();
  }, []);

  const retry = () => {
    setLoading(true);
    setLoadError(null);
    setNotice(null);
    setActionError(null);
    fetchSessions();
  };

  const current = sessions?.find((s) => s.current) ?? null;
  const others = sessions?.filter((s) => !s.current) ?? [];
  const phones = sessions?.filter((s) => s.deviceType === "PHONE").length ?? 0;
  const computers = sessions?.filter((s) => s.deviceType === "COMPUTER").length ?? 0;
  const activeNow = sessions?.filter((s) => activityStatus(s, fetchedAt).kind === "live").length ?? 0;

  // This device: the server ends the Keycloak session, then this tab clears its own tokens and
  // goes to /login (same pattern as authManager.logout()).
  const signOutThisDevice = async () => {
    setActionError(null);
    setBusyId("current");
    try {
      await sessionsService.revokeCurrent();
    } catch (err) {
      setActionError(backendMessage(err) ?? "Could not sign out this device. Please try again.");
      setBusyId(null);
      return;
    }
    clearTokens();
    dispatch(clearUser());
    window.location.replace("/login");
  };

  const signOutOtherDevice = async (session: SessionResponse) => {
    if (!session.sessionId) return;
    setActionError(null);
    setNotice(null);
    setBusyId(session.sessionId);
    try {
      await sessionsService.revoke(session.sessionId);
      setSessions((prev) => prev?.filter((s) => s.sessionId !== session.sessionId) ?? prev);
      setNotice(`${deviceTitle(session)} has been signed out.`);
    } catch (err) {
      setActionError(backendMessage(err) ?? "Could not sign out that device. Please try again.");
    } finally {
      setBusyId(null);
      setConfirmTarget(null);
    }
  };

  // One call per device (the API signs out one session at a time); stops counting failures
  // rather than aborting, so one stale session never blocks the rest.
  const signOutAllOthers = async () => {
    const targets = others.filter((s) => s.revocable && s.sessionId);
    setActionError(null);
    setNotice(null);
    setBusyId("all-others");
    const signedOut: string[] = [];
    let failed = 0;
    for (const s of targets) {
      try {
        await sessionsService.revoke(s.sessionId as string);
        signedOut.push(s.sessionId as string);
      } catch {
        failed += 1;
      }
    }
    setSessions((prev) => prev?.filter((s) => !s.sessionId || !signedOut.includes(s.sessionId)) ?? prev);
    if (failed > 0) {
      setActionError(`${failed} device${failed === 1 ? "" : "s"} could not be signed out. Please try again.`);
    }
    if (signedOut.length > 0) {
      setNotice(`Signed out of ${signedOut.length} other device${signedOut.length === 1 ? "" : "s"}.`);
    }
    setBusyId(null);
    setConfirmTarget(null);
  };

  const confirmIsCurrent = confirmTarget !== null && confirmTarget !== "all-others" && confirmTarget.current;
  const confirmTitle =
    confirmTarget === "all-others"
      ? `Sign out of ${others.length} other device${others.length === 1 ? "" : "s"}?`
      : confirmIsCurrent
        ? "Sign out of this device?"
        : `Sign out ${confirmTarget ? deviceTitle(confirmTarget) : "this device"}?`;
  const confirmMessage =
    confirmTarget === "all-others"
      ? "Every other device will need to sign in again. This device stays signed in."
      : confirmIsCurrent
        ? "You'll be taken to the sign-in page. Your other devices stay signed in."
        : "That device will need to sign in again. This device stays signed in.";

  const ready = !loading && !loadError && sessions;

  return (
    <div className="sessions-page">
      <PageHeader
        title="My sessions"
        description="Devices where you're signed in. Signing in again on the same browser replaces that browser's old session; your other devices stay signed in."
        actions={
          ready && sessions.length > 0 ? (
            <Button variant="secondary" size="sm" onClick={retry}>
              Refresh
            </Button>
          ) : undefined
        }
      />

      {loading && (
        <section className="sessions-panel">
          <Spinner label="Loading your sessions…" />
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

      {ready && sessions.length === 0 && (
        <EmptyState title="No active sessions found" description="Nothing to show for this account right now." />
      )}

      {ready && sessions.length > 0 && (
        <>
          {/* Current active devices at a glance. */}
          <section className="sessions-summary" aria-label="Active devices summary">
            <div className="sessions-summary__item sessions-summary__item--total">
              <span className="sessions-summary__value">
                <span className="session-status__dot session-status__dot--live" aria-hidden="true" />
                {activeNow}
              </span>
              <span className="sessions-summary__label">Active now</span>
            </div>
            <div className="sessions-summary__item">
              <span className="sessions-summary__value">{sessions.length}</span>
              <span className="sessions-summary__label">
                Signed-in device{sessions.length === 1 ? "" : "s"}
              </span>
            </div>
            <div className="sessions-summary__item">
              <span className="sessions-summary__value">{computers}</span>
              <span className="sessions-summary__label">Computer{computers === 1 ? "" : "s"}</span>
            </div>
            <div className="sessions-summary__item">
              <span className="sessions-summary__value">{phones}</span>
              <span className="sessions-summary__label">Phone{phones === 1 ? "" : "s"} / tablet{phones === 1 ? "" : "s"}</span>
            </div>
          </section>

          {notice && (
            <div className="sessions-message sessions-message--success" role="status">
              {notice}
            </div>
          )}
          {actionError && (
            <div className="sessions-message sessions-message--error" role="alert">
              {actionError}
            </div>
          )}

          {current && (
            <section className="sessions-section" aria-labelledby="sessions-current-title">
              <h2 id="sessions-current-title" className="sessions-section__title">
                This device
              </h2>
              <SessionDeviceCard
                session={current}
                busy={busyId === "current"}
                now={fetchedAt}
                onSignOut={setConfirmTarget}
                formatInstant={formatInstant}
              />
            </section>
          )}

          <section className="sessions-section" aria-labelledby="sessions-others-title">
            <div className="sessions-section__header">
              <h2 id="sessions-others-title" className="sessions-section__title">
                Other active devices <span className="sessions-section__count">{others.length}</span>
              </h2>
              {others.length > 1 && (
                <Button
                  variant="secondary"
                  size="sm"
                  busy={busyId === "all-others"}
                  onClick={() => setConfirmTarget("all-others")}
                >
                  Sign out all other devices
                </Button>
              )}
            </div>
            {others.length === 0 ? (
              <p className="sessions-empty">You're not signed in anywhere else.</p>
            ) : (
              <div className="session-device-grid">
                {others.map((session) => (
                  <SessionDeviceCard
                    key={session.sessionId ?? deviceTitle(session)}
                    session={session}
                    busy={busyId === "all-others" || (busyId !== null && busyId === session.sessionId)}
                    now={fetchedAt}
                    onSignOut={setConfirmTarget}
                    formatInstant={formatInstant}
                  />
                ))}
              </div>
            )}
          </section>
        </>
      )}

      <ConfirmDialog
        isOpen={confirmTarget !== null}
        onClose={() => setConfirmTarget(null)}
        onConfirm={() => {
          if (confirmTarget === null) return;
          if (confirmTarget === "all-others") {
            void signOutAllOthers();
          } else if (confirmTarget.current) {
            setConfirmTarget(null);
            void signOutThisDevice();
          } else {
            void signOutOtherDevice(confirmTarget);
          }
        }}
        title={confirmTitle}
        message={confirmMessage}
        confirmLabel="Sign out"
        busy={busyId !== null && !confirmIsCurrent}
      />
    </div>
  );
}
