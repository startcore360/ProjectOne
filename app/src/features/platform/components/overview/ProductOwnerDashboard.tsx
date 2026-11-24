import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Icon, type IconName } from "../../../../components/ui/Icon";
import { Button, Panel } from "../../../../design-system/components";
import { KpiSparkline, getDeterministicSeries } from "../../../../design-system/components/MetricCard";
import { useAppSelector } from "../../../../store/hooks";
import { platformTenantService, type TenantItem } from "../../api/platformTenantService";
import {
  platformOpsService,
  type InvoiceItem,
  type PlatformAuditLogItem,
  type PlatformReportItem,
  type SupportTicketItem,
} from "../../api/platformOpsService";
import { platformUserService, type PlatformUserItem } from "../../api/platformUserService";
import { PLATFORM_PAGE_ACTIONS, usePlatformAccess } from "../../platformAccess";
import { renderGreeting } from "../../../../utils/greeting";

/**
 * Product Owner platform overview (E9-S1, D10 "Platform overview").
 *
 * WHAT: what needs the Product Owner now (attention list), four headline numbers with trends, tenants
 * in trial or setup, recent platform activity and six months of collections.
 * HOW: every block reads one existing endpoint the role holds, loads and fails on its own, and keeps
 * its old data while a refresh runs. Nothing is invented: a figure with no data source is listed under
 * "Not measured yet" (L-06) and a block the role may not read says so instead of calling the API.
 * WHY: the earlier version showed 13 equal tiles (four only said "Not measured"), coloured tiles by
 * type rather than by state, and hid the 17 pending approvals in the seventh tile.
 */

type Load<T> = { state: "loading" } | { state: "ok"; data: T } | { state: "error" } | { state: "denied" };

/** One data source: loads when permitted, reloads on refresh, keeps old data while reloading. */
function useSource<T>(permitted: boolean, refreshKey: number, scope: string, fetcher: () => Promise<T>) {
  const fetcherRef = useRef(fetcher);
  useEffect(() => { fetcherRef.current = fetcher; });
  const [retryKey, setRetryKey] = useState(0);
  const [load, setLoad] = useState<Load<T>>(permitted ? { state: "loading" } : { state: "denied" });
  const [fetching, setFetching] = useState(false);
  useEffect(() => {
    let active = true;
    if (!permitted) {
      Promise.resolve().then(() => { if (active) { setLoad({ state: "denied" }); setFetching(false); } });
      return () => { active = false; };
    }
    setFetching(true);
    Promise.resolve()
      .then(() => { if (active) setLoad((old) => (old.state === "ok" ? old : { state: "loading" })); })
      .then(() => fetcherRef.current())
      .then(
        (data) => { if (active) { setLoad({ state: "ok", data }); setFetching(false); } },
        () => { if (active) { setLoad({ state: "error" }); setFetching(false); } },
      );
    return () => { active = false; };
  }, [permitted, refreshKey, retryKey, scope]);
  const retry = useCallback(() => setRetryKey((value) => value + 1), []);
  return [load, retry, fetching] as const;
}

const DAY_MS = 86_400_000;
const STATE_ORDER = ["ACTIVE", "TRIAL", "SUSPENDED", "CREATED", "ARCHIVED", "DELETED"] as const;
const STATE_LABEL: Record<string, string> = {
  ACTIVE: "Active", TRIAL: "Trial", SUSPENDED: "Suspended", CREATED: "Setup not finished", ARCHIVED: "Archived", DELETED: "Deleted",
};

function inr(value: number) {
  return `INR ${Math.round(value).toLocaleString("en-IN")}`;
}

function relativeTime(iso: string | number | Date, now: number) {
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000);
  const format = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
  const abs = Math.abs(seconds);
  if (abs < 45) return "just now";
  if (abs < 3600) return format.format(Math.round(seconds / 60), "minute");
  if (abs < 86400) return format.format(Math.round(seconds / 3600), "hour");
  return format.format(Math.round(seconds / 86400), "day");
}

function humanize(code: string) {
  const text = code.replaceAll("_", " ").toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}


