import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Icon } from "../../components/ui/Icon";
import { roleDashboardCatalog, roleDashboardByCode, type DashboardFamily, type RoleProfile } from "./roleDashboardCatalog";

const familyCopy: Record<DashboardFamily, { title: string; focus: string; panels: [string,string,string][] }> = {
  platform: { title: "Platform operations", focus: "Service health, tenant operations and platform-level work.", panels: [["Tenant portfolio","TENANTS & SERVICES","Tenant lifecycle, plan status and service health"],["Operational queue","NEEDS ATTENTION","Assigned support, release and platform tasks"],["Platform assurance","CONTROL & AUDIT","Security, backups, usage and audit evidence"]] },
  leadership: { title: "Institution leadership", focus: "Institution-wide performance, decision queues and accountable oversight.", panels: [["Institution pulse","PERFORMANCE","Academic, attendance, finance and people summaries in authorised scope"],["Approval desk","DECISIONS","Pending approvals, delegated items and due dates"],["Institution priorities","FOLLOW-THROUGH","Operational risks, notices and upcoming milestones"]] },
  academic: { title: "Academic administration", focus: "Academic delivery, examination readiness and department-level coordination.", panels: [["Academic pulse","DELIVERY","Programmes, subjects, timetable and academic progress"],["Examination desk","ASSESSMENT","Schedules, marks workflow and result publication stages"],["Department follow-up","ACADEMIC OPERATIONS","Faculty allocation, syllabus and unresolved academic items"]] },
  teaching: { title: "Teaching workspace", focus: "Assigned classes, teaching preparation, attendance and assessment work.", panels: [["My teaching","CLASSES & TIMETABLE","Assigned classes, subjects and next teaching sessions"],["Assessment queue","MARKS & ASSIGNMENTS","Draft marks, submissions, reviews and evaluation tasks"],["Learner follow-up","CLASS SUPPORT","Attendance exceptions, student leave and parent communication"]] },
  finance: { title: "Finance workspace", focus: "Collections, reconciliation, finance approvals and payroll tasks within scope.", panels: [["Finance position","COLLECTIONS & LEDGER","Receipts, dues, ledger and reconciliation summaries"],["Payment workbench","TRANSACTIONS","Exceptions, concessions, receipts and payment follow-up"],["Finance approvals","CONTROL QUEUE","Approval items and period controls assigned to this role"]] },
  people: { title: "People operations", focus: "Staff records, leave administration and workforce processes.", panels: [["Workforce pulse","STAFF & RECORDS","Staff records, documents and team status"],["Leave desk","LEAVE WORKFLOW","Applications, verification, balances and delegated approvals"],["People follow-up","HR OPERATIONS","Assigned HR tasks and upcoming workforce milestones"]] },
  operations: { title: "Operations workspace", focus: "Assigned operational services, service requests and day-to-day execution.", panels: [["Service overview","OPERATIONS","Assigned service areas, workload and operating status"],["Work queue","TASKS & REQUESTS","Open work, exceptions and requests assigned to this role"],["Daily coordination","SCHEDULE & FOLLOW-UP","Upcoming schedules, notices and operational hand-offs"]] },
  governance: { title: "Governance and assurance", focus: "Controlled review, compliance evidence and accountable decision-making.", panels: [["Assurance overview","CONTROL ENVIRONMENT","Assigned controls, evidence and compliance status"],["Review queue","PENDING REVIEW","Audit, privacy, legal and governance items in authorised scope"],["Evidence & history","TRACEABILITY","Decision history, findings and records available to this role"]] },
  student: { title: "Your learning space", focus: "A focused student experience for progress, daily learning and next steps.", panels: [["Your day","TIMETABLE","Classes, rooms and teachers"],["Next small steps","ASSIGNMENTS","Assignments, deadlines and study goals"],["From your campus","NOTICES & UPDATES","School announcements and useful updates"]] },
  family: { title: "Family workspace", focus: "A clear view of linked learner information and family actions.", panels: [["Learner overview","PROGRESS","Attendance, published results and learning updates"],["Family actions","FEES & LEAVE","Fee information, receipts and leave requests"],["School connection","MESSAGES & NOTICES","Teacher messages, school notices and upcoming events"]] },
  admissions: { title: "Admissions workspace", focus: "Application progress, required information and next steps.", panels: [["Application journey","STATUS","Application progress and decision updates"],["Documents & details","REQUIREMENTS","Required documents and profile completion"],["Next steps","ADMISSIONS TASKS","Submission actions and institution communications"]] }
};

