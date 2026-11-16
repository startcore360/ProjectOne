import { useEffect, useState } from "react";
import { Notice } from "../../design-system/components";
import { readTrialExpired, TRIAL_EXPIRED_EVENT } from "../../services/trialNotice";

// E1-S7 (R2-24): after the server answers TRIAL_EXPIRED, explain why changes are refused. It stays
// for the rest of this tab's session; reading still works, so the user can keep looking.
export function TrialExpiredBanner() {
  const [endedAt, setEndedAt] = useState<string | undefined>(() => readTrialExpired());

  useEffect(() => {
    const onExpired = (event: Event) => setEndedAt(((event as CustomEvent<string | null>).detail ?? "") as string);
    window.addEventListener(TRIAL_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(TRIAL_EXPIRED_EVENT, onExpired);
  }, []);

  if (endedAt === undefined) return null;
  const date = endedAt ? new Date(endedAt).toLocaleDateString() : "";
  return (
    <Notice tone="warning">
      <strong>{date ? `Trial ended on ${date}.` : "The trial has ended."}</strong> This workspace is read-only until it is
      paid and activated. Contact your administrator.
    </Notice>
  );
}