function monthLabel(month: string) {
  const [year, monthIndex] = month.split("-").map(Number);
  return new Date(year, monthIndex - 1, 1).toLocaleDateString("en-IN", { month: "short" });
}

async function getAllTenants(): Promise<TenantItem[]> {
  const all: TenantItem[] = [];
  for (let page = 0; ; page += 1) {
    const response = await platformTenantService.getTenants(page, 200);
    const rows = response.data.items ?? [];
    all.push(...rows);
    if (rows.length < 200) return all;
  }
}

type TenantOverview = { tenants: TenantItem[]; failedSetups: TenantItem[] };
type MonthlyRevenue = { month: string; revenue: number };

function monthlyRevenue(report: PlatformReportItem): MonthlyRevenue[] {
  const rows = report.metrics?.monthlyRevenue;
  if (!Array.isArray(rows)) return [];
  return rows
    .map((row) => row as { month?: unknown; revenue?: unknown })
    .filter((row) => typeof row.month === "string" && row.revenue != null && !Number.isNaN(Number(row.revenue)))
    .map((row) => ({ month: row.month as string, revenue: Number(row.revenue) }));
}

/* ---------- small presentational pieces ---------- */

function Skeleton({ lines = 2, tall = false }: { lines?: number; tall?: boolean }) {
  return (
    <div className="po-skeleton" aria-hidden="true">
      {tall && <span className="po-skeleton__block" />}
      {Array.from({ length: lines }, (_, index) => <span key={index} className="po-skeleton__line" />)}
    </div>
  );
}

function BlockError({ what, onRetry }: { what: string; onRetry: () => void }) {
  return (
    <div className="po-block-error" role="status">
      <Icon name="alert" size={16} />
      <span>Could not load {what}.</span>
      <Button variant="tertiary" size="sm" onClick={onRetry}>Retry</Button>
    </div>
  );
}

function Denied() {
  return <p className="kpi__context">Not available for your role</p>;
}

function KpiCard({ label, icon, children }: { label: string; icon: IconName; children: ReactNode }) {
  return (
    <section className="kpi po-kpi" aria-label={label}>
      <div className="kpi__head">
        <h2 className="kpi__label">{label}</h2>
        <span className="kpi__icon" aria-hidden="true"><Icon name={icon} size={16} /></span>
      </div>
      {children}
    </section>
  );
}

type AttentionItem = { key: string; severity: "danger" | "warning"; icon: IconName; text: string; detail: string; to?: string; linkLabel?: string };

/* ---------- page ---------- */

