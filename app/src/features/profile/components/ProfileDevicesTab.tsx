import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Notice, SkeletonCard, SkeletonText } from "../../../design-system/components";
import { sessionsService, type SessionResponse } from "../../sessions/api/sessionsService";

// AUTH_DEVICES_MANAGE (Scope: Self) - a summary only. Signing devices out stays on the full
// "Active devices" page (/sessions), which already handles the confirm and error flows.
export function ProfileDevicesTab() {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState<SessionResponse[] | null>(null);
  const [error, setError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    sessionsService
      .list()
      .then(({ data }) => {
        if (active) setSessions(data ?? []);
      })
      .catch(() => {
        if (active) setError(true);
      });

    return () => {
      active = false;
    };
  }, [reloadKey]);

  const retry = () => {
    setError(false);
    setSessions(null);
    setReloadKey((key) => key + 1);
  };
  const deviceCount = sessions?.length ?? 0;

  return (
    <section className="app-profile-card" aria-labelledby="profile-devices-title">
      <header className="app-profile-card__header">
        <div>
          <h3 id="profile-devices-title" className="app-profile-card__title">Where you are signed in</h3>
          <p className="app-profile-card__description">
            {sessions === null && !error
              ? "Checking where you are signed in…"
              : error
                ? "Your devices could not be loaded."
                : `You are signed in on ${deviceCount} ${deviceCount === 1 ? "device" : "devices"}.`}
          </p>
        </div>
        <Button variant="secondary" onClick={() => navigate("/sessions")}>
          Manage devices
        </Button>
      </header>

      {sessions === null && !error && (
        <div className="app-profile-loading" role="status" aria-busy="true" aria-label="Loading your devices">
          <span className="sr-only">Loading your devices...</span>
          <SkeletonCard><SkeletonText height="1rem" width="55%" /><SkeletonText lines={2} /></SkeletonCard>
        </div>
      )}

      {error && (
        <Notice tone="danger">
          Could not load your devices.{" "}
          <button type="button" className="eds-link-button" onClick={retry}>Try again</button>
        </Notice>
      )}
      {sessions?.length === 0 && <p className="app-profile-empty-state">No active devices were found.</p>}
    </section>
  );
}
