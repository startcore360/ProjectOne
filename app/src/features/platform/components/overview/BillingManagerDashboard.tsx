import { useEffect, useState } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Spinner } from "../../../../components/ui";
import { Notice, Panel } from "../../../../design-system/components";
import { platformOpsService, type InvoiceItem } from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";

type MetricState = { status: "loading" | "ok" | "not-measured" | "error" | "denied"; value?: string | number; detail?: string };
type RevenueReport = { currency?: string; metrics?: Record<string, unknown> };

function MetricTile({ title, metric }: { title: string; metric: MetricState }) {
  return <Panel title={title}>
    {metric.status === "loading" ? <Spinner label={`Loading ${title.toLowerCase()}...`} /> :
      metric.status === "error" ? <Notice tone="warning">{metric.detail || "Could not load this metric."}</Notice> :
      metric.status === "denied" ? <span>Not available for this role</span> :
      metric.status === "not-measured" ? <span>Not measured{metric.detail ? ` — ${metric.detail}` : ""}</span> :
      <strong className="platform-dashboard-value">{metric.value}</strong>}
  </Panel>;
}

function formatCurrency(currency: string, amount: unknown): string | null {
  if (typeof amount !== "number" && typeof amount !== "string") return null;
  const value = Number(amount);
  if (!Number.isFinite(value)) return null;
  return `${currency} ${value.toLocaleString("en-IN")}`;
}

export function BillingManagerDashboard() {
  const { can } = usePlatformAccess();
  const [metrics, setMetrics] = useState<Record<string, MetricState>>({
    invoicesDue: { status: "loading" }, invoicesPaid: { status: "loading" },
    invoicesOverdue: { status: "loading" }, revenue: { status: "loading" },
    renewals: { status: "loading" },
  });

  useEffect(() => {
    let active = true;
    const set = (key: string, value: MetricState) => { if (active) setMetrics(old => ({ ...old, [key]: value })); };
    const load = async (key: string, permitted: boolean, task: () => Promise<MetricState>) => {
      if (!permitted) { set(key, { status: "denied" }); return; }
      try { set(key, await task()); }
      catch (error) { set(key, { status: "error", detail: error instanceof Error ? error.message : "Request failed" }); }
    };

    const canManageInvoices = can("INVOICE_MANAGE");
    let invoicesPromise: Promise<InvoiceItem[]> | undefined;
    const loadInvoices = () => (invoicesPromise ??= platformOpsService.listInvoices());
    let overduePromise: Promise<InvoiceItem[]> | undefined;
    const loadOverdueInvoices = () => (overduePromise ??= platformOpsService.listOverdueInvoices());
    let revenueReportPromise: Promise<RevenueReport> | undefined;
    const loadRevenueReport = () => (revenueReportPromise ??= platformOpsService.getReport("REVENUE") as Promise<RevenueReport>);

    void load("invoicesDue", canManageInvoices, async () => {
      const invoices = await loadInvoices();
      return { status: "ok", value: invoices.filter(invoice => invoice.status === "SENT" && !invoice.overdue).length };
    });
    void load("invoicesPaid", canManageInvoices, async () => {
      const invoices = await loadInvoices();
      return { status: "ok", value: invoices.filter(invoice => invoice.status === "PAID").length };
    });
    void load("invoicesOverdue", canManageInvoices, async () => {
      const invoices = await loadOverdueInvoices();
      return { status: "ok", value: invoices.length };
    });

    void load("revenue", can("PLATFORM_REPORTS_VIEW"), async () => {
      const report = await loadRevenueReport();
      const revenue = formatCurrency(report.currency || "INR", report.metrics?.totalCollectionsThisMonth);
      return revenue == null ? { status: "not-measured" } : { status: "ok", value: revenue };
    });
    void load("renewals", can("PLATFORM_REPORTS_VIEW"), async () => {
      const report = await loadRevenueReport();
      const renewals = report.metrics?.renewalsDueIn60Days;
      return typeof renewals === "number" && Number.isFinite(renewals)
        ? { status: "ok", value: renewals }
        : { status: "not-measured" };
    });

    return () => { active = false; };
  }, [can]);

  const tiles = [
    ["Invoices due", metrics.invoicesDue],
    ["Invoices paid", metrics.invoicesPaid],
    ["Invoices overdue", metrics.invoicesOverdue],
    ["Revenue this month", metrics.revenue],
    ["Renewals due in 60 days", metrics.renewals],
  ] as const;

  return <div className="app-page">
    <PageHeader title="Billing Manager dashboard" description="Invoice position, collections and upcoming tenant renewals." />
    <div className="kpi-grid">{tiles.map(([title, metric]) => <MetricTile key={title} title={title} metric={metric} />)}</div>
  </div>;
}
