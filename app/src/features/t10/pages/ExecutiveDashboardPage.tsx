import { useEffect, useState } from "react";
import { PageHeader, MetricCard } from "../../../design-system/components";
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
      t10Api.dashboard(tenant, institution)
        .then((metrics) => setResult({ key, data: metrics }))
        .catch(() => setFailure({ key, message: "Dashboard is unavailable. Ensure the read-only edu_report connection is configured." }));
    }
  }, [tenant, institution, key]);

  const labels: Record<string, string> = {
    activeStudents: "Active students",
    activeStaff: "Active staff",
    libraryOutstandingFines: "Outstanding library fines",
    openLibraryCirculations: "Open circulations",
    transportAllocations: "Transport allocations",
    smsSent: "SMS sent",
  };
  return <main className="app-page">
    <PageHeader title="Executive Dashboard" description="Institutional indicators from the isolated, read-only reporting connection." />
    {loading && <p role="status">Loading executive metrics…</p>}
    {error && <p role="alert">{error}</p>}
    <div className="app-grid">{Object.entries(labels).map(([metric, label]) =>
      <MetricCard key={metric} label={label} value={data[metric] === undefined ? "—" : String(data[metric])} />)}
    </div>
  </main>;
}
