import { useAppSelector } from "../../../store/hooks";
import { Icon } from "../../../components/ui/Icon";

function Metric({ label, value, detail, icon }: { label: string; value: string; detail: string; icon: "calendar" | "file" | "chart" }) {
  return <article className="student-metric"><span className="student-metric__icon"><Icon name={icon} size={18} /></span><div><p>{label}</p><strong>{value}</strong><small>{detail}</small></div></article>;
}

function Panel({ id, title, eyebrow, children }: { id: string; title: string; eyebrow: string; children: React.ReactNode }) {
  return <section className="student-panel" id={id}><header className="student-panel__header"><div><p className="student-eyebrow">{eyebrow}</p><h2>{title}</h2></div><span className="student-panel__chevron" aria-hidden="true"><Icon name="arrow-up-right" size={17} /></span></header>{children}</section>;
}

function QuietEmpty({ title, description }: { title: string; description: string }) {
  return <div className="student-empty"><span className="student-empty__mark"><Icon name="book" size={20} /></span><div><strong>{title}</strong><p>{description}</p></div></div>;
}

function StudentDashboard({ name }: { name: string }) {
  return <div className="student-dashboard">
    <div className="student-breadcrumb"><span>Workspace</span><span aria-hidden="true">/</span><strong>Overview</strong></div>
    <section className="student-welcome">
      <div className="student-welcome__copy"><p className="student-eyebrow">YOUR LEARNING SPACE</p><h1>Good morning, {name}.</h1><p>A little clarity for the day ahead. Focus on one good next step.</p><a href="#student-plan" className="student-text-link">View your day <Icon name="arrow-up-right" size={16} /></a></div>
      <div className="student-welcome__aside"><div className="student-streak__icon"><Icon name="flame" size={22} /></div><div><p className="student-eyebrow">LEARNING STREAK</p><strong>Start your streak</strong><span>Build a steady rhythm, one learning day at a time.</span></div><div className="student-streak__days" aria-label="Your weekly streak will appear when activity is connected"><i/><i/><i/><i/><i/><i/><i/></div><small>Weekly activity · awaiting connection</small></div>
    </section>
    <section className="student-section" aria-labelledby="student-glance"><div className="student-section__heading"><div><p className="student-eyebrow">AT A GLANCE</p><h2 id="student-glance">Your progress</h2></div><span className="student-muted">Live academic data will appear here</span></div>
      <div className="student-metrics"><Metric label="Attendance" value="—" detail="Not connected yet" icon="calendar" /><Metric label="Assignments" value="—" detail="No live tasks loaded" icon="file" /><Metric label="Published average" value="—" detail="Published results only" icon="chart" /></div>
    </section>
    <div className="student-content-grid">
      <Panel id="student-plan" title="Your day" eyebrow="TODAY · TIMETABLE"><QuietEmpty title="Your timetable will appear here" description="When your institution connects timetable data, you’ll see upcoming classes, rooms and teachers in this space." /></Panel>
      <Panel id="student-focus" title="Next small steps" eyebrow="YOUR PRIORITIES"><QuietEmpty title="Nothing waiting just yet" description="Assignments and other tasks that need your attention will be listed here." /></Panel>
      <Panel id="student-updates" title="From your campus" eyebrow="NOTICES & UPDATES"><QuietEmpty title="No new notices" description="School announcements and useful updates will appear here." /></Panel>
      <Panel id="student-results" title="Recent results" eyebrow="ASSESSMENT"><QuietEmpty title="Results will appear here" description="Only published results will be shown in your learning overview." /></Panel>
    </div>
    <p className="student-data-note"><span aria-hidden="true">i</span> Your dashboard shows information available to your signed-in account. No sample scores, attendance figures or task counts are displayed.</p>
  </div>;
}

function WorkspaceDashboard({ name, userType }: { name: string; userType: string }) {
  const labels: Record<string, { title: string; description: string; scope: string }> = {
    STAFF: { title: "Your work, in one place.", description: "Your classes, responsibilities and institution updates will appear here as connected services become available.", scope: "Staff workspace" },
    GUARDIAN: { title: "A clearer view of learning.", description: "Linked student progress, attendance, notices and fee information will appear here when available to your account.", scope: "Family workspace" },
    APPLICANT: { title: "Your application journey.", description: "Application status, required documents and next steps will appear here when admissions services are connected.", scope: "Applicant workspace" },
    GOVERNANCE: { title: "Governance at a glance.", description: "Items assigned to your governance role will appear here, within your authorised scope.", scope: "Governance workspace" },
    SYSTEM: { title: "Your workspace.", description: "Your assigned services and permitted actions will appear here when connected.", scope: "System workspace" },
  };
  const content = labels[userType] ?? { title: "Your workspace.", description: "Your assigned services and permitted actions will appear here as they become available.", scope: "Institution workspace" };
  return <div className="workspace-dashboard"><div className="student-breadcrumb"><span>Workspace</span><span aria-hidden="true">/</span><strong>Overview</strong></div><section className="workspace-welcome"><p className="student-eyebrow">{content.scope.toUpperCase()}</p><h1>Welcome, {name}.</h1><h2>{content.title}</h2><p>{content.description}</p></section><section className="student-panel workspace-state"><span className="student-panel__chevron"><Icon name="grid" size={20}/></span><h2>Your workspace is ready</h2><p>Live information will appear here when the relevant institution services are connected. Available actions remain controlled by your assigned role and permissions.</p></section></div>;
}

export default function DashboardPage() {
  const user = useAppSelector((s) => s.auth.user);
  const name = user?.displayName?.trim() || "there";
  if (user?.userType === "STUDENT") return <StudentDashboard name={name} />;
  return <WorkspaceDashboard name={name} userType={user?.userType ?? "SYSTEM"} />;
}
