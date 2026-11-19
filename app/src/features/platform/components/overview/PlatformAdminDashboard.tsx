import { useEffect, useState } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Spinner } from "../../../../components/ui";
import { Notice, Panel } from "../../../../design-system/components";
import { platformTenantService, type TenantItem } from "../../api/platformTenantService";
import { platformOpsService, type SupportTicketItem } from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";

type MetricState = { status: "loading" | "ok" | "not-measured" | "error" | "denied"; value?: string | number; detail?: string };
type BackupRow = { stale?: boolean };
type BackupResponse = { tenants?: BackupRow[] };

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

function MetricTile({ title, metric }: { title: string; metric: MetricState }) {
  return <Panel title={title}>
    {metric.status === "loading" ? <Spinner label={`Loading ${title.toLowerCase()}...`} /> :
      metric.status === "error" ? <Notice tone="warning">{metric.detail || "Could not load this metric."}</Notice> :
      metric.status === "denied" ? <span>Not available for this role</span> :
      metric.status === "not-measured" ? <span>Not measured{metric.detail ? ` — ${metric.detail}` : ""}</span> :
      <strong className="platform-dashboard-value">{metric.value}</strong>}
  </Panel>;
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
