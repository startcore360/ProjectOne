import { useEffect, useState } from "react";
import { Button, Notice } from "../../../../design-system/components";
import { serverMessage } from "../../../auth/serverMessage";
import { userManagementService } from "../../api/userManagementService";

// E1-S5 (WF-01): warn at 90% of the user limit, say plainly when it is full, and let the admin ask
// for an upgrade (a BILLING support ticket). Shows nothing below 90% or when no limit is set.
export function UserLimitBanner({ tenantId }: { tenantId: string }) {
  const [usage, setUsage] = useState<{ limit: number | null; active: number; percent: number } | null>(null);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    userManagementService
      .getUserLimit(tenantId)
      .then(({ data }) => { if (active) setUsage(data); })
      .catch(() => { /* No banner if the reading fails; creating a user still checks the limit. */ });
    return () => { active = false; };
  }, [tenantId]);

  if (!usage || usage.limit == null || usage.percent < 90) return null;
  const full = usage.active >= usage.limit;

  const request = async () => {
    setSending(true);
    setError("");
    try {
      await userManagementService.requestUpgrade(tenantId);
      setSent(true);
    } catch (err) {
      setError(serverMessage(err));
    } finally {
      setSending(false);
    }
  };

  return (
    <Notice tone={full ? "danger" : "warning"}>
      <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: "var(--space-3)", justifyContent: "space-between" }}>
        <span>
          {full
            ? `User limit reached: ${usage.active} of ${usage.limit} accounts are in use. New accounts can't be created until the plan is upgraded.`
            : `You are using ${usage.active} of ${usage.limit} accounts (${usage.percent}%). Request an upgrade before you run out.`}
          {sent && " Your upgrade request was sent to the EDU 360 billing team."}
          {error && ` ${error}`}
        </span>
        {!sent && (
          <Button size="sm" variant="secondary" busy={sending} onClick={() => void request()}>
            Request upgrade
          </Button>
        )}
      </div>
    </Notice>
  );
}
