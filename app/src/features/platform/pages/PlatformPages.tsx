import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Kicker, TextField } from "../../../design-system/components";
import { Badge, Spinner } from "../../../components/ui";
import { PageHeader } from "../../../components/layout/PageHeader";
import api from "../../../services/axios";
import { Icon } from "../../../components/ui/Icon";

type Tenant = { id: string; name: string; type: string; plan: string; status: "Active" | "Trial" | "Suspended"; gates: number; updated: string };
type PlatformUser = { id: string; name: string; email: string; role: string; status: "Active" | "Invited" | "Suspended"; lastActive: string };
type AuditEvent = { id: string; when: string; actor: string; action: string; target: string; outcome: "Success" | "Denied" };

const initialTenants: Tenant[] = [
  { id: "TEN-0001", name: "Greenwood Public School", type: "School", plan: "Premium", status: "Active", gates: 0, updated: "Sample register" },
  { id: "TEN-0002", name: "Northbridge University", type: "University", plan: "Enterprise", status: "Active", gates: 0, updated: "Sample register" },
  { id: "TEN-0003", name: "Riverbank College", type: "College", plan: "Standard", status: "Trial", gates: 1, updated: "Sample register" },
  { id: "TEN-0004", name: "Hillside School", type: "School", plan: "Standard", status: "Suspended", gates: 0, updated: "Sample register" },
];
const initialUsers: PlatformUser[] = [
  { id: "PLT-0001", name: "Aarav Mehta", email: "Not provided in prototype", role: "Platform Admin", status: "Active", lastActive: "Not recorded" },
];
const initialAudit: AuditEvent[] = [];

function Metric({ label, value, note, mark }: { label: string; value: string | number; note: string; mark: "book" | "check-circle" | "clock" | "users" | "wallet" }) {
  return <article className="platform-metric"><span className="platform-metric__mark" aria-hidden="true"><Icon name={mark} size={19}/></span><p>{label}</p><strong>{value}</strong><small>{note}</small></article>;
}
function Panel({ title, description, action, children }: { title: string; description?: string; action?: ReactNode; children: ReactNode }) {
  return <section className="platform-panel"><header className="platform-panel__header"><div><h2>{title}</h2>{description && <p>{description}</p>}</div>{action}</header>{children}</section>;
}
function Status({ value }: { value: string }) {
  const tone = value === "Active" || value === "Success" || value === "Allowed" ? "success" : value === "Trial" || value === "Invited" ? "warning" : value === "Denied" || value === "Suspended" || value === "Disallowed" ? "danger" : "neutral";
  return <Badge tone={tone}>{value}</Badge>;
}
function DataTable({ headers, children, empty, emptyMessage }: { headers: string[]; children: ReactNode; empty?: boolean; emptyMessage?: string }) {
  return <div className="platform-table-wrap"><table className="platform-table"><thead><tr>{headers.map((h) => <th key={h} scope="col">{h}</th>)}</tr></thead><tbody>{empty ? <tr><td colSpan={headers.length} className="platform-empty-cell">{emptyMessage ?? "No matching records."}</td></tr> : children}</tbody></table></div>;
}
function SearchBox({ value, onChange, placeholder = "Search records" }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return <label className="platform-search"><span aria-hidden="true"><Icon name="search" size={17}/></span><span className="visually-hidden">Search</span><input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} /></label>;
}
function SampleNote() {
  return <p className="platform-sample-note"><span aria-hidden="true"><Icon name="shield" size={15}/></span> Sample interface data for layout review. Live records and actions are not connected yet.</p>;
}
// Reuses the existing .platform-inline-notice box (info-toned by default in components.css) for
// every honest caveat banner on this page: "not connected yet", "not available", and fetch
// errors. The "danger" tone overrides only the color tokens inline (still design-system tokens,
// no hardcoded hex) rather than adding a new CSS class, since this file does not own components.css.
function Notice({ tone = "info", children }: { tone?: "info" | "danger"; children: ReactNode }) {
  const style = tone === "danger"
    ? { background: "var(--color-status-danger-bg)", borderColor: "var(--color-status-danger-border)", color: "var(--color-status-danger-fg)" }
    : undefined;
  return <div className="platform-inline-notice" role={tone === "danger" ? "alert" : "status"} style={style}>{children}</div>;
}
function formatTimestamp(value: string | null): string {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}
function titleCaseOutcome(outcome: string): string {
  const lower = outcome.toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}
function readErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === "object" && "response" in err) {
    const response = (err as { response?: { data?: { error?: string } } }).response;
    if (response?.data?.error) return response.data.error;
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}

export function PlatformOverviewPage() {
  const navigate = useNavigate();
  const [tenants] = useState(initialTenants);
  const [users] = useState(initialUsers);
  const openGates = tenants.reduce((sum, tenant) => sum + tenant.gates, 0);
  return <div className="platform-page">
    <PageHeader title="Platform overview" description="Tenant lifecycle, platform access and service health at a glance." actions={<Button variant="secondary" onClick={() => navigate("/platform/tenants")}>View tenants </Button>} />
    <div className="platform-metrics">
      <Metric label="Tenants" value={tenants.length} note="Across the platform" mark="book" />
      <Metric label="Active tenants" value={tenants.filter((t) => t.status === "Active").length} note="Currently active" mark="check-circle" />
      <Metric label="Trial tenants" value={tenants.filter((t) => t.status === "Trial").length} note={openGates + " open setup gates"} mark="clock" />
      <Metric label="Platform users" value={users.length} note="Admin and support accounts" mark="users" />
    </div>
    <div className="platform-two-col">
      <Panel title="Go-live readiness" description="Tenants with setup items still to complete"><div className="platform-list">{tenants.filter((t) => t.gates > 0).map((tenant) => <div className="platform-list-row" key={tenant.id}><span className="platform-list-icon"><Icon name="clock" size={16}/></span><span className="platform-list-copy"><strong>{tenant.name}</strong><small>{tenant.gates} setup item{tenant.gates === 1 ? "" : "s"} remaining · {tenant.type}</small></span><Status value={tenant.status} /></div>)}</div>{openGates === 0 && <p className="platform-empty-inline">All current setup gates are complete.</p>}</Panel>
      <Panel title="Platform activity" description="Latest platform-scope events"><div className="platform-list">{initialAudit.length ? initialAudit.slice(0, 4).map((event) => <div className="platform-list-row" key={event.id}><span className="platform-list-icon"><Icon name="clock" size={16}/></span><span className="platform-list-copy"><strong>{event.action}</strong><small>{event.actor} · {event.when}</small></span><Status value={event.outcome} /></div>) : <p className="platform-empty-inline">No platform events are loaded in this prototype snapshot.</p>}</div></Panel>
    </div>
    <SampleNote />
  </div>;
}

export function TenantsPage() {
  const [tenants] = useState(initialTenants);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("All");
  const [showCreate, setShowCreate] = useState(false);
  const [draft, setDraft] = useState({ name: "", type: "School", email: "", plan: "Standard" });
  const [message, setMessage] = useState("");
  const filtered = useMemo(() => tenants.filter((t) => (status === "All" || t.status === status) && (t.name + t.id + t.type).toLowerCase().includes(query.toLowerCase())), [tenants, status, query]);
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setMessage("The form is valid. No tenant was created because the tenant API is not connected yet.");
    setShowCreate(false);
  };
  return <div className="platform-page">
    <PageHeader title="Tenants" description="Manage institution tenancy and review the steps required before go-live." actions={<Button onClick={() => { setMessage(""); setShowCreate(true); }}>Create trial tenant</Button>} />
    {message && <div className="platform-inline-notice" role="status">{message}</div>}
    <div className="platform-metrics platform-metrics--compact"><Metric label="All tenants" value={tenants.length} note="Sample register" mark="book" /><Metric label="Active" value={tenants.filter((t) => t.status === "Active").length} note="Live tenants" mark="check-circle" /><Metric label="In trial" value={tenants.filter((t) => t.status === "Trial").length} note="Activation gated" mark="clock" /></div>
    <Panel title="Tenant register" description="A platform-level register. Tenant academic records are not shown here.">
      <div className="platform-toolbar"><SearchBox value={query} onChange={setQuery} placeholder="Search by institution or ID" /><label className="platform-select-label">Status <select value={status} onChange={(e) => setStatus(e.target.value)}><option>All</option><option>Active</option><option>Trial</option><option>Suspended</option></select></label></div>
      <DataTable headers={["Institution", "Type", "Plan", "Status", "Open gates", "Last updated"]} empty={!filtered.length}>{filtered.map((tenant) => <tr key={tenant.id}><td><strong>{tenant.name}</strong><small className="platform-cell-sub">{tenant.id}</small></td><td>{tenant.type}</td><td>{tenant.plan}</td><td><Status value={tenant.status} /></td><td>{tenant.gates ? <Status value={tenant.gates + " pending"} /> : <Status value="Complete" />}</td><td>{tenant.updated}</td></tr>)}</DataTable>
    </Panel>
    {showCreate && <div className="platform-modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) setShowCreate(false); }}><section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="tenant-create-title"><header><div><Kicker>NEW TENANT</Kicker><h2 id="tenant-create-title">Create trial tenant</h2><p>New tenants begin in Trial. Activation remains gated until setup is complete.</p></div><button className="platform-icon-button" onClick={() => setShowCreate(false)} aria-label="Close"><Icon name="check-circle" size={16}/></button></header><form onSubmit={submit}><TextField id="tenant-name" label="Institution name" required value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="e.g. Lakeside School" /><div className="platform-form-grid"><label className="platform-form-field">Institution type<select value={draft.type} onChange={(e) => setDraft({ ...draft, type: e.target.value })}><option>School</option><option>College</option><option>University</option></select></label><label className="platform-form-field">Plan<select value={draft.plan} onChange={(e) => setDraft({ ...draft, plan: e.target.value })}><option>Standard</option><option>Premium</option><option>Enterprise</option></select></label></div><TextField id="tenant-admin-email" label="First administrator email" type="email" required value={draft.email} onChange={(e) => setDraft({ ...draft, email: e.target.value })} placeholder="admin@institution.edu" /><div className="platform-modal-note">This screen is currently a frontend preview. The form will not create a tenant or send an invitation until the API is integrated.</div><footer><Button variant="secondary" onClick={() => setShowCreate(false)}>Cancel</Button><Button type="submit">Validate details</Button></footer></form></section></div>}
    <SampleNote />
  </div>;
}

