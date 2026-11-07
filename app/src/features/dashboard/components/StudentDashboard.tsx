import { Icon } from "../../../components/ui/Icon";
import { PageHeader, Kpi } from "../../../design-system/components";
import { DashboardPanel, QuietEmpty } from "./DashboardPanel";

export interface StudentDashboardProps {
  name: string;
}

export function StudentDashboard({ name }: StudentDashboardProps) {
  return (
    <div className="student-dashboard">
      <PageHeader
        title={`Good morning, ${name}.`}
        description="A little clarity for the day ahead."
      />

      <section className="hero" aria-labelledby="student-hero-title">
        <div>
          <p className="kicker">Your week, in balance</p>
          <h2 className="hero__title" id="student-hero-title">
            Progress isn&rsquo;t a race.
            <br />
            It&rsquo;s a rhythm.
          </h2>
          <p>
            Take it one step at a time. Your classes and tasks for today will appear here once your
            institution timetable is connected.
          </p>
          <a className="hero__cta" href="#student-plan">
            View today&rsquo;s plan
            <Icon name="send" size={16} />
          </a>
        </div>
        <div className="hero__art" aria-hidden="true">
          <span className="halo" />
          <span className="leaf" />
          <span className="star" />
        </div>
      </section>

      <section className="student-section" aria-labelledby="student-glance">
        <div className="student-section__heading">
          <div>
            <p className="student-eyebrow">AT A GLANCE</p>
            <h2 id="student-glance">Your progress</h2>
          </div>
          <span className="student-muted">A clear view of this term</span>
        </div>
        <div className="kpi-grid">
          <Kpi label="Attendance" value="—" context="Term-to-date · awaiting data" icon="calendar" />
          <Kpi
            label="Current CGPA"
            value="—"
            context="Latest published academic record"
            icon="chart"
          />
          <Kpi label="To-do list" value="—" context="Tasks due soon" icon="book" />
          <Kpi label="Fee status" value="—" context="Current outstanding balance" icon="wallet" />
        </div>
      </section>

      <div className="student-content-grid">
        <DashboardPanel
          id="student-plan"
          title="Your timetable"
          eyebrow="TODAY · WHAT’S AHEAD"
          action={{ label: "Full calendar", href: "#student-plan" }}
        >
          <div className="student-week-strip" aria-label="Weekly timetable preview">
            <span>MON</span>
            <span>TUE</span>
            <span>WED</span>
            <span>THU</span>
            <span>FRI</span>
          </div>
          <QuietEmpty
            title="Your classes will appear here"
            description="See today’s periods, subjects, rooms and teachers when your institution timetable is connected."
          />
        </DashboardPanel>
        <DashboardPanel
          id="student-focus"
          title="Next small steps"
          eyebrow="CLEAR PRIORITIES, NO CLUTTER"
          action={{ label: "All tasks", href: "#student-focus" }}
        >
          <QuietEmpty
            title="You’re all caught up for now"
            description="Published assignments, due dates and tasks that need your attention will appear here."
          />
        </DashboardPanel>
        <DashboardPanel
          id="student-updates"
          title="From your campus"
          eyebrow="USEFUL UPDATES, IN ONE PLACE"
          action={{ label: "All notices", href: "#student-updates" }}
        >
          <QuietEmpty
            title="No new notices"
            description="School announcements, class updates and useful reminders will appear here."
          />
        </DashboardPanel>
        <DashboardPanel
          id="student-results"
          title="Recent results"
          eyebrow="LATEST PUBLISHED ASSESSMENTS"
          action={{ label: "All results", href: "#student-results" }}
        >
          <QuietEmpty
            title="Your results will appear here"
            description="Only results that have been approved and published will be shown."
          />
        </DashboardPanel>
      </div>

      <div className="student-content-grid student-content-grid--lower">
        <DashboardPanel
          id="student-attendance"
          title="Attendance, at a glance"
          eyebrow="TERM TO DATE · ALL SUBJECTS"
          action={{ label: "Attendance details", href: "#student-attendance" }}
        >
          <div className="student-attendance-summary">
            <div className="student-attendance-ring" aria-hidden="true">
              <span>—</span>
            </div>
            <div>
              <strong>Attendance data isn’t available yet</strong>
              <p>
                When registers are connected, this view will show your attendance percentage and
                sessions attended.
              </p>
            </div>
          </div>
        </DashboardPanel>
        <DashboardPanel id="student-study" title="Your learning rhythm" eyebrow="STUDY HABITS">
          <QuietEmpty
            title="Your weekly streak starts here"
            description="Study goals and consistent learning activity will help you see your progress over time."
          />
        </DashboardPanel>
      </div>
      <p className="student-data-note">
        <Icon name="info" size={16} /> Your dashboard shows information available to your signed-in
        account. No sample scores, attendance figures or assignment counts are presented as live
        data.
      </p>
    </div>
  );
}
