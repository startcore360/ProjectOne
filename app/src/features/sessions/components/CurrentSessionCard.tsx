import { Button, Kicker, Badge } from "../../../design-system/components";
import type { SessionResponse } from "../api/sessionsService";

export interface CurrentSessionCardProps {
  current: SessionResponse;
  revoking: boolean;
  revokeError: string | null;
  onRevoke: () => void;
  formatInstant: (value: string | null) => string;
}

export function CurrentSessionCard({
  current,
  revoking,
  revokeError,
  onRevoke,
  formatInstant,
}: CurrentSessionCardProps) {
  return (
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
          This session cannot be revoked from here (no session id is present on your current sign-in
          token).
        </p>
      )}

      <Button
        variant="danger"
        busy={revoking}
        disabled={!current.revocable}
        onClick={onRevoke}
      >
        {revoking ? "Signing out…" : "Sign out this session"}
      </Button>
    </section>
  );
}
