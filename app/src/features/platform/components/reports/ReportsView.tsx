import { useEffect, useState } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Button, MetricCard, Notice, Panel } from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import { platformOpsService, type PlatformReportItem } from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";

export function ReportsView() {
  const { role } = usePlatformAccess();
  const [reportCode, setReportCode] = useState<string>(
    role === "BILLING_MANAGER" ? "BILLING_REVENUE" : role === "SUPPORT_MANAGER" ? "SUPPORT_SLA" : "PLATFORM_OVERVIEW"
  );
  const [report, setReport] = useState<PlatformReportItem | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const loadReport = async (code: string) => {
    setLoading(true);
    setError("");
    try {
      const data = await platformOpsService.getReport(code);
      setReport(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load report");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReport(reportCode);
  }, [reportCode]);

  return (
    <div className="platform-page">
      <PageHeader
        title="Platform Reports"
        description="Aggregated platform executive analytics, revenue collections, system throughput, and support SLAs."
      />

      {error && <Notice tone="danger">{error}</Notice>}

      <div style={{ display: "flex", gap: "8px", flexWrap: "wrap", marginBottom: "8px" }}>
        <Button
          size="sm"
          variant={reportCode === "PLATFORM_OVERVIEW" ? "primary" : "secondary"}
          onClick={() => setReportCode("PLATFORM_OVERVIEW")}
        >
          Platform Overview
        </Button>
        <Button
          size="sm"
          variant={reportCode === "BILLING_REVENUE" ? "primary" : "secondary"}
          onClick={() => setReportCode("BILLING_REVENUE")}
        >
          Billing & Revenue
        </Button>
        <Button
          size="sm"
          variant={reportCode === "SUPPORT_SLA" ? "primary" : "secondary"}
          onClick={() => setReportCode("SUPPORT_SLA")}
        >
          Support & SLA
        </Button>
      </div>

      {loading ? (
        <div className="platform-empty-inline">
          <Spinner label="Generating platform report..." />
        </div>
      ) : report ? (
        <>
          <div className="kpi-grid">
            {Object.entries(report.metrics).map(([key, val]) => {
              const formattedLabel = key
                .replace(/([A-Z])/g, " $1")
                .replace(/^./, (str) => str.toUpperCase());
              const displayVal =
                typeof val === "number" && (key.toLowerCase().includes("revenue") || key.toLowerCase().includes("amount") || key.toLowerCase().includes("collections"))
                  ? `₹${val.toLocaleString("en-IN")}`
                  : String(val);

              return (
                <MetricCard
                  key={key}
                  label={formattedLabel}
                  value={displayVal}
                  note={`Report: ${report.reportCode}`}
                  mark="chart"
                />
              );
            })}
          </div>

          <Panel title={report.reportName} description={`Generated at ${new Date(report.generatedAt).toLocaleString()}`}>
            <div style={{ padding: "8px 0" }}>
              <p style={{ margin: 0, color: "var(--color-text-muted)", fontSize: "var(--fs-body)" }}>
                This report summarizes authenticated live operations across enrolled tenants and active subscriptions.
                Currency applied: <strong>{report.currency}</strong>.
              </p>
            </div>
          </Panel>
        </>
      ) : null}
    </div>
  );
}

export const ReportsPage = ReportsView;
