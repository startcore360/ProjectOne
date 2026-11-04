import { useMemo, useState, type FormEvent, type ReactNode } from "react";\nimport { useNavigate } from "react-router-dom";
import { Button, Kicker, TextField } from "../../../design-system/components";
import { Badge } from "../../../components/ui";
import { PageHeader } from "../../../components/layout/PageHeader";

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

function Metric({ label, value, note, mark }: { label: string; value: string | number; note: string; mark: string }) {
  return <article className="platform-metric"><span className="platform-metric__mark" aria-hidden="true">{mark}</span><p>{label}</p><strong>{value}</strong><small>{note}</small></article>;
}
function Panel({ title, description, action, children }: { title: string; description?: string; action?: ReactNode; children: ReactNode }) {
  return <section className="platform-panel"><header className="platform-panel__header"><div><h2>{title}</h2>{description && <p>{description}</p>}</div>{action}</header>{children}</section>;
}
function Status({ value }: { value: string }) {
  const tone = value === "Active" || value === "Success" ? "success" : value === "Trial" || value === "Invited" ? "warning" : value === "Denied" || value === "Suspended" ? "danger" : "neutral";
  return <Badge tone={tone}>{value}</Badge>;
}
function DataTable({ headers, children, empty }: { headers: string[]; children: ReactNode; empty?: boolean }) {
  return <div className="platform-table-wrap"><table className="platform-table"><thead><tr>{headers.map((h) => <th key={h} scope="col">{h}</th>)}</tr></thead><tbody>{empty ? <tr><td colSpan={headers.length} className="platform-empty-cell">No matching records.</td></tr> : children}</tbody></table></div>;
}
function SearchBox({ value, onChange, placeholder = "Search records" }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return <label className="platform-search"><span aria-hidden="true">⌕</span><span className="visually-hidden">Search</span><input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} /></label>;
}
function SampleNote() {
  return <p className="platform-sample-note"><span aria-hidden="true">ⓘ</span> Sample interface data for layout review. Live records and actions are not connected yet.</p>;
}

export function PlatformOverviewPage() {
  const navigate = useNavigate();\n  const [tenants] = useState(initialTenants);
  const [users] = useState(initialUsers);
  const openGates = tenants.reduce((sum, tenant) => sum + tenant.gates, 0);
  return <div className="platform-page">
    <PageHeader title="Platform overview" description="Tenant lifecycle, platform access and service health at a glance." actions={<Button variant="secondary" onClick={() => navigate("/platform/tenants")}>View tenants <span aria-hidden="true">→</span></Button>} />
    <div className="platform-metrics">
      <Metric label="Tenants" value={tenants.length} note="Across the platform" mark="▤" />
      <Metric label="Active tenants" value={tenants.filter((t) => t.status === "Active").length} note="Currently active" mark="✓" />
      <Metric label="Trial tenants" value={tenants.filter((t) => t.status === "Trial").length} note={openGates + " open setup gates"} mark="◷" />
      <Metric label="Platform users" value={users.length} note="Admin and support accounts" mark="♙" />
    </div>
    <div className="platform-two-col">
      <Panel title="Go-live readiness" description="Tenants with setup items still to complete"><div className="platform-list">{tenants.filter((t) => t.gates > 0).map((tenant) => <div className="platform-list-row" key={tenant.id}><span className="platform-list-icon">!</span><span className="platform-list-copy"><strong>{tenant.name}</strong><small>{tenant.gates} setup item{tenant.gates === 1 ? "" : "s"} remaining · {tenant.type}</small></span><Status value={tenant.status} /></div>)}</div>{openGates === 0 && <p className="platform-empty-inline">All current setup gates are complete.</p>}</Panel>
      <Panel title="Platform activity" description="Latest platform-scope events"><div className="platform-list">{initialAudit.slice(0, 4).map((event) => <div className="platform-list-row" key={event.id}><span className="platform-list-icon">◷</span><span className="platform-list-copy"><strong>{event.action}</strong><small>{event.actor} · {event.when}</small></span><Status value={event.outcome} /></div>)}</div></Panel>
    </div>
    <SampleNote />
  </div>;
}

