import { useEffect, useState } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Spinner } from "../../../../components/ui";
import { Notice, Panel } from "../../../../design-system/components";
import { platformOpsService, type SupportTicketItem } from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";

type MetricState = { status: "loading" | "ok" | "error" | "denied"; value?: string | number; detail?: string };

function MetricTile({ title, metric }: { title: string; metric: MetricState }) {
  return <Panel title={title}>
    {metric.status === "loading" ? <Spinner label={`Loading ${title.toLowerCase()}...`} /> :
      metric.status === "error" ? <Notice tone="warning">{metric.detail || "Could not load this metric."}</Notice> :
      metric.status === "denied" ? <span>Not available for this role</span> :
      <strong className="platform-dashboard-value">{metric.value}</strong>}
  </Panel>;
}

const OPEN_STATUSES = new Set(["NEW", "TRIAGED", "ASSIGNED", "ESCALATED"]);
const PRIORITY_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];

function summarizeOpenByPriority(tickets: SupportTicketItem[]): string {
  const counts = new Map<string, number>();
  for (const ticket of tickets) {
    if (OPEN_STATUSES.has(ticket.status)) {
      const priority = (ticket.priority || "UNSPECIFIED").toUpperCase();
      counts.set(priority, (counts.get(priority) || 0) + 1);
    }
  }
  const priorities = [...new Set([...PRIORITY_ORDER, ...counts.keys()])].filter(priority => counts.has(priority));
  return priorities.length ? priorities.map(priority => `${priority}: ${counts.get(priority)}`).join(" · ") : "0";
}

export function SupportManagerDashboard() {
  const { can } = usePlatformAccess();
  const [metrics, setMetrics] = useState<Record<string, MetricState>>({
    priority: { status: "loading" },
    escalated: { status: "loading" },
    sessions: { status: "loading" },
  });

  useEffect(() => {
    let active = true;
    const set = (key: string, value: MetricState) => { if (active) setMetrics(old => ({ ...old, [key]: value })); };
    const load = async (key: string, permitted: boolean, task: () => Promise<MetricState>) => {
      if (!permitted) { set(key, { status: "denied" }); return; }
      try { set(key, await task()); }
      catch (error) { set(key, { status: "error", detail: error instanceof Error ? error.message : "Request failed" }); }
    };

    let ticketsPromise: Promise<SupportTicketItem[]> | undefined;
    const tickets = () => (ticketsPromise ??= platformOpsService.listSupportTickets());
    void load("priority", can("SUPPORT_TICKET_MANAGE"), async () => {
      const rows = await tickets();
      return { status: "ok", value: summarizeOpenByPriority(rows) };
    });
    void load("escalated", can("SUPPORT_TICKET_MANAGE"), async () => {
      const rows = await tickets();
      return { status: "ok", value: rows.filter(ticket =>
        Boolean(ticket.escalatedAt) && OPEN_STATUSES.has(ticket.status)
      ).length };
    });
    void load("sessions", can("SUPPORT_ACCESS_USE"), async () => {
      const response = await platformOpsService.getActiveSupportSessionCount();
      return { status: "ok", value: response.count };
    });

    return () => { active = false; };
  }, [can]);

  return <div className="app-page">
    <PageHeader title="Support Manager dashboard" description="Open ticket priorities, escalations and active support sessions." />
    <div className="kpi-grid">
      <MetricTile title="Open tickets by priority" metric={metrics.priority} />
      <MetricTile title="Escalated tickets" metric={metrics.escalated} />
      <MetricTile title="Active support sessions" metric={metrics.sessions} />
    </div>
  </div>;
}
