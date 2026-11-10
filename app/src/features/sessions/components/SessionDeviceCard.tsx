import { Button } from "../../../design-system/components";
import { Icon } from "../../../components/ui/Icon";
import type { SessionResponse } from "../api/sessionsService";
import { activityStatus, deviceTitle } from "./deviceLabels";

export interface SessionDeviceCardProps {
  session: SessionResponse;
  busy: boolean;
  /** Time the list was last fetched (ms); "Active now / N min ago" is computed against it. */
  now: number;
  onSignOut: (session: SessionResponse) => void;
  formatInstant: (value: string | null | undefined) => string;
}

function deviceKind(session: SessionResponse): string {
  if (session.deviceType === "PHONE") return "Phone or tablet";
  if (session.deviceType === "COMPUTER") return "Computer";
  return "Device type not recorded";
}

/**
 * One signed-in device in "My sessions" (AUTH_DEVICES_MANAGE, SES-01). The current device is
 * rendered as a wide highlighted card; every other device as a compact card in a grid. Each
 * card carries a live status: Active now (pulsing dot) / Active N min ago / Idle.
 */
export function SessionDeviceCard({ session, busy, now, onSignOut, formatInstant }: SessionDeviceCardProps) {
  const title = deviceTitle(session);
  const titleId = `session-${session.sessionId ?? "current"}-title`;
  const current = session.current;
  const status = activityStatus(session, now);

  return (
    <article
      className={"session-device" + (current ? " session-device--current" : "")}
      aria-labelledby={titleId}
    >
      <div className="session-device__top">
        <span className="session-device__icon" aria-hidden="true">
          <Icon name={session.deviceType === "PHONE" ? "smartphone" : "monitor"} size={current ? 28 : 22} />
        </span>
        <div className="session-device__heading">
          <h3 id={titleId} className="session-device__title">{title}</h3>
          <span className="session-device__kind">{deviceKind(session)}</span>
        </div>
        <span className={`session-status session-status--${status.kind}`}>
          <span className="session-status__dot" aria-hidden="true" />
          {status.label}
        </span>
      </div>

      <dl className="session-device__meta">
        <div>
          <dt>Signed in</dt>
          <dd>{formatInstant(session.issuedAt)}</dd>
        </div>
        {!current && (
          <div>
            <dt>Last active</dt>
            <dd>{formatInstant(session.lastActiveAt)}</dd>
          </div>
        )}
        <div>
          <dt>IP address</dt>
          <dd>{session.ipAddress ?? "Not recorded"}</dd>
        </div>
      </dl>

      {session.deviceType === "UNKNOWN" && !current && (
        <p className="session-device__note">Signed in before device details were recorded.</p>
      )}
      {!session.revocable && (
        <p className="session-device__note">
          This session can't be signed out from here (your sign-in token has no session id).
        </p>
      )}

      <div className="session-device__action">
        <Button
          variant={current ? "danger" : "secondary"}
          busy={busy}
          disabled={!session.revocable}
          onClick={() => onSignOut(session)}
          aria-label={current ? "Sign out this device" : `Sign out ${title}`}
        >
          {busy ? "Signing out…" : current ? "Sign out this device" : "Sign out"}
        </Button>
      </div>
    </article>
  );
}