export function TenantsPage() {
  const [tenants, setTenants] = useState(initialTenants);
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
    <PageHeader title="Tenants" description="Manage institution tenancy and review the steps required before go-live." actions={<Button onClick={() => { setMessage(""); setShowCreate(true); }}>＋ Create trial tenant</Button>} />
    {message && <div className="platform-inline-notice" role="status">{message}</div>}
    <div className="platform-metrics platform-metrics--compact"><Metric label="All tenants" value={tenants.length} note="Sample register" mark="▤" /><Metric label="Active" value={tenants.filter((t) => t.status === "Active").length} note="Live tenants" mark="✓" /><Metric label="In trial" value={tenants.filter((t) => t.status === "Trial").length} note="Activation gated" mark="◷" /></div>
    <Panel title="Tenant register" description="A platform-level register. Tenant academic records are not shown here.">
      <div className="platform-toolbar"><SearchBox value={query} onChange={setQuery} placeholder="Search by institution or ID" /><label className="platform-select-label">Status <select value={status} onChange={(e) => setStatus(e.target.value)}><option>All</option><option>Active</option><option>Trial</option><option>Suspended</option></select></label></div>
      <DataTable headers={["Institution", "Type", "Plan", "Status", "Open gates", "Last updated"]} empty={!filtered.length}>{filtered.map((tenant) => <tr key={tenant.id}><td><strong>{tenant.name}</strong><small className="platform-cell-sub">{tenant.id}</small></td><td>{tenant.type}</td><td>{tenant.plan}</td><td><Status value={tenant.status} /></td><td>{tenant.gates ? <Status value={tenant.gates + " pending"} /> : <Status value="Complete" />}</td><td>{tenant.updated}</td></tr>)}</DataTable>
    </Panel>
    {showCreate && <div className="platform-modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) setShowCreate(false); }}><section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="tenant-create-title"><header><div><Kicker>NEW TENANT</Kicker><h2 id="tenant-create-title">Create trial tenant</h2><p>New tenants begin in Trial. Activation remains gated until setup is complete.</p></div><button className="platform-icon-button" onClick={() => setShowCreate(false)} aria-label="Close">×</button></header><form onSubmit={submit}><TextField id="tenant-name" label="Institution name" required value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="e.g. Lakeside School" /><div className="platform-form-grid"><label className="platform-form-field">Institution type<select value={draft.type} onChange={(e) => setDraft({ ...draft, type: e.target.value })}><option>School</option><option>College</option><option>University</option></select></label><label className="platform-form-field">Plan<select value={draft.plan} onChange={(e) => setDraft({ ...draft, plan: e.target.value })}><option>Standard</option><option>Premium</option><option>Enterprise</option></select></label></div><TextField id="tenant-admin-email" label="First administrator email" type="email" required value={draft.email} onChange={(e) => setDraft({ ...draft, email: e.target.value })} placeholder="admin@institution.edu" /><div className="platform-modal-note">This screen is currently a frontend preview. The form will not create a tenant or send an invitation until the API is integrated.</div><footer><Button variant="secondary" onClick={() => setShowCreate(false)}>Cancel</Button><Button type="submit">Validate details</Button></footer></form></section></div>}
    <SampleNote />
  </div>;
}

export function PlatformUsersPage() {
  const [users] = useState(initialUsers);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("All");
  const filtered = users.filter((u) => (filter === "All" || u.status === filter) && (u.name + u.email + u.role).toLowerCase().includes(query.toLowerCase()));
  return <div className="platform-page"><PageHeader title="Platform users" description="Manage access for people who operate the EDU 360 platform." actions={<Button disabled title="User invitation API is not connected">＋ Invite platform user</Button>} /><Panel title="Platform access" description="Platform staff accounts only. Institution users are managed within their institution."><div className="platform-toolbar"><SearchBox value={query} onChange={setQuery} placeholder="Search name, email or role" /><label className="platform-select-label">Status <select value={filter} onChange={(e) => setFilter(e.target.value)}><option>All</option><option>Active</option><option>Invited</option><option>Suspended</option></select></label></div><DataTable headers={["Name", "Email", "Platform role", "Status", "Last active"]} empty={!filtered.length}>{filtered.map((u) => <tr key={u.id}><td><strong>{u.name}</strong><small className="platform-cell-sub">{u.id}</small></td><td>{u.email}</td><td>{u.role}</td><td><Status value={u.status} /></td><td>{u.lastActive}</td></tr>)}</DataTable></Panel><SampleNote /></div>;
}

export function PlansPage() {
  const plans = [{ name: "Standard", billing: "Annual", description: "Core institution workspace" }, { name: "Premium", billing: "Annual", description: "Expanded institution workspace" }, { name: "Enterprise", billing: "Custom", description: "Institution-specific agreement" }];
  return <div className="platform-page"><PageHeader title="Plans & billing" description="Review the platform plan catalogue and tenant distribution." /><div className="platform-plan-grid">{plans.map((plan) => <article className="platform-plan-card" key={plan.name}><Kicker>PLAN</Kicker><h2>{plan.name}</h2><p>{plan.description}</p><div className="platform-plan-card__billing">{plan.billing} billing</div><div className="platform-plan-card__footer"><span>Tenant allocation</span><strong>{initialTenants.filter((t) => t.plan === plan.name).length}</strong></div><Badge tone="neutral">Catalogue preview</Badge></article>)}</div><SampleNote /></div>;
}

export function SupportAccessPage() {
  const sessions = [{ ticket: "SUP-2026-00398", tenant: "Greenwood Public School", purpose: "Report export failure", status: "Expired", expires: "21 Oct 2026, 4:30 PM IST" }];
  return <div className="platform-page"><PageHeader title="Support access" description="Support access is ticket-bound, read-only, revocable and limited to 24 hours." /><div className="platform-policy-callout"><span aria-hidden="true">⛨</span><div><strong>Tenant data remains protected</strong><p>Platform roles do not browse tenant records. A support session requires a ticket, a stated purpose and tenant approval.</p></div></div><Panel title="Support sessions" description="Requests and time-limited sessions"><DataTable headers={["Ticket", "Institution", "Purpose", "Status", "Access ends"]}>{sessions.map((s) => <tr key={s.ticket}><td><strong>{s.ticket}</strong></td><td>{s.tenant}</td><td>{s.purpose}</td><td><Status value={s.status} /></td><td>{s.expires}</td></tr>)}</DataTable></Panel><SampleNote /></div>;
}

export function AuditLogPage() {
  const [events] = useState(initialAudit);
  const [query, setQuery] = useState("");
  const [outcome, setOutcome] = useState("All");
  const filtered = events.filter((e) => (outcome === "All" || e.outcome === outcome) && (e.actor + e.action + e.target + e.id).toLowerCase().includes(query.toLowerCase()));
  return <div className="platform-page"><PageHeader title="Audit log" description="Review platform-scope activity, including successful actions and denied attempts." /><Panel title="Platform audit events" description="Tenant-level events remain within each tenant."><div className="platform-toolbar"><SearchBox value={query} onChange={setQuery} placeholder="Search actor, action or target" /><label className="platform-select-label">Outcome <select value={outcome} onChange={(e) => setOutcome(e.target.value)}><option>All</option><option>Success</option><option>Denied</option></select></label></div><DataTable headers={["When", "Actor", "Action", "Target", "Outcome"]} empty={!filtered.length}>{filtered.map((e) => <tr key={e.id}><td>{e.when}</td><td>{e.actor}</td><td><strong>{e.action}</strong><small className="platform-cell-sub">{e.id}</small></td><td>{e.target}</td><td><Status value={e.outcome} /></td></tr>)}</DataTable></Panel><SampleNote /></div>;
}
