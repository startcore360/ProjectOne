import { useEffect, useState } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Spinner } from "../../../../components/ui";
import { Notice, Panel } from "../../../../design-system/components";
import { platformTenantService, type TenantItem } from "../../api/platformTenantService";
import { platformOpsService } from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";

type MetricState = { status: "loading" | "ok" | "not-measured" | "error" | "denied"; value?: string | number; detail?: string };
type BackupRow = { stale?: boolean };
type BackupResponse = { tenants?: BackupRow[] };
type Ticket = { escalatedAt?: string; status: string };
type UsageMetric = { metricCode: string; value: number | null; measured?: boolean };

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

export function ProductOwnerDashboard() {
  const { can, canAny } = usePlatformAccess();
  const [metrics, setMetrics] = useState<Record<string, MetricState>>({
    tenants: { status: "loading" }, revenue: { status: "loading" }, activeUsers: { status: "loading" },
    backups: { status: "loading" }, failedSetups: { status: "loading" }, escalations: { status: "loading" },
    pendingDp: { status: "loading" },
    errors: { status: "not-measured" }, failedJobs: { status: "not-measured" },
  });
  useEffect(() => {
    let active = true;
    const set = (key: string, value: MetricState) => { if (active) setMetrics(old => ({ ...old, [key]: value })); };
    const load = async (key: string, permitted: boolean, task: () => Promise<MetricState>) => {
      if (!permitted) { set(key, { status: "denied" }); return; }
      try { set(key, await task()); }
      catch (error) { set(key, { status: "error", detail: error instanceof Error ? error.message : "Request failed" }); }
    };
    const tenantPermission = canAny(["TENANT_CREATE", "TENANT_SETTINGS", "TENANT_ACTIVATE", "TENANT_SUSPEND_ARCHIVE", "INVOICE_MANAGE", "SUPPORT_TICKET_MANAGE"]);
    const setupPermission = canAny(["TENANT_CREATE", "TENANT_SETTINGS"]);
    void load("tenants", tenantPermission, async () => {
      const tenants = await getAllTenants();
      const counts = tenants.reduce<Record<string, number>>((all, tenant) => { all[tenant.state] = (all[tenant.state] || 0) + 1; return all; }, {});
      return { status: "ok", value: Object.entries(counts).map(([state, count]) => `${state}: ${count}`).join(" · ") || "0 tenants" };
    });
    void load("failedSetups", setupPermission, async () => {
      const tenants = await getAllTenants();
      const results = await Promise.all(tenants.map(async tenant => {
        const response = await platformTenantService.getProvisioning(tenant.tenantId);
        return response.data.overallStatus === "FAILED";
      }));
      return { status: "ok", value: results.filter(Boolean).length };
    });
    void load("revenue", can("PLATFORM_REPORTS_VIEW"), async () => {
      const report = await platformOpsService.getReport("REVENUE");
      const value = report.metrics?.totalCollectionsThisMonth;
      return value == null ? { status: "not-measured" } : { status: "ok", value: `${report.currency || "INR"} ${Number(value).toLocaleString("en-IN")}` };
    });
    void load("activeUsers", can("USAGE_COUNTS_VIEW"), async () => {
      const usage = await platformOpsService.getUsageCounts();
      const metric = usage.metrics?.find((item: UsageMetric) => item.metricCode === "ACTIVE_USERS") as UsageMetric | undefined;
      return metric?.measured === false || metric?.value == null
        ? { status: "not-measured" } : { status: "ok", value: metric.value };
    });
    void load("backups", can("BACKUP_STATUS_VIEW"), async () => {
      const response = await platformOpsService.getPlatformBackupStatus() as unknown as BackupResponse;
      const rows = response.tenants;
      if (!Array.isArray(rows) || rows.length === 0 || rows.some(row => typeof row.stale !== "boolean")) return { status: "not-measured" };
      return { status: "ok", value: rows.filter(row => row.stale === true).length };
    });
    void load("pendingDp", canAny(["TENANT_EXPORT_REQUEST", "TENANT_DELETION_REQUEST"]), async () => {
      const response = await platformOpsService.getPendingDataProtectionCount();
      return { status: "ok", value: response.count };
    });
    void load("escalations", can("SUPPORT_TICKET_MANAGE"), async () => {
      const tickets = await platformOpsService.listSupportTickets() as unknown as Ticket[];
      const count = tickets.filter(ticket => ticket.escalatedAt && ticket.status !== "CLOSED" && ticket.status !== "RESOLVED").length;
      return { status: "ok", value: count };
    });
    return () => { active = false; };
  }, [can, canAny]);
  const tiles = [
    ["Tenants by state", metrics.tenants], ["Revenue this month", metrics.revenue],
    ["Active users", metrics.activeUsers], ["Stale backups", metrics.backups],
    ["Failed setups", metrics.failedSetups], ["Open escalations", metrics.escalations],
    ["Pending DP approvals", metrics.pendingDp], ["5xx errors (24h)", metrics.errors],
    ["Failed jobs", metrics.failedJobs],
  ] as const;
  return <div className="app-page">
    <PageHeader title="Product Owner dashboard" description="Platform-wide operational and commercial health." />
    <div className="kpi-grid">{tiles.map(([title, metric]) => <MetricTile key={title} title={title} metric={metric} />)}</div>
  </div>;
}
