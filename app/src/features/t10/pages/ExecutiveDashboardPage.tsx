import { useEffect, useState } from "react";
import { PageHeader, MetricCard, Notice, Spinner, Panel } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { t10Api } from "../api/t10Api";

type DashboardResult = { key: string; data: Record<string, unknown> };
type DashboardFailure = { key: string; message: string };

export default function ExecutiveDashboardPage() {
  const tenant = useAppSelector((s) => s.auth.user?.tenantId ?? "");
  const institution = useAppSelector((s) => s.tenantPermissions.current?.institutionId ?? "");
  const key = `${tenant}:${institution}`;
  const [result, setResult] = useState<DashboardResult | null>(null);
  const [failure, setFailure] = useState<DashboardFailure | null>(null);
  const data = result?.key === key ? result.data : {};
  const error = failure?.key === key ? failure.message : "";
  const loading = Boolean(tenant && institution) && result?.key !== key && failure?.key !== key;

  useEffect(() => {
    if (tenant && institution) {
      t10Api
        .dashboard(tenant, institution)
        .then((metrics) => setResult({ key, data: metrics }))
        .catch(() =>
          setFailure({
            key,
            message: "Dashboard is unavailable. Ensure the read-only reporting connection (edu_report) is configured.",
          })
        );
    }
  }, [tenant, institution, key]);

  const labels: Record<string, { label: string; unit?: string }> = {
    activeStudents: { label: "Active students", unit: "enrolled" },
    activeStaff: { label: "Active staff", unit: "members" },
    libraryOutstandingFines: { label: "Outstanding library fines", unit: "INR" },
    openLibraryCirculations: { label: "Open circulations", unit: "books" },
    transportAllocations: { label: "Transport allocations", unit: "seats" },
    smsSent: { label: "SMS sent", unit: "messages" },
  };

  return (
    <main className="app-page">
      <PageHeader
        title="Executive Dashboard"
        description="Institutional indicators from the isolated, read-only reporting connection."
      />

      {!institution && (
        <Notice tone="info">
          No active institution scope is linked to your current role. Indicators will populate when an institution context is selected.
        </Notice>
      )}

      {loading && (
        <div style={{ display: "flex", justifyContent: "center", padding: "var(--space-6)" }}>
          <Spinner />
        </div>
      )}

      {error && <Notice tone="danger">{error}</Notice>}

      <Panel title="Institutional indicators" description="Key performance metrics updated in near real-time.">
        <div className="kpi-grid">
          {Object.entries(labels).map(([metric, config]) => (
            <MetricCard
              key={metric}
              label={config.label}
              value={data[metric] === undefined ? "—" : String(data[metric])}
              unit={data[metric] !== undefined ? config.unit : undefined}
            />
          ))}
        </div>
      </Panel>
    </main>
  );
}