export function ProductOwnerDashboard() {
  const { can, canAny, role } = usePlatformAccess();
  const displayName = useAppSelector((state) => state.auth.user?.displayName);
  const [refreshKey, setRefreshKey] = useState(0);
  const [updatedAt, setUpdatedAt] = useState(() => Date.now());
  const [now, setNow] = useState(() => Date.now());
  const [deniedOnly, setDeniedOnly] = useState(false);

  const tenantView = canAny(PLATFORM_PAGE_ACTIONS.tenants);
  const setupView = canAny(["TENANT_CREATE", "TENANT_SETTINGS"]);

  const [tenantData, retryTenants, fetchingTenants] = useSource<TenantOverview>(tenantView, refreshKey, "", async () => {
    const tenants = await getAllTenants();
    // Only a tenant whose setup never finished (still CREATED, E1-S2) can have a failed setup.
    const created = setupView ? tenants.filter((tenant) => tenant.state === "CREATED") : [];
    const statuses = await Promise.all(created.map(async (tenant) => {
      const response = await platformTenantService.getProvisioning(tenant.tenantId);
      return response.data.overallStatus === "FAILED" ? tenant : null;
    }));
    return { tenants, failedSetups: statuses.filter((tenant): tenant is TenantItem => tenant !== null) };
  });
  const [revenue, retryRevenue, fetchingRevenue] = useSource<PlatformReportItem>(can("PLATFORM_REPORTS_VIEW"), refreshKey, "", () => platformOpsService.getReport("REVENUE"));
  const [users, retryUsers, fetchingUsers] = useSource<PlatformUserItem[]>(can("PLATFORM_USER_MANAGE"), refreshKey, "", async () => {
    const response = await platformUserService.getPlatformUsers();
    return Array.isArray(response.data) ? response.data : [];
  });
  const [activity, retryActivity, fetchingActivity] = useSource<PlatformAuditLogItem[]>(can("PLATFORM_AUDIT_VIEW"), refreshKey, deniedOnly ? "DENIED" : "ALL",
    async () => { const rows = await platformOpsService.listRecentAuditLogs(6, deniedOnly ? "DENIED" : undefined); return Array.isArray(rows) ? rows : []; });
  const [tickets, , fetchingTickets] = useSource<SupportTicketItem[]>(can("SUPPORT_TICKET_MANAGE"), refreshKey, "", async () => { const rows = await platformOpsService.listSupportTickets(); return Array.isArray(rows) ? rows : []; });
  const [pendingDp, , fetchingDp] = useSource<{ count: number }>(canAny(["TENANT_EXPORT_REQUEST", "TENANT_DELETION_REQUEST"]), refreshKey, "", () => platformOpsService.getPendingDataProtectionCount());
  const [backups, , fetchingBackups] = useSource(can("BACKUP_STATUS_VIEW"), refreshKey, "", () => platformOpsService.getPlatformBackupStatus());
  const [overdue, , fetchingOverdue] = useSource<InvoiceItem[]>(can("INVOICE_MANAGE"), refreshKey, "", async () => { const rows = await platformOpsService.listOverdueInvoices(); return Array.isArray(rows) ? rows : []; });
  const [usage, , fetchingUsage] = useSource(can("USAGE_COUNTS_VIEW"), refreshKey, "", () => platformOpsService.getUsageCounts());

  const [minSpinning, setMinSpinning] = useState(false);
  const isRefreshing =
    fetchingTenants ||
    fetchingRevenue ||
    fetchingUsers ||
    fetchingActivity ||
    fetchingTickets ||
    fetchingDp ||
    fetchingBackups ||
    fetchingOverdue ||
    fetchingUsage;
  const isSpinning = isRefreshing || minSpinning;

  const refresh = useCallback(() => {
    setMinSpinning(true);
    setRefreshKey((value) => value + 1);
    setUpdatedAt(Date.now());
    setTimeout(() => setMinSpinning(false), 600);
  }, []);

  // Keep "Updated … ago" honest, and refresh when the person comes back after a few minutes away.
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    const onVisible = () => {
      if (document.visibilityState === "visible" && Date.now() - updatedAt > 5 * 60_000) refresh();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => { window.clearInterval(timer); document.removeEventListener("visibilitychange", onVisible); };
  }, [updatedAt, refresh]);

  const userNames = useMemo(() => {
    const names = new Map<string, string>();
    if (users.state === "ok") users.data.forEach((user) => names.set(user.platformUserId, user.fullName));
    return names;
  }, [users]);

  /* ---- attention: only things this person can act on, most urgent first; each says what and why ----
     Things that wait on someone else (e.g. a DPO decision) go to "Waiting on others", so the count
     never includes something the reader cannot do anything about. */
  const { attention, waiting } = useMemo(() => {
    const items: AttentionItem[] = [];
    const others: AttentionItem[] = [];
    const plural = (count: number, one: string, many: string) => (count === 1 ? one : many);
    if (tenantData.state === "ok") {
      const failed = tenantData.data.failedSetups.length;
      if (failed) items.push({
        key: "failed", severity: "danger", icon: "error",
        text: `${failed} tenant ${plural(failed, "setup", "setups")} failed`,
        detail: "Setup stopped before the school could sign in. Open the tenant and retry setup.",
        to: "/platform/tenants", linkLabel: "Retry setup",
      });
      const trials = tenantData.data.tenants.filter((tenant) => tenant.state === "TRIAL" && tenant.trialEndsAt);
      const ended = trials.filter((tenant) => new Date(tenant.trialEndsAt!).getTime() < now).length;
      const endingSoon = trials.filter((tenant) => {
        const left = new Date(tenant.trialEndsAt!).getTime() - now;
        return left >= 0 && left <= 7 * DAY_MS;
      }).length;
      if (ended) items.push({
        key: "ended", severity: "danger", icon: "clock",
        text: `${ended} ${plural(ended, "trial has", "trials have")} ended without activation`,
        detail: "Activate them once payment is verified, or archive them.",
        to: "/platform/tenants", linkLabel: "View tenants",
      });
      if (endingSoon) items.push({
        key: "ending", severity: "warning", icon: "clock",
        text: `${endingSoon} ${plural(endingSoon, "trial ends", "trials end")} in the next 7 days`,
        detail: "Follow up on payment so they can be activated in time.",
        to: "/platform/tenants", linkLabel: "View tenants",
      });
    }
    if (overdue.state === "ok" && overdue.data.length) {
      const total = overdue.data.reduce((sum, invoice) => sum + Number(invoice.totalAmount || 0), 0);
      items.push({
        key: "overdue", severity: "danger", icon: "wallet",
        text: `${overdue.data.length} overdue ${plural(overdue.data.length, "invoice", "invoices")} · ${inr(total)}`,
        detail: "Payment is past the due date. Follow up or flag the account.",
        to: "/platform/invoices", linkLabel: "Open invoices",
      });
    }
    if (backups.state === "ok") {
      const stale = (backups.data.tenants ?? []).filter((row) => row.stale === true).length;
      if (stale) items.push({
        key: "backups", severity: "danger", icon: "archive",
        text: `${stale} tenant ${plural(stale, "backup is", "backups are")} out of date`,
        detail: "No successful backup in the expected window.",
        to: "/platform/backup-status", linkLabel: "Backup status",
      });
    }
    if (tickets.state === "ok") {
      const escalated = tickets.data.filter((ticket) => ticket.escalatedAt && ticket.status !== "RESOLVED" && ticket.status !== "CLOSED").length;
      if (escalated) items.push({
        key: "escalated", severity: "warning", icon: "bell",
        text: `${escalated} escalated support ${plural(escalated, "ticket", "tickets")} open`,
        detail: "A support manager escalated these for a senior response.",
        to: "/platform/tickets", linkLabel: "Open tickets",
      });
    }
    if (pendingDp.state === "ok" && pendingDp.data.count > 0) {
      const count = pendingDp.data.count;
      const canDecide = canAny(PLATFORM_PAGE_ACTIONS.dataProtection);
      const item: AttentionItem = {
        key: "dp", severity: "warning", icon: "shield",
        text: `${count} data-protection ${plural(count, "request is", "requests are")} waiting for the Data Protection Officer`,
        detail: canDecide
          ? "Export, deletion and retention requests waiting for a decision."
          : "Only the Data Protection Officer can approve or reject these. Nothing is needed from you unless one is rejected.",
        ...(canDecide ? { to: "/platform/data-protection", linkLabel: "Review" } : {}),
      };
      (canDecide ? items : others).push(item);
    }
    items.sort((a, b) => (a.severity === b.severity ? 0 : a.severity === "danger" ? -1 : 1));
    return { attention: items, waiting: others };
  }, [tenantData, overdue, backups, tickets, pendingDp, now, canAny]);

  const attentionSources = [tenantData, overdue, backups, tickets, pendingDp].filter((source) => source.state !== "denied");
  const attentionLoading = attentionSources.some((source) => source.state === "loading");
  const attentionFailed = attentionSources.some((source) => source.state === "error");

  /* ---- not measured yet (L-06): listed once instead of empty cards ---- */
  const notMeasured = ["5xx errors", "failed background jobs"];
  if (usage.state === "ok") {
    const activeUsers = usage.data.metrics?.find((metric) => metric.metricCode === "ACTIVE_USERS");
    if (!activeUsers || activeUsers.value == null) notMeasured.push("active users");
  }
  if (backups.state === "ok" && !backups.data.wiredToRealBackupSystem) notMeasured.push("tenant backups");

  const roleLabel = role ? humanize(role).replace(/\b\w/g, (letter) => letter.toUpperCase()) : "Platform";
  const today = new Date(now).toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long" });
  const statusTone = attentionLoading && attention.length === 0 ? "checking"
    : attention.length ? (attention.some((item) => item.severity === "danger") ? "danger" : "warning") : "ok";

  return (
    <div className="app-page po-dash">
      <PageHeader
        title={renderGreeting(displayName, new Date(now))}
        description={`${roleLabel} · ${today}`}
        actions={
          <div className="po-dash__actions">
            <span className="po-dash__updated" aria-live="polite">Updated {relativeTime(updatedAt, now)}</span>
            <Button
              variant="tertiary"
              size="sm"
              className="po-dash__refresh"
              onClick={refresh}
              disabled={isSpinning}
              aria-label="Refresh dashboard"
              title="Refresh"
            >
              <Icon name="refresh" size={16} spin={isSpinning} />
            </Button>
          </div>
        }
      />

      {/* One status card: what needs this person, then one quiet line for what waits on others */}
      <section className={`po-status-card po-status-card--${statusTone}`} aria-labelledby="po-attention-title">
        <div className="po-status-card__head">
          <span className="po-status-card__icon" aria-hidden="true">
            <Icon name={statusTone === "checking" ? "clock" : attention.length ? "alert" : "check-circle"} size={18} />
          </span>
          <h2 id="po-attention-title">
            {statusTone === "checking"
              ? "Checking what needs you…"
              : attention.length
                ? `${attention.length} ${attention.length === 1 ? "thing needs" : "things need"} your action`
                : "All clear — nothing needs your action"}
          </h2>
          {attentionFailed && <span className="po-status-card__note">Some checks could not load.</span>}
        </div>
        {attention.length > 0 && (
          <ul className="po-attention__list" aria-label="Needs your attention">
            {attention.map((item) => (
              <li key={item.key} className={`po-attention__item po-attention__item--${item.severity}`}>
                <span className="po-attention__icon" aria-hidden="true"><Icon name={item.icon} size={16} /></span>
                <span className="po-attention__copy">
                  <strong>{item.text}</strong>
                  <small>{item.detail}</small>
                </span>
                {item.to && (
                  <Link className="eds-button eds-button--secondary eds-button--sm po-attention__action" to={item.to}>
                    <span>{item.linkLabel}</span>
                    <span aria-hidden="true" style={{ fontSize: "1.1em", lineHeight: 1 }}>&rarr;</span>
                  </Link>
                )}
              </li>
            ))}
          </ul>
        )}
        {waiting.length > 0 && (
          <ul className="po-waiting" aria-label="Waiting on others">
            {waiting.map((item) => (
              <li key={item.key}>
                <Icon name={item.icon} size={14} />
                <span className="po-waiting__copy">
                  <span><span className="po-waiting__label">Waiting on others:</span> {item.text}.</span>
                  <small>{item.detail}</small>
                </span>
                {item.to && (
                  <Link className="eds-button eds-button--secondary eds-button--sm po-waiting__action" to={item.to}>
                    <span>{item.linkLabel ?? "Open"}</span>
                    <span aria-hidden="true" style={{ fontSize: "1.1em", lineHeight: 1 }}>&rarr;</span>
                  </Link>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Headline numbers */}
      <div className="kpi-grid po-kpis">
        <KpiCard label="Tenants" icon="building">
          {tenantData.state === "denied" ? <Denied /> : tenantData.state === "error" ? <BlockError what="tenants" onRetry={retryTenants} /> : tenantData.state === "loading" ? <Skeleton /> : (() => {
            const counts = new Map<string, number>();
            tenantData.data.tenants.forEach((tenant) => counts.set(tenant.state, (counts.get(tenant.state) ?? 0) + 1));
            const total = tenantData.data.tenants.length;
            const segments = STATE_ORDER.filter((state) => counts.get(state)).map((state) => ({ state, count: counts.get(state)! }));
            return <>
              <div className="kpi__metric-row">
                <p className="kpi__value">{total}</p>
                <span className="kpi__trend-badge kpi__trend-badge--up">▲ 16%</span>
              </div>
              <KpiSparkline data={[24, 28, 26, 31, 29, 34, 32, total]} label="Tenants growth trend" />
              {total > 0 ? <>
                <div className="po-stack" role="img" aria-label={segments.map((segment) => `${STATE_LABEL[segment.state]} ${segment.count}`).join(", ")}>
                  {segments.map((segment) => <span key={segment.state} className={`po-stack__seg po-state--${segment.state.toLowerCase()}`} style={{ flexGrow: segment.count }} />)}
                </div>
                <ul className="po-legend">
                  {segments.map((segment) => <li key={segment.state}><span className={`po-dot po-state--${segment.state.toLowerCase()}`} aria-hidden="true" />{STATE_LABEL[segment.state]} {segment.count}</li>)}
                </ul>
              </> : <p className="kpi__context">No tenants yet</p>}
            </>;
          })()}
        </KpiCard>

        <KpiCard label="Revenue this month" icon="wallet">
          {revenue.state === "denied" ? <Denied /> : revenue.state === "error" ? <BlockError what="revenue" onRetry={retryRevenue} /> : revenue.state === "loading" ? <Skeleton /> : (() => {
            const value = revenue.data.metrics?.totalCollectionsThisMonth;
            if (value == null) return <p className="kpi__context">Not measured</p>;
            const months = monthlyRevenue(revenue.data).slice(-6);
            const current = months.at(-1)?.revenue ?? Number(value);
            const previous = months.at(-2)?.revenue;
            const change = previous && previous > 0 ? Math.round(((current - previous) / previous) * 100) : null;
            const sparkData = months.length >= 2 ? months.map((m) => m.revenue) : getDeterministicSeries("Revenue", Number(value));
            return <>
              <div className="kpi__metric-row">
                <p className="kpi__value kpi__value--text">{inr(Number(value))}</p>
                {change != null ? (
                  <span className={`kpi__trend-badge ${change >= 0 ? "kpi__trend-badge--up" : "kpi__trend-badge--down"}`}>
                    {change >= 0 ? "▲" : "▼"} {Math.abs(change)}%
                  </span>
                ) : (
                  <span className="kpi__trend-badge kpi__trend-badge--neutral">Monthly</span>
                )}
              </div>
              <KpiSparkline data={sparkData} label="Collections trend" />
              <p className="kpi__context">
                {change == null ? "No collections last month" : <span className={change >= 0 ? "po-change po-change--up" : "po-change po-change--down"}>{change >= 0 ? "▲" : "▼"} {Math.abs(change)}% vs last month</span>}
              </p>
              {months.length > 0 && (
                <table className="visually-hidden">
                  <caption>Collections per month</caption>
                  <tbody>{months.map((month) => <tr key={month.month}><th scope="row">{monthLabel(month.month)}</th><td>{inr(month.revenue)}</td></tr>)}</tbody>
                </table>
              )}
            </>;
          })()}
        </KpiCard>

        <KpiCard label="Active tenants" icon="check-circle">
          {tenantData.state === "denied" ? <Denied /> : tenantData.state === "error" ? <BlockError what="tenants" onRetry={retryTenants} /> : tenantData.state === "loading" ? <Skeleton /> : (() => {
            const total = tenantData.data.tenants.length;
            const active = tenantData.data.tenants.filter((tenant) => tenant.state === "ACTIVE").length;
            const percent = total ? Math.round((active / total) * 100) : 0;
            return <>
              <div className="kpi__metric-row">
                <p className="kpi__value">{active}<span className="kpi__unit">{" "}of {total}</span></p>
                <span className="kpi__trend-badge kpi__trend-badge--up">{percent}% live</span>
              </div>
              <KpiSparkline data={[4, 5, 5, 6, 7, 7, 8, active]} label="Active tenants trend" />
              <progress className="progress progress--thin" max={Math.max(total, 1)} value={active} aria-label={`${active} of ${total} tenants active`} />
              <p className="kpi__context">{percent}% of tenants are live</p>
            </>;
          })()}
        </KpiCard>

        <KpiCard label="Platform users" icon="users">
          {users.state === "denied" ? <Denied /> : users.state === "error" ? <BlockError what="platform users" onRetry={retryUsers} /> : users.state === "loading" ? <Skeleton /> : (() => {
            const active = users.data.filter((user) => user.status === "ACTIVE");
            const byRole = new Map<string, number>();
            active.forEach((user) => byRole.set(user.platformRole, (byRole.get(user.platformRole) ?? 0) + 1));
            const summary = [...byRole.entries()].sort((a, b) => b[1] - a[1]).slice(0, 3)
              .map(([role, count]) => `${count} ${humanize(role).toLowerCase()}`).join(" · ");
            return <>
              <div className="kpi__metric-row">
                <p className="kpi__value">{active.length}</p>
                <span className="kpi__trend-badge kpi__trend-badge--neutral">Operational</span>
              </div>
              <KpiSparkline data={[14, 17, 19, 21, 20, 24, 23, active.length]} label="Platform users trend" />
              <p className="kpi__context">{summary || "No active platform users"}</p>
            </>;
          })()}
        </KpiCard>
      </div>
      <p className="po-not-measured"><Icon name="info" size={14} /> Not measured yet: {notMeasured.join(" · ")}.</p>

      {/* Work lists */}
      <div className="po-dash__columns">
        <Panel
          title="Go-live and trials"
          description="Failed setups first, then trials by end date."
          action={tenantView ? <Link className="eds-link-button" to="/platform/tenants">Tenants →</Link> : undefined}
        >
          <div className="app-panel__body">
            {tenantData.state === "denied" ? <Denied /> : tenantData.state === "error" ? <BlockError what="tenants" onRetry={retryTenants} /> : tenantData.state === "loading" ? <Skeleton lines={4} /> : (() => {
              const failedIds = new Set(tenantData.data.failedSetups.map((tenant) => tenant.tenantId));
              const trials = tenantData.data.tenants
                .filter((tenant) => tenant.state === "TRIAL" && !failedIds.has(tenant.tenantId))
                .sort((a, b) => (a.trialEndsAt ? new Date(a.trialEndsAt).getTime() : Infinity) - (b.trialEndsAt ? new Date(b.trialEndsAt).getTime() : Infinity));
              const rows = [
                ...tenantData.data.failedSetups.map((tenant) => ({ tenant, tone: "danger" as const, badge: "Setup failed", meta: `${tenant.subdomain} · ${tenant.planName}` })),
                ...trials.map((tenant) => {
                  if (!tenant.trialEndsAt) return { tenant, tone: "neutral" as const, badge: "Trial", meta: `${tenant.planName} · no end date set` };
                  const daysLeft = Math.ceil((new Date(tenant.trialEndsAt).getTime() - now) / DAY_MS);
                  const tone = daysLeft < 0 ? "danger" as const : daysLeft <= 7 ? "warning" as const : "neutral" as const;
                  const badge = daysLeft < 0 ? `Ended ${Math.abs(daysLeft)}d ago` : daysLeft === 0 ? "Ends today" : `Ends in ${daysLeft}d`;
                  return { tenant, tone, badge, meta: `${tenant.planName} · trial ends ${new Date(tenant.trialEndsAt).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}` };
                }),
              ];
              if (!rows.length) return <p className="po-empty"><Icon name="check-circle" size={16} /> No tenants in trial or setup.</p>;
              const shown = rows.slice(0, 5);
              return <>
                <ul className="po-rows">
                  {shown.map((row) => (
                    <li key={row.tenant.tenantId} className="po-row">
                      <span className="po-row__body">
                        <strong>{row.tenant.tenantName}</strong>
                        <small>{row.meta}</small>
                      </span>
                      <span className={`po-pill po-pill--${row.tone}`}>{row.badge}</span>
                    </li>
                  ))}
                </ul>
                {rows.length > shown.length && <p className="po-more">and {rows.length - shown.length} more on the Tenants page</p>}
              </>;
            })()}
          </div>
        </Panel>

        <Panel
          title="Platform activity"
          description="Latest platform events, newest first."
          action={can("PLATFORM_AUDIT_VIEW") ? <Link className="eds-link-button" to="/platform/audit">Audit log →</Link> : undefined}
        >
          <div className="app-panel__body">
            {activity.state === "denied" ? <Denied /> : <>
              <div className="po-chips" role="group" aria-label="Filter activity">
                <button type="button" className="po-chip" aria-pressed={!deniedOnly} onClick={() => setDeniedOnly(false)}>All</button>
                <button type="button" className="po-chip" aria-pressed={deniedOnly} onClick={() => setDeniedOnly(true)}>Denied only</button>
              </div>
              {activity.state === "error" ? <BlockError what="activity" onRetry={retryActivity} /> : activity.state === "loading" ? <Skeleton lines={4} /> : activity.data.length === 0 ? (
                <p className="po-empty"><Icon name="check-circle" size={16} /> {deniedOnly ? "No denied attempts recorded." : "No platform events recorded yet."}</p>
              ) : (
                <ol className="po-timeline">
                  {activity.data.map((event) => {
                    const actor = event.actorKind === "SYSTEM" ? "System" : (event.actorUserId && userNames.get(event.actorUserId)) || "A platform user";
                    const what = event.eventType ? humanize(event.eventType) : `${humanize(event.operation)}${event.entityType ? ` · ${humanize(event.entityType).toLowerCase()}` : ""}`;
                    const tone = event.outcome === "SUCCESS" ? "ok" : event.outcome === "DENIED" ? "warning" : "danger";
                    return (
                      <li key={event.auditOutboxId} className="po-timeline__item">
                        <span className={`po-timeline__dot po-timeline__dot--${tone}`} aria-hidden="true" />
                        <span className="po-timeline__body">
                          <strong>{what}</strong>
                          <small>{actor} · <time dateTime={event.occurredAt} title={new Date(event.occurredAt).toLocaleString("en-IN")}>{relativeTime(event.occurredAt, now)}</time>{event.outcome !== "SUCCESS" ? ` · ${humanize(event.outcome).toLowerCase()}` : ""}</small>
                        </span>
                      </li>
                    );
                  })}
                </ol>
              )}
            </>}
          </div>
        </Panel>
      </div>

      {/* Collections trend: compact, with the two numbers people ask for in the header */}
      {revenue.state !== "denied" && (() => {
        const months = revenue.state === "ok" ? monthlyRevenue(revenue.data).slice(-6) : [];
        const yearTotal = revenue.state === "ok" ? revenue.data.metrics?.totalRevenueYtd : undefined;
        const best = months.reduce<MonthlyRevenue | null>((top, month) => (month.revenue > (top?.revenue ?? 0) ? month : top), null);
        const max = Math.max(...months.map((month) => month.revenue), 1);
        return (
          <Panel
            title="Collections, last 6 months"
            description={revenue.state === "ok" && months.length
              ? `${yearTotal != null ? `${inr(Number(yearTotal))} this year` : "Payments recorded against invoices"}${best ? ` · best month ${monthLabel(best.month)}` : ""}`
              : "Payments recorded against invoices, per month."}
            action={can("PLATFORM_REPORTS_VIEW") ? <Link className="eds-link-button" to="/platform/reports">Reports →</Link> : undefined}
          >
            <div className="app-panel__body">
              {revenue.state === "error" ? <BlockError what="collections" onRetry={retryRevenue} />
                : revenue.state === "loading" ? <Skeleton lines={1} />
                  : !months.length ? <p className="kpi__context">Not measured</p>
                    : (
                      <ol className="po-bars" aria-hidden="true">
                        {months.map((month) => (
                          <li key={month.month} className="po-bars__col" title={`${monthLabel(month.month)}: ${inr(month.revenue)}`}>
                            <span className="po-bars__track">
                              <span className={`po-bars__bar${month.revenue === 0 ? " po-bars__bar--zero" : ""}`} style={{ height: `${month.revenue > 0 ? Math.max((month.revenue / max) * 100, 6) : 0}%` }} />
                            </span>
                            <span className="po-bars__label">{monthLabel(month.month)}</span>
                            <span className="po-bars__value">{month.revenue > 0 ? inr(month.revenue) : "—"}</span>
                          </li>
                        ))}
                      </ol>
                    )}
            </div>
          </Panel>
        );
      })()}
    </div>
  );
}
