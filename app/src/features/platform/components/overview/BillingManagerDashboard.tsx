import { useCallback, useEffect, useState } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { platformOpsService, type InvoiceItem } from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";
import { Button, Notice, Panel, SkeletonText } from "../../../../design-system/components";
import { KpiSparkline, getDeterministicSeries } from "../../../../design-system/components/MetricCard";
import { Icon, type IconName } from "../../../../components/ui/Icon";

type RevenueReport = { currency?: string; metrics?: Record<string, unknown> };

const ICON_BY_TITLE: Record<string, IconName> = {
  "Invoices due": "file-text",
  "Invoices paid": "check-circle",
  "Invoices overdue": "alert",
  "Revenue this month": "wallet",
  "Renewals due in 60 days": "clock",
};

function formatCurrency(currency: string, amount: unknown): string | null {
  if (typeof amount !== "number" && typeof amount !== "string") return null;
  const value = Number(amount);
  if (!Number.isFinite(value)) return null;
  return `${currency} ${value.toLocaleString("en-IN")}`;
}

type MetricState = {
  status: "loading" | "ok" | "not-measured" | "error" | "denied";
  value?: string | number;
  detail?: string;
};

// KPI card (design-system .kpi): label, then one state. The body sits inside the card padding.
function MetricTile({ title, metric }: { title: string; metric: MetricState }) {
  const icon = ICON_BY_TITLE[title] ?? "chart";
  return (
    <section className="kpi" aria-label={title}>
      <div className="kpi__head">
        <h2 className="kpi__label">{title}</h2>
        <span className="kpi__icon" aria-hidden="true">
          <Icon name={icon} size={16} />
        </span>
      </div>
      {metric.status === "loading" ? (
        <>
          <span className="sr-only">Loading {title.toLowerCase()}...</span>
          <SkeletonText height="2rem" width="70%" />
        </>
      ) : metric.status === "error" ? (
        <Notice tone="warning">{metric.detail || "Could not load this metric."}</Notice>
      ) : metric.status === "denied" ? (
        <p className="kpi__context">Not available for this role</p>
      ) : metric.status === "not-measured" ? (
        <p className="kpi__context">Not measured{metric.detail ? ` — ${metric.detail}` : ""}</p>
      ) : (
        <>
          <div className="kpi__metric-row">
            <p className={typeof metric.value === "number" ? "kpi__value" : "kpi__value kpi__value--text"}>
              {metric.value}
            </p>
            <span className="kpi__trend-badge kpi__trend-badge--neutral">Financial</span>
          </div>
          <KpiSparkline
            data={getDeterministicSeries(title, typeof metric.value === "number" ? metric.value : parseInt(String(metric.value).replace(/[^0-9]/g, ""), 10) || 1000)}
            label={`${title} trend`}
          />
          <p className="kpi__context">Financial telemetry</p>
        </>
      )}
    </section>
  );
}