export function PlatformUsersPage() {
  const [users] = useState(initialUsers);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("All");
  const filtered = users.filter((u) => (filter === "All" || u.status === filter) && (u.name + u.email + u.role).toLowerCase().includes(query.toLowerCase()));
  return <div className="platform-page"><PageHeader title="Platform users" description="Manage access for people who operate the EDU 360 platform." actions={<Button disabled title="User invitation API is not connected">Invite platform user</Button>} /><Panel title="Platform access" description="Platform staff accounts only. Institution users are managed within their institution."><div className="platform-toolbar"><SearchBox value={query} onChange={setQuery} placeholder="Search name, email or role" /><label className="platform-select-label">Status <select value={filter} onChange={(e) => setFilter(e.target.value)}><option>All</option><option>Active</option><option>Invited</option><option>Suspended</option></select></label></div><DataTable headers={["Name", "Email", "Platform role", "Status", "Last active"]} empty={!filtered.length}>{filtered.map((u) => <tr key={u.id}><td><strong>{u.name}</strong><small className="platform-cell-sub">{u.id}</small></td><td>{u.email}</td><td>{u.role}</td><td><Status value={u.status} /></td><td>{u.lastActive}</td></tr>)}</DataTable></Panel><SampleNote /></div>;
}

export function PlansPage() {
  const plans = [{ name: "Standard", billing: "Annual", description: "Core institution workspace" }, { name: "Premium", billing: "Annual", description: "Expanded institution workspace" }, { name: "Enterprise", billing: "Custom", description: "Institution-specific agreement" }];
  return <div className="platform-page"><PageHeader title="Plans & billing" description="Review the platform plan catalogue and tenant distribution." /><div className="platform-plan-grid">{plans.map((plan) => <article className="platform-plan-card" key={plan.name}><Kicker>PLAN</Kicker><h2>{plan.name}</h2><p>{plan.description}</p><div className="platform-plan-card__billing">{plan.billing} billing</div><div className="platform-plan-card__footer"><span>Tenant allocation</span><strong>{initialTenants.filter((t) => t.plan === plan.name).length}</strong></div><Badge tone="neutral">Catalogue preview</Badge></article>)}</div><SampleNote /></div>;
}

