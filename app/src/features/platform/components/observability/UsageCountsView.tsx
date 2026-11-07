import { useEffect, useState } from "react";
import { Spinner } from "../../../../components/ui";
import { Icon } from "../../../../components/ui/Icon";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { MetricCard, Notice } from "../../../../design-system/components";
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

  useEffect(() => {
    let active = true;
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
  }, []);

  return (
    <div className="platform-page">
      <PageHeader
        title="Usage counts"
        description="Anonymous, combined platform usage numbers. No per-tenant or per-user figures are shown here."
      />
      {status === "error" && <Notice tone="danger">Could not load usage counts: {errorMessage}</Notice>}

      {status === "loading" ? (
        <div className="platform-empty-inline">
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

            {data.totalUserCountGap && (
              <p className="platform-sample-note" style={{ marginTop: "var(--space-4)", color: "var(--color-text-muted)" }}>
                <span aria-hidden="true" style={{ color: "var(--color-text-muted)" }}>
                  <Icon name="info" size={14} />
                </span>
                <span style={{ color: "var(--color-text-muted)" }}>
                  <strong style={{ color: "var(--color-text)", fontWeight: "var(--fw-medium)" }}>Total user count: not available.</strong>{" "}
                  {data.totalUserCountGap.replace(/^Not available:\s*/i, "")}
                </span>
              </p>
            )}
          </>
        )
      )}
    </div>
  );
}

// Export alias
export const UsageCountsPage = UsageCountsView;
