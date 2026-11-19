import { useEffect, useState } from "react";
import { Spinner } from "../../../../components/ui";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Button, MetricCard } from "../../../../design-system/components";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import {
  readErrorMessage,
  type FetchStatus,
  type UsageCounts,
  USAGE_METRIC_LABELS,
} from "../common/types";
import api from "../../../../services/axios";

export function UsageCountsView() {
  const [status, setStatus] = useState<FetchStatus>("loading");
  const [data, setData] = useState<UsageCounts | null>(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    let active = true;
    // "loading" is set by the initial state and by the Retry handler, not here.
    api
      .get("/platform/usage-counts")
      .then((res) => {
        if (!active) return;
        setData(res.data);
        setStatus("success");
      })
      .catch((err) => {
        if (!active) return;
        setErrorMessage(readErrorMessage(err, "Could not load usage counts."));
        setStatus("error");
      });
    return () => {
      active = false;
    };
  }, [retryCount]);

  return (
    <div className="app-page">
      <PageHeader
        title="Usage counts"
        description="Anonymous, combined platform usage numbers from the nightly tenant-isolated rollup. No per-tenant or per-user figures are shown here."
      />
      {status === "error" && (
        <ErrorState
          message={`Could not load usage counts: ${errorMessage}`}
          action={<Button variant="secondary" onClick={() => { setStatus("loading"); setRetryCount((c) => c + 1); }}>Retry</Button>}
        />
      )}

      {status === "loading" ? (
        <div className="app-empty-inline">
          <Spinner label="Loading usage counts" />
        </div>
      ) : (
        data && (
          <>
            <div className="kpi-grid">
              <MetricCard label="Tenants" value={data.totalTenantCount} note="Across the platform" mark="book" />
              {data.metrics.map((m) => (
                <MetricCard
                  key={m.metricCode}
                  label={USAGE_METRIC_LABELS[m.metricCode] ?? m.metricCode}
                  value={m.value ?? "Not available"}
                  note={m.asOfDate ? `As of ${m.asOfDate}` : "No value computed yet"}
                  mark="clock"
                />
              ))}
            </div>


          </>
        )
      )}
    </div>
  );
}

// Export alias
export const UsageCountsPage = UsageCountsView;