export function SupportAccessPage() {
  const sessions = [{ ticket: "SUP-2026-00398", tenant: "Greenwood Public School", purpose: "Report export failure", status: "Expired", expires: "21 Oct 2026, 4:30 PM IST" }];
  return <div className="platform-page"><PageHeader title="Support access" description="Support access is ticket-bound, read-only, revocable and limited to 24 hours." /><div className="platform-policy-callout"><span aria-hidden="true"><Icon name="shield" size={15}/></span><div><strong>Tenant data remains protected</strong><p>Platform roles do not browse tenant records. A support session requires a ticket, a stated purpose and tenant approval.</p></div></div><Panel title="Support sessions" description="Requests and time-limited sessions"><DataTable headers={["Ticket", "Institution", "Purpose", "Status", "Access ends"]}>{sessions.map((s) => <tr key={s.ticket}><td><strong>{s.ticket}</strong></td><td>{s.tenant}</td><td>{s.purpose}</td><td><Status value={s.status} /></td><td>{s.expires}</td></tr>)}</DataTable></Panel><SampleNote /></div>;
}

// Real shape from GET /api/platform/audit-logs (AuditLogEntryResponse.java /
// AuditLogPageResponse.java) - reads platform.audit_outbox only, never a tenant's own audit
// trail. See PlatformAuditOutboxEvent's javadoc: nothing writes to that table yet, so this will
// legitimately come back empty until a platform-action audit writer exists - the empty state
// below says so honestly instead of showing a fake row.
type AuditLogItem = {
  auditOutboxId: string;
  tenantId: string | null;
  occurredAt: string;
  actorKind: string;
  actorUserId: string | null;
  operation: string;
  actionCode: string | null;
  entityType: string | null;
  entityId: string | null;
  outcome: string;
  correlationId: string | null;
};
type FetchStatus = "loading" | "success" | "error";

export function AuditLogPage() {
  const [status, setStatus] = useState<FetchStatus>("loading");
  const [events, setEvents] = useState<AuditLogItem[]>([]);
  const [errorMessage, setErrorMessage] = useState("");
  const [query, setQuery] = useState("");
  const [outcome, setOutcome] = useState("All");

  useEffect(() => {
    let active = true;
    api.get("/platform/audit-logs", { params: { page: 0, size: 100 } })
      .then((res) => {
        if (!active) return;
        setEvents(res.data?.items ?? []);
        setStatus("success");
      })
      .catch((err) => {
        if (!active) return;
        setErrorMessage(readErrorMessage(err, "Could not load audit events."));
        setStatus("error");
      });
    return () => { active = false; };
  }, []);

  const filtered = useMemo(
    () => events.filter((e) =>
      (outcome === "All" || titleCaseOutcome(e.outcome) === outcome) &&
      [e.actorKind, e.actorUserId, e.operation, e.actionCode, e.entityType, e.auditOutboxId]
        .filter(Boolean)
        .join(" ")
        .toLowerCase()
        .includes(query.toLowerCase())),
    [events, outcome, query],
  );

  return <div className="platform-page">
    <PageHeader title="Audit log" description="Review platform-scope activity, including successful actions and denied attempts." />
    <Panel title="Platform audit events" description="Tenant-level events remain within each tenant.">
      {status === "error" && <Notice tone="danger">Could not load audit events: {errorMessage}</Notice>}
      <div className="platform-toolbar">
        <SearchBox value={query} onChange={setQuery} placeholder="Search actor, action or target" />
        <label className="platform-select-label">Outcome <select value={outcome} onChange={(e) => setOutcome(e.target.value)}><option>All</option><option>Success</option><option>Denied</option></select></label>
      </div>
      {status === "loading"
        ? <div className="platform-empty-inline"><Spinner label="Loading audit events" /></div>
        : <DataTable
            headers={["When", "Actor", "Action", "Target", "Outcome"]}
            empty={!filtered.length}
            emptyMessage={status === "error" ? "Audit events could not be loaded." : "No platform audit events recorded yet."}
          >
            {filtered.map((e) => <tr key={e.auditOutboxId}>
              <td>{formatTimestamp(e.occurredAt)}</td>
              <td><strong>{e.actorKind}</strong>{e.actorUserId && <small className="platform-cell-sub">{e.actorUserId}</small>}</td>
              <td><strong>{e.actionCode ?? e.operation}</strong><small className="platform-cell-sub">{e.operation}</small></td>
              <td>{e.entityType ?? "—"}{e.entityId && <small className="platform-cell-sub">{e.entityId}</small>}</td>
              <td><Status value={titleCaseOutcome(e.outcome)} /></td>
            </tr>)}
          </DataTable>}
    </Panel>
  </div>;
}

