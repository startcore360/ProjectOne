import { useCallback, useEffect, useState } from "react";
import {
  PageHeader,
  MetricCard,
  Notice,
  Spinner,
  Panel,
  SelectField,
  Button,
} from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { t10Api } from "../api/t10Api";
import {
  institutionService,
  type InstitutionItem,
} from "../../institution-admin/api/institutionService";

type DashboardResult = { key: string; data: Record<string, unknown> };
type DashboardFailure = { key: string; message: string };

export default function ExecutiveDashboardPage() {
  const tenant = useAppSelector((s) => s.auth.user?.tenantId ?? "");
  const activeInstitution = useAppSelector(
    (s) => s.tenantPermissions.current?.institutionId ?? ""
  );

  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [selectedInstitution, setSelectedInstitution] = useState(activeInstitution);
  const [result, setResult] = useState<DashboardResult | null>(null);
  const [failure, setFailure] = useState<DashboardFailure | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    if (!tenant) return;
    void institutionService
      .listInstitutions(tenant)
      .then((res) => {
        const items = res?.items ?? [];
        setInstitutions(items);
        if (!selectedInstitution && items.length > 0) {
          setSelectedInstitution(activeInstitution || items[0].institutionId);
        }
      })
      .catch(() => {});
  }, [tenant, activeInstitution, selectedInstitution]);

  const effectiveInstitution = selectedInstitution || activeInstitution;
  const key = `${tenant}:${effectiveInstitution}`;
  const data = result?.key === key ? result.data : {};
  const error = failure?.key === key ? failure.message : "";
  const loading =
    Boolean(tenant && effectiveInstitution) &&
    result?.key !== key &&
    failure?.key !== key;

  const loadMetrics = useCallback(
    async (force = false) => {
      if (!tenant || !effectiveInstitution) return;
      if (force) setRefreshing(true);
      try {
        const metrics = await t10Api.dashboard(tenant, effectiveInstitution);
        setResult({ key, data: metrics });
        setFailure(null);
      } catch (err: unknown) {
        const msg =
          err instanceof Error
            ? err.message
            : "Dashboard is unavailable. Ensure the read-only reporting connection (edu_report) is configured.";
        setFailure({ key, message: msg });
      } finally {
        setRefreshing(false);
      }
    },
    [tenant, effectiveInstitution, key]
  );

  useEffect(() => {
    void loadMetrics();
  }, [loadMetrics]);

  const labels: Record<string, { label: string; unit?: string }> = {
    activeStudents: { label: "Active enrolled students", unit: "students" },
    activeStaff: { label: "Active staff & faculty", unit: "members" },
    libraryOutstandingFines: { label: "Outstanding library fines", unit: "INR" },
    openLibraryCirculations: { label: "Open book circulations", unit: "books" },
    transportAllocations: { label: "Active transport allocations", unit: "seats" },
    smsSent: { label: "SMS broadcasts sent", unit: "messages" },
  };

  return (
    <main className="app-page">
      <PageHeader
        title="Executive Dashboard"
        description="Institutional indicators and key governance metrics from the read-only reporting connection."
        actions={
          <div style={{ display: "flex", gap: "var(--space-2)", alignItems: "center" }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => void loadMetrics(true)}
              disabled={loading || refreshing || !effectiveInstitution}
              busy={refreshing}
            >
              Refresh indicators
            </Button>
          </div>
        }
      />

      <Panel
        title="Dashboard Scope"
        description="Select the institution branch to review real-time executive indicators."
      >
        <div style={{ maxWidth: "420px" }}>
          {institutions.length > 0 ? (
            <SelectField
              id="exec-inst-select"
              label="Selected Institution Branch"
              value={effectiveInstitution}
              onChange={(e) => setSelectedInstitution(e.target.value)}
              options={institutions.map((i) => ({
                value: i.institutionId,
                label: i.institutionName,
              }))}
            />
          ) : (
            <p style={{ margin: 0, fontSize: "var(--fs-body-sm)", color: "var(--color-text-muted)" }}>
              Active scope: <strong>{effectiveInstitution || "Default institution"}</strong>
            </p>
          )}
        </div>
      </Panel>

      {!effectiveInstitution && (
        <Notice tone="info">
          No active institution scope is linked to your current role. Select an institution above to view executive indicators.
        </Notice>
      )}

      {loading && (
        <div style={{ display: "flex", justifyContent: "center", padding: "var(--space-6)" }}>
          <Spinner />
        </div>
      )}

      {error && (
        <Notice tone="warning">
          <strong>Reporting note:</strong> {error}
        </Notice>
      )}

      <Panel
        title="Institutional indicators"
        description="Key performance metrics updated in near real-time from active registrations."
      >
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
