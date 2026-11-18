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
    const load = async (keys: string[], permitted: boolean, task: () => Promise<Record<string, MetricState>>) => {
      if (!permitted) { keys.forEach(key => set(key, { status: "denied" })); return; }
      try {
        const values = await task();
        Object.entries(values).forEach(([key, value]) => set(key, value));
      } catch (error) {
        const detail = error instanceof Error ? error.message : "Request failed";
        keys.forEach(key => set(key, { status: "error", detail }));
      }
    };

    void load(["invoicesDue", "invoicesPaid", "invoicesOverdue"], can("INVOICE_MANAGE"), async () => {
      const invoices: InvoiceItem[] = await platformOpsService.listInvoices();
      const overdue = invoices.filter(invoice => invoice.overdue || invoice.status === "OVERDUE").length;
      const paid = invoices.filter(invoice => invoice.status === "PAID").length;
      const due = invoices.filter(invoice => invoice.status === "SENT" && !invoice.overdue).length;
      return {
        invoicesDue: { status: "ok", value: due },
        invoicesPaid: { status: "ok", value: paid },
        invoicesOverdue: { status: "ok", value: overdue },
      };
    });

    void load(["revenue", "renewals"], can("PLATFORM_REPORTS_VIEW"), async () => {
      const report = await platformOpsService.getReport("REVENUE") as RevenueReport;
      const revenue = formatCurrency(report.currency || "INR", report.metrics?.totalCollectionsThisMonth);
      const renewals = report.metrics?.renewalsDueIn60Days;
      return {
        revenue: revenue == null ? { status: "not-measured" } : { status: "ok", value: revenue },
        renewals: typeof renewals === "number" && Number.isFinite(renewals)
          ? { status: "ok", value: renewals }
          : { status: "not-measured" },
      };
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
