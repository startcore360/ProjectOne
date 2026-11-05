import { useAppSelector } from "../../../store/hooks";
import type { ReactNode } from "react";
import { Icon } from "../../../components/ui/Icon";

function Metric({ label, value, detail, icon }: { label: string; value: string; detail: string; icon: "calendar" | "book" | "chart" | "clock" }) {
  return <article className="student-metric"><span className="student-metric__icon"><Icon name={icon} size={18} /></span><div><p>{label}</p><strong>{value}</strong><small>{detail}</small></div></article>;
}

function Panel({ id, title, eyebrow, children }: { id: string; title: string; eyebrow: string; children: ReactNode }) {
  return <section className="student-panel" id={id}><header className="student-panel__header"><div><p className="student-eyebrow">{eyebrow}</p><h2>{title}</h2></div><span className="student-panel__chevron" aria-hidden="true"><Icon name="more" size={17} /></span></header>{children}</section>;
}

function QuietEmpty({ title, description }: { title: string; description: string }) {
  return <div className="student-empty"><span className="student-empty__mark"><Icon name="book" size={20} /></span><div><strong>{title}</strong><p>{description}</p></div></div>;
}

function StudentDashboard({ name }: { name: string }) {
  return <div className="student-dashboard">
    <div className="student-breadcrumb"><span>Workspace</span><span aria-hidden="true">/</span><strong>Overview</strong></div>
    <section className="student-welcome">
      <div className="student-welcome__copy">
        <p className="student-eyebrow">YOUR WEEK, IN BALANCE</p>
        <h1>Good morning, {name}.</h1>
        <h2 className="student-welcome__headline">Progress isn’t a race.<br/>It’s a rhythm.</h2>
        <p>A little clarity for the day ahead. Take it one step at a time.</p>
        <a href="#student-plan" className="student-text-link">View today’s plan <Icon name="more" size={16}/></a>
      </div>
      <aside className="student-welcome__aside" aria-label="Learning streak">
        <div className="student-streak__icon"><Icon name="chart" size={22}/></div>
        <div><p className="student-eyebrow">LEARNING STREAK</p><strong>Start your rhythm</strong><span>Small, consistent study sessions add up.</span></div>
        <div className="student-streak__days" aria-label="Weekly activity is not connected yet"><i/><i/><i/><i/><i/><i/><i/></div>
        <small>Weekly activity · connect your learning activity to begin</small>
      </aside>
    </section>

    <section className="student-section" aria-labelledby="student-glance">
      <div className="student-section__heading"><div><p className="student-eyebrow">AT A GLANCE</p><h2 id="student-glance">Your progress</h2></div><span className="student-muted">A clear view of this term</span></div>
      <div className="student-metrics">
        <Metric label="Attendance" value="—" detail="Term-to-date · awaiting data" icon="calendar"/>
        <Metric label="Assignments to submit" value="—" detail="Published assignments only" icon="book"/>
        <Metric label="Published average" value="—" detail="Published results only" icon="chart"/>
        <Metric label="Leave requests" value="—" detail="Your pending requests" icon="clock"/>
      </div>
    </section>

    <div className="student-content-grid">
      <Panel id="student-plan" title="Your timetable" eyebrow="TODAY · WHAT’S AHEAD">
        <div className="student-week-strip" aria-label="Weekly timetable preview"><span>MON</span><span>TUE</span><span>WED</span><span>THU</span><span>FRI</span></div>
        <QuietEmpty title="Your classes will appear here" description="See today’s periods, subjects, rooms and teachers when your institution timetable is connected."/>
      </Panel>
      <Panel id="student-focus" title="Next small steps" eyebrow="CLEAR PRIORITIES, NO CLUTTER">
        <QuietEmpty title="You’re all caught up for now" description="Published assignments, due dates and tasks that need your attention will appear here."/>
      </Panel>
      <Panel id="student-updates" title="From your campus" eyebrow="USEFUL UPDATES, IN ONE PLACE">
        <QuietEmpty title="No new notices" description="School announcements, class updates and useful reminders will appear here."/>
      </Panel>
      <Panel id="student-results" title="Recent results" eyebrow="LATEST PUBLISHED ASSESSMENTS">
        <QuietEmpty title="Your results will appear here" description="Only results that have been approved and published will be shown."/>
      </Panel>
    </div>

    <div className="student-content-grid student-content-grid--lower">
      <Panel id="student-attendance" title="Attendance, at a glance" eyebrow="TERM TO DATE · ALL SUBJECTS">
        <div className="student-attendance-summary"><div className="student-attendance-ring" aria-hidden="true"><span>—</span></div><div><strong>Attendance data isn’t available yet</strong><p>When registers are connected, this view will show your attendance percentage and sessions attended.</p></div></div>
      </Panel>
      <Panel id="student-study" title="Your learning rhythm" eyebrow="STUDY HABITS">
        <QuietEmpty title="Your weekly streak starts here" description="Study goals and consistent learning activity will help you see your progress over time."/>
      </Panel>
    </div>
    <p className="student-data-note"><Icon name="info" size={16}/> Your dashboard shows information available to your signed-in account. No sample scores, attendance figures or assignment counts are presented as live data.</p>
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
  return <div className="workspace-dashboard"><div className="student-breadcrumb"><span>Workspace</span><span aria-hidden="true">/</span><strong>Overview</strong></div><section className="workspace-welcome"><p className="student-eyebrow">{content.scope.toUpperCase()}</p><h1>Welcome, {name}.</h1><h2>{content.title}</h2><p>{content.description}</p></section><section className="student-panel workspace-state"><span className="student-panel__chevron"><Icon name="home" size={20}/></span><h2>Your workspace is ready</h2><p>Live information will appear here when the relevant institution services are connected. Available actions remain controlled by your assigned role and permissions.</p></section></div>;
}

export default function DashboardPage() {
  const user = useAppSelector((s) => s.auth.user);
  const name = user?.displayName?.trim() || "there";
  if (user?.userType === "STUDENT") return <StudentDashboard name={name} />;
  return <WorkspaceDashboard name={name} userType={user?.userType ?? "SYSTEM"} />;
}