// Real shape from GET /api/platform/usage-counts (UsageCountsResponse.java /
// UsageMetricSnapshot.java). totalUserCountGap is a real, deliberate gap the backend flags
// (cross-tenant RLS boundary) rather than a fabricated figure - shown here as an honest
// "not available" note, never smoothed over with a guessed number.
type UsageMetric = { metricCode: string; value: number | null; asOfDate: string | null };
type UsageCounts = { totalTenantCount: number; metrics: UsageMetric[]; totalUserCountGap: string | null };
const USAGE_METRIC_LABELS: Record<string, string> = {
  LOGINS: "Logins",
  ATTENDANCE_MARKED: "Attendance marked",
  FEES_PAID_ONLINE: "Fees paid online",
};

export function UsageCountsPage() {
  const [status, setStatus] = useState<FetchStatus>("loading");
  const [data, setData] = useState<UsageCounts | null>(null);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    let active = true;
    api.get("/platform/usage-counts")
      .then((res) => {
        if (!active) return;
        setData(res.data);
        setStatus("success");
      })
      .catch((err) => {
        if (!active) return;
        setErrorMessage(readErrorMessage(err, "Could not load usage counts."));
        setStatus("error");
      });
    return () => { active = false; };
  }, []);

  return <div className="platform-page">
    <PageHeader title="Usage counts" description="Anonymous, combined platform usage numbers. No per-tenant or per-user figures are shown here." />
    {status === "error" && <Notice tone="danger">Could not load usage counts: {errorMessage}</Notice>}
    <Panel title="Platform usage" description="platform.usage_metric_daily, anonymous and combined across tenants that have not opted out">
      {status === "loading"
        ? <div className="platform-empty-inline"><Spinner label="Loading usage counts" /></div>
        : data && <>
            <div className="platform-metrics">
              <Metric label="Tenants" value={data.totalTenantCount} note="Across the platform" mark="book" />
              {data.metrics.map((m) => <Metric
                key={m.metricCode}
                label={USAGE_METRIC_LABELS[m.metricCode] ?? m.metricCode}
                value={m.value ?? "Not available"}
                note={m.asOfDate ? `As of ${m.asOfDate}` : "No value computed yet"}
                mark="clock"
              />)}
            </div>
            {data.totalUserCountGap && <Notice tone="info"><strong>Total user count: not available.</strong> {data.totalUserCountGap}</Notice>}
          </>}
    </Panel>
  </div>;
}

// Real shape from GET /api/platform/backup-status (BackupStatusResponse.java /
// BackupStatusEntryResponse.java). wiredToRealBackupSystem is hard-coded false on the backend
// today - the banner below surfaces that honestly rather than implying backups are monitored.
type BackupStatusEntry = {
  tenantId: string;
  lastDailyBackupAt: string | null;
  lastBackupOutcome: string | null;
  earliestRestorableAt: string | null;
  latestRestorableAt: string | null;
  lastRestoreTestAt: string | null;
  lastRestoreTestOutcome: string | null;
};
type BackupStatus = { wiredToRealBackupSystem: boolean; note: string; tenants: BackupStatusEntry[] };

export function BackupStatusPage() {
  const [status, setStatus] = useState<FetchStatus>("loading");
  const [data, setData] = useState<BackupStatus | null>(null);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    let active = true;
    api.get("/platform/backup-status")
      .then((res) => {
        if (!active) return;
        setData(res.data);
        setStatus("success");
      })
      .catch((err) => {
        if (!active) return;
        setErrorMessage(readErrorMessage(err, "Could not load backup status."));
        setStatus("error");
      });
    return () => { active = false; };
  }, []);

  return <div className="platform-page">
    <PageHeader title="Backup status" description="Per-tenant daily backup and restore-test status." />
    {status === "error" && <Notice tone="danger">Could not load backup status: {errorMessage}</Notice>}
    {data && !data.wiredToRealBackupSystem && <Notice tone="info"><strong>Not connected to a real backup system.</strong> {data.note}</Notice>}
    <Panel title="Tenant backup status" description="platform.tenant_backup_status">
      {status === "loading"
        ? <div className="platform-empty-inline"><Spinner label="Loading backup status" /></div>
        : <DataTable
            headers={["Tenant", "Last backup", "Backup outcome", "Restorable window", "Last restore test", "Restore test outcome"]}
            empty={!data?.tenants.length}
            emptyMessage={status === "error" ? "Backup status could not be loaded." : "No backup status rows recorded yet."}
          >
            {(data?.tenants ?? []).map((t) => <tr key={t.tenantId}>
              <td>{t.tenantId}</td>
              <td>{formatTimestamp(t.lastDailyBackupAt)}</td>
              <td>{t.lastBackupOutcome ?? "—"}</td>
              <td>{t.earliestRestorableAt && t.latestRestorableAt ? `${formatTimestamp(t.earliestRestorableAt)} – ${formatTimestamp(t.latestRestorableAt)}` : "—"}</td>
              <td>{formatTimestamp(t.lastRestoreTestAt)}</td>
              <td>{t.lastRestoreTestOutcome ?? "—"}</td>
            </tr>)}
          </DataTable>}
    </Panel>
  </div>;
}

