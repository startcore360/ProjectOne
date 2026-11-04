import { useMemo, useState, type ReactNode } from "react";
import { Badge } from "../../../components/ui";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, Kicker } from "../../../design-system/components";

const institutions = [
  { name: "Greenwood Public School", type: "School · Classes 1–12", status: "Active" },
  { name: "Northbridge College of Science", type: "College · 4 departments", status: "Active" },
  { name: "Northbridge School of Commerce", type: "College · setup in progress", status: "Pending" },
];

function Panel({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  return <section className="platform-panel"><header className="platform-panel__header"><div><h2>{title}</h2>{description && <p>{description}</p>}</div></header>{children}</section>;
}
function StateBadge({ status }: { status: string }) {
  return <Badge tone={status === "Active" ? "success" : status === "Pending" ? "warning" : "neutral"}>{status}</Badge>;
}
function Note() {
  return <p className="platform-sample-note"><span aria-hidden="true">ⓘ</span> Prototype reference data only. Changes are not connected to the institution API.</p>;
}

export function InstitutionsPage() {
  return <div className="platform-page"><PageHeader title="Institutions" description="Institutions within Northbridge University. Tenant creation and lifecycle remain with the platform team." /><Panel title="Institution register" description="2026–27 · University scope"><div className="platform-table-wrap"><table className="platform-table"><thead><tr><th>Institution</th><th>Type and structure</th><th>Setup</th></tr></thead><tbody>{institutions.map((institution) => <tr key={institution.name}><td><strong>{institution.name}</strong></td><td>{institution.type}</td><td><StateBadge status={institution.status} /></td></tr>)}</tbody></table></div></Panel><Note /></div>;
}

export function InstitutionUsersPage() {
  const [query, setQuery] = useState("");
  const [kind, setKind] = useState("All");
  const emptyMessage = query || kind !== "All" ? "No Northbridge University users match these filters." : "No Northbridge University user records are loaded in this prototype view.";
  return <div className="platform-page"><PageHeader title="Users & roles" description="Create institution profiles, assign roles and link guardians. Profile creation and role assignment are separate steps." actions={<Button disabled title="User management API is not connected">＋ Create user</Button>} /><Panel title="User register" description="University scope · the current prototype does not supply university user rows."><div className="platform-toolbar"><label className="platform-search"><span aria-hidden="true">⌕</span><span className="visually-hidden">Search</span><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search name, ID or email" /></label><label className="platform-select-label">Profile type <select value={kind} onChange={(e) => setKind(e.target.value)}><option>All</option><option>Student</option><option>Teacher</option><option>Parent</option></select></label></div><div className="platform-admin-empty"><span aria-hidden="true">♙</span><strong>{emptyMessage}</strong><p>When institution records are connected, this register will show only people within the selected university scope.</p></div></Panel><Note /></div>;
}

const roleRows = [
  { name: "University Admin", level: "University", type: "Standard" },
  { name: "College Admin", level: "College", type: "Standard" },
  { name: "School Admin", level: "School", type: "Standard" },
  { name: "Principal", level: "School", type: "Standard" },
  { name: "Teacher", level: "School", type: "Standard" },
  { name: "Accountant", level: "Institution", type: "Standard" },
  { name: "Governing Body Approver", level: "Governance", type: "Standard" },
];
export function RolesPermissionsPage() {
  const [query, setQuery] = useState("");
  const filtered = useMemo(() => roleRows.filter((role) => (role.name + role.level).toLowerCase().includes(query.toLowerCase())), [query]);
  return <div className="platform-page"><PageHeader title="Roles & permissions" description="Review standard roles and their permission scope. Standard roles are read-only; custom roles use actions from the approved Action List." /><Panel title="Role catalogue" description="Representative role names from the V4.0 prototype. This is not the complete 71-role catalogue."><div className="platform-toolbar"><label className="platform-search"><span aria-hidden="true">⌕</span><span className="visually-hidden">Search roles</span><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search role or level" /></label></div><div className="platform-table-wrap"><table className="platform-table"><thead><tr><th>Role</th><th>Level</th><th>Type</th><th>Configuration</th></tr></thead><tbody>{filtered.map((role) => <tr key={role.name}><td><strong>{role.name}</strong></td><td>{role.level}</td><td>{role.type}</td><td><Badge tone="neutral">Read-only template</Badge></td></tr>)}</tbody></table></div></Panel><div className="platform-policy-callout"><span aria-hidden="true">ⓘ</span><div><strong>Role assignment is a separate step</strong><p>Standard role definitions are read-only. Custom roles are assembled from approved Action List actions. A role is not assigned to a person merely because it exists.</p></div></div><Note /></div>;
}

export function MissingRolesPage() {
  return <div className="platform-page"><PageHeader title="Missing roles" description="V4.0 role roll-down: see who handles work when a defined role has no current holder." /><div className="platform-policy-callout"><span aria-hidden="true">ⓘ</span><div><strong>Decision V4-PD-01</strong><p>When a role is missing, approvals go to the head of the next level down. Other work goes to that level's administrator. Changes require an authorised role and a recorded reason.</p></div></div><Panel title="Default roll-down rules" description="Policy view from the prototype; no live role-holder assignments are loaded."><div className="platform-roll-grid"><article><Kicker>APPROVALS</Kicker><h3>Next-level leader</h3><p>Approval work moves to the head of the next level down. Approval rights cannot be withheld.</p></article><article><Kicker>OTHER WORK</Kicker><h3>Next-level administrator</h3><p>Other work moves to the next level's administrator. Where permitted, it may be withheld until the role is filled.</p></article></div></Panel><Panel title="Rules that cannot be overridden"><ul className="platform-rule-list"><li>Nobody approves their own request.</li><li>A preparer never approves their own work.</li><li>Students, parents, applicants and platform staff never act for an institution role.</li><li>Every action taken through roll-down must show “Acting for …” and be recorded in the audit log.</li></ul></Panel><Note /></div>;
}