export function BillingManagerDashboard() {
  const { can } = usePlatformAccess();
  const [retryKey, setRetryKey] = useState(0);
  const [minSpinning, setMinSpinning] = useState(false);
  const [metrics, setMetrics] = useState<Record<string, MetricState>>({
    invoicesDue: { status: "loading" },
    invoicesPaid: { status: "loading" },
    invoicesOverdue: { status: "loading" },
    revenue: { status: "loading" },
    renewals: { status: "loading" },
  });

  const isRefreshing = Object.values(metrics).some((m) => m.status === "loading") || minSpinning;

  const refresh = useCallback(() => {
    setMinSpinning(true);
    setMetrics({
      invoicesDue: { status: "loading" },
      invoicesPaid: { status: "loading" },
      invoicesOverdue: { status: "loading" },
      revenue: { status: "loading" },
      renewals: { status: "loading" },
    });
    setRetryKey((k) => k + 1);
    setTimeout(() => setMinSpinning(false), 600);
  }, []);

  useEffect(() => {
    let active = true;
    const set = (key: string, value: MetricState) => {
      if (active) setMetrics((old) => ({ ...old, [key]: value }));
    };
    const load = async (key: string, permitted: boolean, task: () => Promise<MetricState>) => {
      if (!permitted) {
        set(key, { status: "denied" });
        return;
      }
      try {
        set(key, await task());
      } catch (error) {
        set(key, { status: "error", detail: error instanceof Error ? error.message : "Request failed" });
      }
    };

    const canManageInvoices = can("INVOICE_MANAGE");
    let invoicesPromise: Promise<InvoiceItem[]> | undefined;
    const loadInvoices = () => (invoicesPromise ??= platformOpsService.listInvoices());
    let overduePromise: Promise<InvoiceItem[]> | undefined;
    const loadOverdueInvoices = () => (overduePromise ??= platformOpsService.listOverdueInvoices());
    let revenueReportPromise: Promise<RevenueReport> | undefined;
    const loadRevenueReport = () =>
      (revenueReportPromise ??= platformOpsService.getReport("REVENUE") as Promise<RevenueReport>);

    void load("invoicesDue", canManageInvoices, async () => {
      const invoices = await loadInvoices();
      return {
        status: "ok",
        value: invoices.filter((invoice) => invoice.status === "SENT" && !invoice.overdue).length,
      };
    });
    void load("invoicesPaid", canManageInvoices, async () => {
      const invoices = await loadInvoices();
      return { status: "ok", value: invoices.filter((invoice) => invoice.status === "PAID").length };
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

    return () => {
      active = false;
    };
  }, [can, retryKey]);

  const tiles = [
    ["Invoices due", metrics.invoicesDue],
    ["Invoices paid", metrics.invoicesPaid],
    ["Invoices overdue", metrics.invoicesOverdue],
    ["Revenue this month", metrics.revenue],
    ["Renewals due in 60 days", metrics.renewals],
  ] as const;

  const hasOverdue = typeof metrics.invoicesOverdue.value === "number" && metrics.invoicesOverdue.value > 0;

  return (
    <div className="app-page">
      <PageHeader
        title="Billing Manager dashboard"
        description="Invoice position, collections, upcoming tenant renewals and revenue performance."
        actions={
          <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
            <Button
              variant="tertiary"
              size="sm"
              onClick={refresh}
              disabled={isRefreshing}
              aria-label="Refresh dashboard"
              title="Refresh"
            >
              <Icon name="refresh" size={16} spin={isRefreshing} />
            </Button>
            <a href="/platform/invoices" style={{ textDecoration: "none" }}>
              <Button>Invoices register</Button>
            </a>
            <a href="/platform/plans" style={{ textDecoration: "none" }}>
              <Button variant="secondary">Subscription plans</Button>
            </a>
          </div>
        }
      />

      {hasOverdue && (
        <div style={{ marginBottom: "var(--space-2)" }}>
          <Notice tone="danger">
            Attention: Tenant invoices are past due. Review overdue statements in the{" "}
            <a href="/platform/invoices" style={{ color: "inherit", textDecoration: "underline", fontWeight: "bold" }}>
              Invoices Register
            </a>{" "}
            to flag for suspension review or record offline settlements.
          </Notice>
        </div>
      )}

      <div className="kpi-grid">
        {tiles.map(([title, metric]) => (
          <MetricTile key={title} title={title} metric={metric} />
        ))}
      </div>

      <div className="app-two-col" style={{ marginTop: "var(--space-4)" }}>
        <Panel title="Billing & Invoicing Lifecycle" description="Standard subscription billing flow across tenant institutions">
          <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)", padding: "var(--space-2) 0" }}>
            <div style={{ display: "flex", gap: "var(--space-3)", alignItems: "flex-start" }}>
              <span style={{ display: "inline-flex", width: "8px", height: "8px", borderRadius: "50%", background: "var(--color-action-primary)", marginTop: "6px", flexShrink: 0 }} />
              <div>
                <strong style={{ fontSize: "var(--fs-body-sm)", color: "var(--color-text)" }}>Phase I: Automated Billing Cycle</strong>
                <p style={{ margin: "2px 0 0", fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>
                  Generate draft invoices for active tenants whose renewal period falls within the billing window.
                </p>
              </div>
            </div>

            <div style={{ display: "flex", gap: "var(--space-3)", alignItems: "flex-start" }}>
              <span style={{ display: "inline-flex", width: "8px", height: "8px", borderRadius: "50%", background: "var(--color-action-primary)", marginTop: "6px", flexShrink: 0 }} />
              <div>
                <strong style={{ fontSize: "var(--fs-body-sm)", color: "var(--color-text)" }}>Phase II: Review &amp; Product Owner Approval</strong>
                <p style={{ margin: "2px 0 0", fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>
                  Validate line items, apply promotional or special discounts, and obtain PO sign-off.
                </p>
              </div>
            </div>

            <div style={{ display: "flex", gap: "var(--space-3)", alignItems: "flex-start" }}>
              <span style={{ display: "inline-flex", width: "8px", height: "8px", borderRadius: "50%", background: "var(--color-action-primary)", marginTop: "6px", flexShrink: 0 }} />
              <div>
                <strong style={{ fontSize: "var(--fs-body-sm)", color: "var(--color-text)" }}>Phase III: Dispatch &amp; Payment Collection</strong>
                <p style={{ margin: "2px 0 0", fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>
                  Dispatch approved statement to institution. Record incoming gateway payments or bank transfer UTRs.
                </p>
              </div>
            </div>
          </div>
        </Panel>

        <Panel title="Quick Links & Shortcuts" description="Direct operations for billing and commercial ops">
          <div style={{ display: "grid", gap: "var(--space-3)", padding: "var(--space-2) 0" }}>
            <a
              href="/platform/invoices"
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                padding: "var(--space-3)",
                borderRadius: "var(--radius-md)",
                background: "var(--color-bg-subtle)",
                textDecoration: "none",
                color: "var(--color-text)",
                border: "1px solid var(--color-border-divider)",
              }}
            >
              <div>
                <strong style={{ display: "block", fontSize: "var(--fs-body-sm)" }}>Platform Invoices Register</strong>
                <span style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>
                  View all drafts, sent statements, and collections
                </span>
              </div>
              <span style={{ color: "var(--color-action-primary)", fontWeight: "bold" }}>→</span>
            </a>

            <a
              href="/platform/plans"
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                padding: "var(--space-3)",
                borderRadius: "var(--radius-md)",
                background: "var(--color-bg-subtle)",
                textDecoration: "none",
                color: "var(--color-text)",
                border: "1px solid var(--color-border-divider)",
              }}
            >
              <div>
                <strong style={{ display: "block", fontSize: "var(--fs-body-sm)" }}>Subscription Plans &amp; Packaging</strong>
                <span style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>
                  Review active pricing tiers and quote calculations
                </span>
              </div>
              <span style={{ color: "var(--color-action-primary)", fontWeight: "bold" }}>→</span>
            </a>

            <a
              href="/platform/reports"
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                padding: "var(--space-3)",
                borderRadius: "var(--radius-md)",
                background: "var(--color-bg-subtle)",
                textDecoration: "none",
                color: "var(--color-text)",
                border: "1px solid var(--color-border-divider)",
              }}
            >
              <div>
                <strong style={{ display: "block", fontSize: "var(--fs-body-sm)" }}>Financial &amp; Revenue Reports</strong>
                <span style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>
                  Deep-dive revenue streams and 60-day renewal forecasts
                </span>
              </div>
              <span style={{ color: "var(--color-action-primary)", fontWeight: "bold" }}>→</span>
            </a>
          </div>
        </Panel>
      </div>
    </div>
  );
}