// Real shape from /api/platform/integration-types (IntegrationTypeResponse.java /
// CreateIntegrationTypeRequest.java / UpdateIntegrationTypeRequest.java). This manages only the
// TYPE catalogue (e.g. "SMS_DLT") - never a tenant's actual provider keys/webhooks.
type IntegrationTypeStatusValue = "ALLOWED" | "DISALLOWED";
type IntegrationTypeItem = {
  integrationTypeId: string;
  integrationTypeCode: string;
  description: string;
  status: IntegrationTypeStatusValue;
  allowedProviders: string[];
  createdAt: string;
  updatedAt: string;
  rowVersion: number;
};
function parseProviders(value: string): string[] {
  return value.split(",").map((p) => p.trim()).filter(Boolean);
}

export function IntegrationTypesPage() {
  const [status, setStatus] = useState<FetchStatus>("loading");
  const [items, setItems] = useState<IntegrationTypeItem[]>([]);
  const [errorMessage, setErrorMessage] = useState("");
  const [query, setQuery] = useState("");
  const [message, setMessage] = useState("");

  const [showCreate, setShowCreate] = useState(false);
  const [createDraft, setCreateDraft] = useState({ integrationTypeCode: "", description: "", allowedProviders: "" });
  const [createError, setCreateError] = useState("");
  const [creating, setCreating] = useState(false);

  const [editing, setEditing] = useState<IntegrationTypeItem | null>(null);
  const [editDraft, setEditDraft] = useState({ description: "", status: "ALLOWED" as IntegrationTypeStatusValue, allowedProviders: "" });
  const [editError, setEditError] = useState("");
  const [saving, setSaving] = useState(false);

  // Deliberately does not set status back to "loading" on reload after a create/edit - the
  // table already has real rows to show, and resetting to "loading" would flash the spinner and
  // hide them while the PATCH/POST just applied finishes refetching.
  const load = () =>
    api.get("/platform/integration-types")
      .then((res) => { setItems(res.data ?? []); setStatus("success"); })
      .catch((err) => { setErrorMessage(readErrorMessage(err, "Could not load integration types.")); setStatus("error"); });

  useEffect(() => {
    load();
  }, []);

  const filtered = items.filter((i) => (i.integrationTypeCode + i.description).toLowerCase().includes(query.toLowerCase()));

  const submitCreate = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setCreateError("");
    setCreating(true);
    api.post("/platform/integration-types", {
      integrationTypeCode: createDraft.integrationTypeCode,
      description: createDraft.description,
      allowedProviders: parseProviders(createDraft.allowedProviders),
    })
      .then(() => {
        setShowCreate(false);
        setCreateDraft({ integrationTypeCode: "", description: "", allowedProviders: "" });
        setMessage("Integration type created.");
        return load();
      })
      .catch((err) => setCreateError(readErrorMessage(err, "Could not create integration type.")))
      .finally(() => setCreating(false));
  };

  const openEdit = (item: IntegrationTypeItem) => {
    setMessage("");
    setEditError("");
    setEditing(item);
    setEditDraft({ description: item.description, status: item.status, allowedProviders: item.allowedProviders.join(", ") });
  };

  const submitEdit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editing) return;
    setEditError("");
    setSaving(true);
    api.patch(`/platform/integration-types/${editing.integrationTypeId}`, {
      description: editDraft.description,
      status: editDraft.status,
      allowedProviders: parseProviders(editDraft.allowedProviders),
    })
      .then(() => {
        setEditing(null);
        setMessage("Integration type updated.");
        return load();
      })
      .catch((err) => setEditError(readErrorMessage(err, "Could not update integration type.")))
      .finally(() => setSaving(false));
  };

  return <div className="platform-page">
    <PageHeader
      title="Integration types"
      description="The catalogue of integration types a tenant may enable (e.g. SMS, payments). Type catalogue only - never a tenant's own keys or webhooks."
      actions={<Button onClick={() => { setMessage(""); setCreateError(""); setShowCreate(true); }}>＋ Add integration type</Button>}
    />
    {message && <div className="platform-inline-notice" role="status">{message}</div>}
    {status === "error" && <Notice tone="danger">Could not load integration types: {errorMessage}</Notice>}
    <Panel title="Allowed integration types" description="platform.integration_type">
      <div className="platform-toolbar"><SearchBox value={query} onChange={setQuery} placeholder="Search by code or description" /></div>
      {status === "loading"
        ? <div className="platform-empty-inline"><Spinner label="Loading integration types" /></div>
        : <DataTable
            headers={["Code", "Description", "Status", "Allowed providers", "Updated", ""]}
            empty={!filtered.length}
            emptyMessage={status === "error" ? "Integration types could not be loaded." : "No integration types defined yet."}
          >
            {filtered.map((item) => <tr key={item.integrationTypeId}>
              <td><strong>{item.integrationTypeCode}</strong></td>
              <td>{item.description}</td>
              <td><Status value={item.status === "ALLOWED" ? "Allowed" : "Disallowed"} /></td>
              <td>{item.allowedProviders.length ? item.allowedProviders.join(", ") : "—"}</td>
              <td>{formatTimestamp(item.updatedAt)}</td>
              <td><Button variant="secondary" size="sm" onClick={() => openEdit(item)}>Edit</Button></td>
            </tr>)}
          </DataTable>}
    </Panel>
    {showCreate && <div className="platform-modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) setShowCreate(false); }}><section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="integration-create-title"><header><div><Kicker>NEW INTEGRATION TYPE</Kicker><h2 id="integration-create-title">Add integration type</h2><p>Adds a new allowed category to the platform catalogue.</p></div><button className="platform-icon-button" onClick={() => setShowCreate(false)} aria-label="Close"><Icon name="x-circle" size={18}/></button></header><form onSubmit={submitCreate}><TextField id="integration-code" label="Integration type code" required value={createDraft.integrationTypeCode} onChange={(e) => setCreateDraft({ ...createDraft, integrationTypeCode: e.target.value })} placeholder="e.g. SMS_DLT" /><TextField id="integration-description" label="Description" required value={createDraft.description} onChange={(e) => setCreateDraft({ ...createDraft, description: e.target.value })} placeholder="What this integration type covers" /><TextField id="integration-providers" label="Allowed providers (comma-separated)" value={createDraft.allowedProviders} onChange={(e) => setCreateDraft({ ...createDraft, allowedProviders: e.target.value })} placeholder="e.g. TWILIO, MSG91" />{createError && <Notice tone="danger">{createError}</Notice>}<footer><Button variant="secondary" onClick={() => setShowCreate(false)}>Cancel</Button><Button type="submit" busy={creating}>Add integration type</Button></footer></form></section></div>}
    {editing && <div className="platform-modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) setEditing(null); }}><section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="integration-edit-title"><header><div><Kicker>EDIT</Kicker><h2 id="integration-edit-title">Edit {editing.integrationTypeCode}</h2><p>Updates apply immediately to the platform catalogue.</p></div><button className="platform-icon-button" onClick={() => setEditing(null)} aria-label="Close"><Icon name="x-circle" size={18}/></button></header><form onSubmit={submitEdit}><TextField id="integration-edit-description" label="Description" required value={editDraft.description} onChange={(e) => setEditDraft({ ...editDraft, description: e.target.value })} /><div className="platform-form-grid"><label className="platform-form-field">Status<select value={editDraft.status} onChange={(e) => setEditDraft({ ...editDraft, status: e.target.value as IntegrationTypeStatusValue })}><option value="ALLOWED">Allowed</option><option value="DISALLOWED">Disallowed</option></select></label></div><TextField id="integration-edit-providers" label="Allowed providers (comma-separated)" value={editDraft.allowedProviders} onChange={(e) => setEditDraft({ ...editDraft, allowedProviders: e.target.value })} />{editError && <Notice tone="danger">{editError}</Notice>}<footer><Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button><Button type="submit" busy={saving}>Save changes</Button></footer></form></section></div>}
  </div>;
}
