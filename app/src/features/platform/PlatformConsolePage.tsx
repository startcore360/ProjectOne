import { useMemo, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { Button, Kicker, TextField } from "../../design-system/components";

type Screen = "overview" | "institutions" | "users" | "access" | "audit" | "settings";

const navigation: { id: Screen; label: string; icon: string; path: string }[] = [
  { id: "overview", label: "Overview", icon: "▦", path: "/platform" },
  { id: "institutions", label: "Institutions", icon: "⌂", path: "/platform/institutions" },
  { id: "users", label: "Users", icon: "♙", path: "/platform/users" },
  { id: "access", label: "Roles & access", icon: "◇", path: "/platform/access" },
  { id: "audit", label: "Audit activity", icon: "◷", path: "/platform/audit" },
  { id: "settings", label: "Platform settings", icon: "⚙", path: "/platform/settings" },
];

const institutions = [
  { name: "Northfield Academy", code: "NFA-001", region: "Bengaluru", users: 842, status: "Active" },
  { name: "Greenwood Public School", code: "GPS-014", region: "Mysuru", users: 516, status: "Active" },
  { name: "Riverstone Learning Centre", code: "RLC-022", region: "Hubballi", users: 0, status: "Pending setup" },
];

const users = [
  { name: "Aarav Menon", email: "aarav.menon@example.edu", role: "Institution admin", institution: "Northfield Academy", status: "Active" },
  { name: "Diya Rao", email: "diya.rao@example.edu", role: "Teacher", institution: "Greenwood Public School", status: "Active" },
  { name: "Ishaan Shah", email: "ishaan.shah@example.edu", role: "Platform support", institution: "Platform", status: "Invited" },
];

const auditEvents = [
  { action: "Institution profile viewed", actor: "Platform operator", target: "Northfield Academy", time: "Today, 10:42 AM", result: "Viewed" },
  { action: "User role updated", actor: "Institution admin", target: "Diya Rao", time: "Today, 09:18 AM", result: "Recorded" },
  { action: "Sign-in attempt", actor: "Aarav Menon", target: "Northfield Academy", time: "Yesterday, 04:36 PM", result: "Successful" },
];

function Status({ value }: { value: string }) {
  const variant = value === "Active" || value === "Successful" || value === "Recorded"
    ? "success"
    : value === "Pending setup" || value === "Invited"
      ? "warning"
      : "neutral";
  return <span className={`eds-badge eds-badge--${variant}`}>{value}</span>;
}

function PageHeading({ title, description, eyebrow }: { title: string; description: string; eyebrow: string }) {
  return (
    <header className="eds-page-header">
      <div>
        <Kicker>{eyebrow}</Kicker>
        <h1 className="eds-page-header__title">{title}</h1>
        <p className="eds-page-header__description">{description}</p>
      </div>
      <div className="eds-page-header__actions">
        <Button variant="secondary" disabled title="Connect the platform API before enabling exports">Export</Button>
        <Button disabled title="Connect the platform API before creating records">＋ Create new</Button>
      </div>
    </header>
  );
}

function PreviewNotice() {
  return (
    <div className="platform-preview-notice" role="status">
      <span className="platform-preview-notice__icon" aria-hidden="true">i</span>
      <div>
        <strong>Interface preview · Not connected to live platform data</strong>
        <p>Example records are shown to demonstrate the layout. Create, edit, permission and settings actions remain disabled until their APIs and server-side access checks are connected.</p>
      </div>
    </div>
  );
}

function Overview() {
  return (
    <>
      <PageHeading eyebrow="PLATFORM WORKSPACE" title="Good morning." description="A clear view of the institutions, people and access foundations across EDU 360." />
      <PreviewNotice />
      <section className="platform-welcome">
        <div>
          <p className="platform-welcome__eyebrow">EDU 360 · PLATFORM OPERATIONS</p>
          <h2>Build a trusted foundation for every institution.</h2>
          <p>Manage institution setup, user access and platform oversight from one workspace.</p>
        </div>
        <div className="platform-welcome__mark" aria-hidden="true"><span>e</span><i>360</i></div>
      </section>
      <section className="platform-section" aria-labelledby="platform-summary-heading">
        <div className="platform-section__heading">
          <div><Kicker>AT A GLANCE</Kicker><h2 id="platform-summary-heading">Platform summary</h2></div>
          <span className="platform-muted">Illustrative values only</span>
        </div>
        <div className="platform-metrics">
          <article className="platform-metric"><span className="platform-metric__icon">⌂</span><span className="platform-metric__label">Institutions</span><strong>03</strong><small>Across example regions</small></article>
          <article className="platform-metric"><span className="platform-metric__icon">♙</span><span className="platform-metric__label">User accounts</span><strong>1,358</strong><small>Example account total</small></article>
          <article className="platform-metric"><span className="platform-metric__icon">◇</span><span className="platform-metric__label">Access roles</span><strong>71</strong><small>Role model in V4.0 specification</small></article>
          <article className="platform-metric"><span className="platform-metric__icon">◷</span><span className="platform-metric__label">Audit events</span><strong>24</strong><small>Illustrative recent events</small></article>
        </div>
      </section>
      <section className="platform-section platform-two-column">
        <article className="platform-panel">
          <div className="platform-section__heading"><div><Kicker>SETUP</Kicker><h2>Foundation checklist</h2></div><Status value="In progress" /></div>
          {[
            ["Institution records", "Set up the institution and its identity"],
            ["User identity", "Connect accounts to the correct institution"],
            ["Roles and permissions", "Apply server-checked access rules"],
            ["Audit trail", "Record important administrative actions"],
          ].map(([title, description], index) => (
            <div className="platform-check-row" key={title}><span className="platform-check-row__number">0{index + 1}</span><div><strong>{title}</strong><p>{description}</p></div><Status value="Not connected" /></div>
          ))}
        </article>
        <article className="platform-panel">
          <Kicker>RECENT ACTIVITY</Kicker><h2>Latest audit events</h2>
          <div className="platform-activity-list">
            {auditEvents.slice(0, 2).map((event) => <div className="platform-activity" key={event.action}><span className="platform-activity__dot" /><div><strong>{event.action}</strong><p>{event.actor} · {event.time}</p></div></div>)}
          </div>
          <NavLink className="platform-text-link" to="/platform/audit">View audit activity <span aria-hidden="true">→</span></NavLink>
        </article>
      </section>
    </>
  );
}

function Institutions() {
  const [query, setQuery] = useState("");
  const filtered = useMemo(() => institutions.filter((item) => [item.name, item.code, item.region].join(" ").toLowerCase().includes(query.toLowerCase())), [query]);
  return (
    <>
      <PageHeading eyebrow="PLATFORM MANAGEMENT" title="Institutions" description="Review institution records and their setup status." />
      <PreviewNotice />
      <section className="platform-panel platform-table-panel">
        <div className="platform-table-toolbar"><div><h2>Institution directory</h2><p>Example records · {filtered.length} shown</p></div><div className="platform-search"><TextField id="institution-search" label="Search institutions" placeholder="Name, code or region" value={query} onChange={(event) => setQuery(event.target.value)} /></div></div>
        <div className="platform-table-scroll"><table className="platform-table"><thead><tr><th>Institution</th><th>Institution code</th><th>Region</th><th>Users</th><th>Setup status</th><th><span className="platform-sr-only">Actions</span></th></tr></thead><tbody>{filtered.map((item) => <tr key={item.code}><td><strong>{item.name}</strong><small>Institution workspace</small></td><td>{item.code}</td><td>{item.region}</td><td>{item.users.toLocaleString("en-IN")}</td><td><Status value={item.status} /></td><td><Button size="sm" variant="tertiary" disabled>View</Button></td></tr>)}</tbody></table></div>
        {filtered.length === 0 && <div className="platform-empty">No institutions match that search.</div>}
      </section>
    </>
  );
}

function Users() {
  const [query, setQuery] = useState("");
  const filtered = useMemo(() => users.filter((item) => [item.name, item.email, item.role, item.institution].join(" ").toLowerCase().includes(query.toLowerCase())), [query]);
  return (
    <>
      <PageHeading eyebrow="IDENTITY & ACCOUNTS" title="Users" description="Review accounts, assigned roles and institution associations." />
      <PreviewNotice />
      <section className="platform-panel platform-table-panel">
        <div className="platform-table-toolbar"><div><h2>User directory</h2><p>Example accounts · {filtered.length} shown</p></div><div className="platform-search"><TextField id="user-search" label="Search users" placeholder="Name, email, role or institution" value={query} onChange={(event) => setQuery(event.target.value)} /></div></div>
        <div className="platform-table-scroll"><table className="platform-table"><thead><tr><th>User</th><th>Assigned role</th><th>Institution scope</th><th>Account status</th><th><span className="platform-sr-only">Actions</span></th></tr></thead><tbody>{filtered.map((item) => <tr key={item.email}><td><strong>{item.name}</strong><small>{item.email}</small></td><td>{item.role}</td><td>{item.institution}</td><td><Status value={item.status} /></td><td><Button size="sm" variant="tertiary" disabled>View</Button></td></tr>)}</tbody></table></div>
        {filtered.length === 0 && <div className="platform-empty">No users match that search.</div>}
      </section>
    </>
  );
}

function Access() {
  const groups = [
    { title: "Platform administration", detail: "Platform-wide configuration and operational oversight", count: "Platform scope" },
    { title: "Institution administration", detail: "Manage users and settings within an assigned institution", count: "Institution scope" },
    { title: "Teaching and learning", detail: "Role permissions for teaching and academic workflows", count: "Institution scope" },
    { title: "Student and family", detail: "Access limited to permitted personal and learning information", count: "Record scope" },
  ];
  return (
    <>
      <PageHeading eyebrow="ACCESS GOVERNANCE" title="Roles & permissions" description="Understand role groups, action permissions and the scope each role can access." />
      <PreviewNotice />
      <div className="platform-callout"><strong>Security rule</strong><p>Showing or hiding a control in the interface does not grant access. Every action, institution scope and workflow state must be checked by the server.</p></div>
      <section className="platform-panel"><div className="platform-section__heading"><div><Kicker>ROLE MODEL</Kicker><h2>Permission groups</h2></div><span className="platform-muted">71 standard roles · V4.0 baseline</span></div><div className="platform-role-grid">{groups.map((group) => <article className="platform-role-card" key={group.title}><span className="platform-role-card__icon">◇</span><h3>{group.title}</h3><p>{group.detail}</p><span>{group.count}</span><Button variant="secondary" size="sm" disabled>Inspect permissions</Button></article>)}</div></section>
    </>
  );
}

function Audit() {
  return (
    <>
      <PageHeading eyebrow="SECURITY & OVERSIGHT" title="Audit activity" description="A review trail for important account, access and institution changes." />
      <PreviewNotice />
      <section className="platform-panel platform-table-panel"><div className="platform-table-toolbar"><div><h2>Recent events</h2><p>Illustrative entries · not a live audit log</p></div><Button variant="secondary" disabled>Filter events</Button></div><div className="platform-table-scroll"><table className="platform-table"><thead><tr><th>Activity</th><th>Performed by</th><th>Related record</th><th>Time</th><th>Result</th></tr></thead><tbody>{auditEvents.map((event) => <tr key={event.action}><td><strong>{event.action}</strong></td><td>{event.actor}</td><td>{event.target}</td><td>{event.time}</td><td><Status value={event.result} /></td></tr>)}</tbody></table></div></section>
      <div className="platform-callout"><strong>Audit integrity</strong><p>Production audit entries must be written by trusted backend services and must not be editable from this screen.</p></div>
    </>
  );
}

function Settings() {
  const settings = [
    ["Institution onboarding", "Controls for the institution setup process", "Not connected"],
    ["Identity provider", "Keycloak connection and identity mapping", "Integration pending"],
    ["Session security", "Session lifetime and account protection policies", "Not connected"],
    ["Audit retention", "Retention and access policy for audit records", "Decision required"],
  ];
  return (
    <>
      <PageHeading eyebrow="PLATFORM CONFIGURATION" title="Platform settings" description="Review the configuration areas required to operate EDU 360 securely." />
      <PreviewNotice />
      <section className="platform-panel"><Kicker>CONFIGURATION AREAS</Kicker><h2>Platform controls</h2><div className="platform-settings-list">{settings.map(([title, description, status]) => <div className="platform-setting" key={title}><div><strong>{title}</strong><p>{description}</p></div><Status value={status} /><Button variant="secondary" size="sm" disabled>Configure</Button></div>)}</div></section>
    </>
  );
}

export default function PlatformConsolePage() {
  const location = useLocation();
  const screen: Screen = location.pathname.endsWith("/institutions") ? "institutions"
    : location.pathname.endsWith("/users") ? "users"
      : location.pathname.endsWith("/access") ? "access"
        : location.pathname.endsWith("/audit") ? "audit"
          : location.pathname.endsWith("/settings") ? "settings" : "overview";
  const active = navigation.find((item) => item.id === screen) ?? navigation[0];
  return (
    <div className="platform-console">
      <div className="platform-breadcrumb"><span>EDU 360</span><span>/</span><strong>Platform workspace</strong><span>/</span><strong>{active.label}</strong></div>
      <nav className="platform-tabs" aria-label="Platform workspace sections">
        {navigation.map((item) => <NavLink end={item.id === "overview"} key={item.id} to={item.path} className={({ isActive }) => "platform-tab" + (isActive ? " is-active" : "")}><span aria-hidden="true">{item.icon}</span>{item.label}</NavLink>)}
      </nav>
      <main className="platform-page-content">
        {screen === "overview" && <Overview />}
        {screen === "institutions" && <Institutions />}
        {screen === "users" && <Users />}
        {screen === "access" && <Access />}
        {screen === "audit" && <Audit />}
        {screen === "settings" && <Settings />}
      </main>
      <p className="platform-footnote">EDU 360 · Sprint 1 interface foundation · Preview data only</p>
    </div>
  );
}
