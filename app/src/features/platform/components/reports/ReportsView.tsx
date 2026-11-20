import { useEffect, useState } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Button, MetricCard, Notice, Panel, SkeletonMetricCard } from "../../../../design-system/components";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import { getApiErrorMessage } from "../../../../services/apiHelper";
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
    setReport(null);
    try {
      const data = await platformOpsService.getReport(code);
      setReport(data);
    } catch (err: unknown) {
      setError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadReport(reportCode);
  }, [reportCode]);

  return (
    <div className="app-page">
      <PageHeader
        title="Platform Reports"
        description="Aggregated platform executive analytics, revenue collections, system throughput, and support SLAs."
      />

      {error && (
        <ErrorState
          message={error}
          action={<Button variant="secondary" onClick={() => void loadReport(reportCode)}>Retry</Button>}
        />
      )}

      <div className="app-toolbar">
        <label className="app-select-label">
          Report{" "}
          <select value={reportCode} onChange={(e) => setReportCode(e.target.value)}>
            <option value="PLATFORM_OVERVIEW">Platform Overview</option>
            <option value="BILLING_REVENUE">Billing &amp; Revenue</option>
            <option value="SUPPORT_SLA">Support &amp; SLA</option>
          </select>
        </label>
      </div>

      {loading ? (
        <div className="kpi-grid" role="status" aria-busy="true" aria-label="Loading platform report">
          <span className="sr-only">Loading platform report...</span>
          {Array.from({ length: 4 }, (_, index) => <SkeletonMetricCard key={index} />)}
        </div>
      ) : report ? (
        <>
          {(() => {
            const currencyCode = typeof report.metrics.currency === "string" ? report.metrics.currency : "INR";
            const formatCurrency = (val: number, options?: Intl.NumberFormatOptions) => {
              try {
                return new Intl.NumberFormat(currencyCode === "INR" ? "en-IN" : "en-US", {
                  style: "currency",
                  currency: currencyCode,
                  maximumFractionDigits: 0,
                  ...options,
                }).format(val);
              } catch {
                return `${currencyCode} ${val.toLocaleString()}`;
              }
            };

            return (
              <>
                {Object.entries(report.metrics).some(([key, value]) => /note|description|message/i.test(key) && typeof value === "string") && (
                  <Notice tone="info">
                    <strong>Reporting note</strong>
                    <p>{Object.entries(report.metrics).filter(([key, value]) => /note|description|message/i.test(key) && typeof value === "string").map(([, value]) => value).join(" ")}</p>
                  </Notice>
                )}
                {Object.entries(report.metrics).filter(([key]) => !/note|description|message|monthlyRevenue|monthlyChurn|renewalsDue$/i.test(key)).length > 0 && (
                  <div className="kpi-grid">
                    {Object.entries(report.metrics).filter(([key]) => !/note|description|message|monthlyRevenue|monthlyChurn|renewalsDue$/i.test(key)).map(([key, val]) => {
                      const formattedLabel = key
                        .replace(/([A-Z])/g, " $1")
                        .replace(/^./, (str) => str.toUpperCase());
                      const displayVal = val == null
                        ? "Not available"
                        : typeof val === "number" && (key.toLowerCase().includes("revenue") || key.toLowerCase().includes("amount") || key.toLowerCase().includes("collections"))
                          ? formatCurrency(val)
                          : String(val);

                      return (
                        <MetricCard
                          key={key}
                          label={formattedLabel}
                          value={displayVal}
                          note={val == null ? "This metric was not returned by the reporting service." : `Report: ${report.reportCode}`}
                          mark="chart"
                        />
                      );
                    })}
                  </div>
                )}

                {(reportCode === "BILLING_REVENUE" || reportCode === "REVENUE") && (
                  <Panel title="Revenue" description="Paid invoice revenue across the trailing twelve calendar months">
                    {(() => {
                      const monthly = Array.isArray(report.metrics.monthlyRevenue) ? report.metrics.monthlyRevenue as Array<{ month: string; revenue: number }> : [];
                      const maxValue = Math.max(1, ...monthly.map((item) => Number(item.revenue) || 0));
                      return monthly.length === 0 ? <p className="app-empty-inline">No monthly data returned.</p> : (
                        <div style={{ display: "grid", gridTemplateColumns: `repeat(${monthly.length}, minmax(0, 1fr))`, gap: "8px", alignItems: "end", minHeight: "190px" }}>
                          {monthly.map((item) => {
                            const value = Number(item.revenue) || 0;
                            const height = Math.max(3, (value / maxValue) * 140);
                            return <div key={item.month} style={{ display: "grid", gap: "6px", alignItems: "end", textAlign: "center", minWidth: 0 }}>
                              <small style={{ fontSize: "10px", color: "var(--color-text-muted)" }}>
                                {value === 0 ? formatCurrency(0) : formatCurrency(value, { notation: "compact", maximumFractionDigits: 1 })}
                              </small>
                              <div role="img" aria-label={`${item.month}: ${formatCurrency(value)} revenue`} style={{ height, background: "var(--color-action-primary)", borderRadius: "5px 5px 0 0", width: "100%" }} />
                              <small style={{ fontSize: "10px", color: "var(--color-text-muted)" }}>{item.month}</small>
                            </div>;
                          })}
                        </div>
                      );
                    })()}
                  </Panel>
                )}
                {(reportCode === "BILLING_REVENUE" || reportCode === "REVENUE") && (
                  <Panel title="Tenant churn" description="Tenants archived or deleted by month over the trailing twelve months">
                    {(() => {
                      const monthly = Array.isArray(report.metrics.monthlyChurn) ? report.metrics.monthlyChurn as Array<{ month: string; churn: number }> : [];
                      const maxValue = Math.max(1, ...monthly.map((item) => Number(item.churn) || 0));
                      return monthly.length === 0 ? <p className="app-empty-inline">No monthly data returned.</p> : (
                        <div style={{ display: "grid", gridTemplateColumns: `repeat(${monthly.length}, minmax(0, 1fr))`, gap: "8px", alignItems: "end", minHeight: "190px" }}>
                          {monthly.map((item) => {
                            const value = Number(item.churn) || 0;
                            const height = Math.max(3, (value / maxValue) * 140);
                            return <div key={item.month} style={{ display: "grid", gap: "6px", alignItems: "end", textAlign: "center", minWidth: 0 }}>
                              <small style={{ fontSize: "10px", color: "var(--color-text-muted)" }}>{value}</small>
                              <div role="img" aria-label={`${item.month}: ${value} tenants churned`} style={{ height, background: "var(--color-action-primary)", borderRadius: "5px 5px 0 0", width: "100%" }} />
                              <small style={{ fontSize: "10px", color: "var(--color-text-muted)" }}>{item.month}</small>
                            </div>;
                          })}
                        </div>
                      );
                    })()}
                  </Panel>
                )}
              </>
            );
          })()}
          {(reportCode === "BILLING_REVENUE" || reportCode === "REVENUE") && (
            <Panel title="Renewals due" description="Active tenants with a renewal date in the next 60 days">
              <strong>{Number(report.metrics.renewalsDueIn60Days ?? 0)} tenants</strong>
              {Array.isArray(report.metrics.renewalsDue) && report.metrics.renewalsDue.length > 0 ? (
                <ul style={{ marginBottom: 0 }}>
                  {(report.metrics.renewalsDue as Array<{ tenantId: string; tenantName: string; renewalDate: string }>).map((item) => <li key={item.tenantId}>{item.tenantName} — {item.renewalDate}</li>)}
                </ul>
              ) : <p className="app-empty-inline">No renewals due within 60 days.</p>}
            </Panel>
          )}
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