const familyMetrics: Record<DashboardFamily,string[]> = {
  platform:["Active tenants","Open escalations","Release readiness"],
  leadership:["Institution overview","Pending approvals","Priority follow-ups"],
  academic:["Academic coverage","Exam milestones","Department actions"],
  teaching:["Assigned classes","Assessment tasks","Learner follow-ups"],
  finance:["Collection position","Reconciliation items","Approval queue"],
  people:["Staff records","Leave requests","HR follow-ups"],
  operations:["Service workload","Open requests","Today's schedule"],
  governance:["Controls in scope","Reviews due","Open findings"],
  student:["Learning streak","Weekly activity","Study goals"],
  family:["Linked learners","Items needing attention","Upcoming dates"],
  admissions:["Application stage","Documents outstanding","Next required step"]
};

function EmptyState({ title, text }: { title:string; text:string }) {
  return <div className="role-preview-empty"><span><Icon name="book" size={18}/></span><div><strong>{title}</strong><p>{text}</p></div></div>;
}

function RoleCard({ role }: { role: RoleProfile }) {
  return <Link className="role-directory-card" to={`/role-preview/${role.code}`}>
    <span className="role-directory-card__level">{role.level}</span>
    <h3>{role.name}</h3><p>{role.purpose}</p>
    <span className="role-directory-card__open">Open dashboard <Icon name="more" size={15}/></span>
  </Link>;
}

export default function RoleDashboardPage() {
  const { roleCode } = useParams();
  const [query,setQuery] = useState("");
  const [level,setLevel] = useState("All");
  const role = roleDashboardByCode(roleCode);
  const filtered = useMemo(() => roleDashboardCatalog.filter(item => (level === "All" || item.level === level) && (item.name.toLowerCase().includes(query.toLowerCase()) || item.code.toLowerCase().includes(query.toLowerCase()))),[query,level]);
  if (roleCode && !role) return <section className="role-preview-page"><h1>Role not found</h1><Link to="/role-previews">Browse all role dashboards</Link></section>;
  if (!role) return <section className="role-preview-page">
    <div className="student-breadcrumb"><span>Design review</span><span>/</span><strong>Role dashboards</strong></div>
    <header className="role-preview-heading"><div><p className="student-eyebrow">EDU 360 · V4.0 ROLE CATALOGUE</p><h1>Dashboard experiences for every role.</h1><p>71 source-defined roles, organised into consistent dashboard families. Each role has its own purpose and work context.</p></div><span className="role-preview-count">71 <small>roles</small></span></header>
    <div className="role-directory-tools"><label className="role-search"><Icon name="search" size={17}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search role or role code" /></label><select value={level} onChange={e=>setLevel(e.target.value)} aria-label="Filter by organisational level"><option>All</option><option>Platform</option><option>Tenant</option><option>University</option><option>College</option><option>School</option><option>External</option></select></div>
    <div className="role-directory-grid">{filtered.map(item=><RoleCard key={item.code} role={item}/>)}</div>
    <p className="role-preview-footnote">Design preview only. Standard-role templates are not active assignments and do not grant access. Live permissions remain enforced by the server for the signed-in role, scope and record state.</p>
  </section>;
  const content = familyCopy[role.family];
  return <section className="role-preview-page">
    <div className="student-breadcrumb"><Link to="/role-previews">All role dashboards</Link><span>/</span><strong>{role.name}</strong></div>
    <div className="role-preview-banner"><div><p className="student-eyebrow">{role.level.toUpperCase()} · {role.code}</p><h1>{role.name}</h1><p>{role.purpose}</p></div><span className="role-preview-tag">DESIGN PREVIEW</span></div>
    <div className="role-preview-intro"><div><p className="student-eyebrow">ROLE DASHBOARD</p><h2>{content.title}</h2><p>{content.focus}</p></div><Link to="/role-previews" className="student-text-link">All roles <Icon name="more" size={15}/></Link></div>
    <div className="role-preview-metrics">{familyMetrics[role.family].map((label,i)=><article className="role-preview-metric" key={label}><span>{label}</span><strong>—</strong><small>{i===0?"Awaiting connected data":"No live records loaded"}</small></article>)}</div>
    <div className="role-preview-panels">{content.panels.map(([title,eyebrow,description],i)=><article className="student-panel" key={title}><header className="student-panel__header"><div><p className="student-eyebrow">{eyebrow}</p><h2>{title}</h2></div><span className="student-panel__chevron"><Icon name={i===1?"clock":i===2?"chart":"home"} size={17}/></span></header><EmptyState title={`${title} will appear here`} text={description + ". Live records will be shown when the relevant module and role-scoped API are connected."}/></article>)}</div>
    <div className="role-preview-note"><Icon name="shield" size={17}/><p>This is a visual dashboard specification preview based on the role catalogue. It does not activate this role, switch the signed-in session, or bypass action-level permissions, tenant scope, approval chains or record-state rules.</p></div>
  </section>;
}
