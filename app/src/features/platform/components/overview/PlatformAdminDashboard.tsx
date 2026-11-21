import { useEffect, useState } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { platformTenantService, type TenantItem } from "../../api/platformTenantService";
import { platformOpsService, type SupportTicketItem } from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";
import { Notice, SkeletonText } from "../../../../design-system/components";
import { KpiSparkline, getDeterministicSeries } from "../../../../design-system/components/MetricCard";
import { Icon, type IconName } from "../../../../components/ui/Icon";

type BackupRow = { stale?: boolean };
type BackupResponse = { tenants?: BackupRow[] };

const ICON_BY_TITLE: Record<string, IconName> = {
  "Tenants pending activation": "clock",
  "Failed setups": "alert",
  "Open tickets": "users",
  "Failed jobs": "activity",
  "Backup alerts": "archive",
};

async function getAllTenants(): Promise<TenantItem[]> {
  const all: TenantItem[] = [];
  let page = 0;
  while (true) {
    const response = await platformTenantService.getTenants(page, 200);
    const rows = response.data.items ?? [];
    all.push(...rows);
    if (rows.length < 200) return all;
    page += 1;
  }
}

type MetricState = { status: "loading" | "ok" | "not-measured" | "error" | "denied"; value?: string | number; detail?: string };

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
            <p className={typeof metric.value === "number" ? "kpi__value" : "kpi__value kpi__value--text"}>{metric.value}</p>
            <span className="kpi__trend-badge kpi__trend-badge--neutral">Telemetry</span>
          </div>
          <KpiSparkline
            data={getDeterministicSeries(title, typeof metric.value === "number" ? metric.value : parseInt(String(metric.value), 10) || 5)}
            label={`${title} trend`}
          />
          <p className="kpi__context">Operational telemetry</p>
        </>
      )}
    </section>
  );
}

export function PlatformAdminDashboard() {
  const { can, canAny } = usePlatformAccess();
  const [metrics, setMetrics] = useState<Record<string, MetricState>>({
    pendingActivation: { status: "loading" }, failedSetups: { status: "loading" },
    openTickets: { status: "loading" }, failedJobs: { status: "not-measured" },
    backupAlerts: { status: "loading" },
  });
  useEffect(() => {
    let active = true;
    const set = (key: string, value: MetricState) => { if (active) setMetrics(old => ({ ...old, [key]: value })); };
    const load = async (key: string, permitted: boolean, task: () => Promise<MetricState>) => {
      if (!permitted) { set(key, { status: "denied" }); return; }
      try { set(key, await task()); }
      catch (error) { set(key, { status: "error", detail: error instanceof Error ? error.message : "Request failed" }); }
    };
    const tenantRead = canAny(["TENANT_CREATE", "TENANT_SETTINGS", "TENANT_ACTIVATE", "TENANT_SUSPEND_ARCHIVE", "INVOICE_MANAGE", "SUPPORT_TICKET_MANAGE"]);
    let tenantsPromise: Promise<TenantItem[]> | undefined;
    const loadTenants = () => (tenantsPromise ??= getAllTenants());
    const provisioningRead = canAny(["TENANT_CREATE", "TENANT_SETTINGS"]);
    void load("pendingActivation", tenantRead, async () => {
      const tenants = await loadTenants();
      return { status: "ok", value: tenants.filter(tenant => tenant.state === "TRIAL").length };
    });
    void load("failedSetups", provisioningRead, async () => {
      const tenants = await loadTenants();
      return { status: "ok", value: tenants.filter(tenant => tenant.state === "CREATED").length };
    });
    void load("openTickets", can("SUPPORT_TICKET_MANAGE"), async () => {
      const tickets: SupportTicketItem[] = await platformOpsService.listSupportTickets();
      return { status: "ok", value: tickets.filter(ticket => ticket.status !== "CLOSED" && ticket.status !== "RESOLVED").length };
    });
    void load("backupAlerts", can("BACKUP_STATUS_VIEW"), async () => {
      const response = await platformOpsService.getPlatformBackupStatus() as unknown as BackupResponse;
      const rows = response.tenants;
      if (!Array.isArray(rows) || rows.length === 0 || rows.some(row => typeof row.stale !== "boolean")) return { status: "not-measured" };
      return { status: "ok", value: rows.filter(row => row.stale === true).length };
    });
    return () => { active = false; };
  }, [can, canAny]);

  const tiles = [
    ["Tenants pending activation", metrics.pendingActivation],
    ["Failed setups", metrics.failedSetups],
    ["Open tickets", metrics.openTickets],
    ["Failed jobs", metrics.failedJobs],
    ["Backup alerts", metrics.backupAlerts],
  ] as const;

  return <div className="app-page">
    <PageHeader title="Platform Admin dashboard" description="Tenant onboarding, support queue and platform operations." />
    <div className="kpi-grid">{tiles.map(([title, metric]) => <MetricTile key={title} title={title} metric={metric} />)}</div>
  </div>;
}
