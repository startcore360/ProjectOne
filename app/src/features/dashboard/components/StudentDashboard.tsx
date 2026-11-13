import { useEffect, useState } from "react";
import { Icon } from "../../../components/ui/Icon";
import { PageHeader, Kpi } from "../../../design-system/components";
import { DashboardPanel, QuietEmpty } from "./DashboardPanel";
import { selfServiceApi, type NoticeItem } from "../../self-service/api/selfServiceApi";

export interface StudentDashboardProps {
  name: string;
  tenantId?: string;
}

export function StudentDashboard({ name, tenantId }: StudentDashboardProps) {
  const [notices, setNotices] = useState<NoticeItem[]>([]);
  const [noticesLoading, setNoticesLoading] = useState(Boolean(tenantId));
  const [noticesError, setNoticesError] = useState(!tenantId);

  useEffect(() => {
    let active = true;
    if (!tenantId) return () => { active = false; };
    selfServiceApi.getNotices(tenantId)
      .then((items) => { if (active) setNotices(Array.isArray(items) ? items.slice(0, 3) : []); })
      .catch(() => { if (active) setNoticesError(true); })
      .finally(() => { if (active) setNoticesLoading(false); });
    return () => { active = false; };
  }, [tenantId]);

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
            View timetable status
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
            context="Awaiting published results"
            icon="chart"
          />
          <Kpi label="To-do list" value="—" context="Awaiting assignment service" icon="book" />
          <Kpi label="Fee status" value="—" context="Awaiting finance service" icon="wallet" />
        </div>
      </section>

      <div className="student-content-grid">
        <DashboardPanel
          id="student-plan"
          title="Your timetable"
          eyebrow="TODAY · WHAT’S AHEAD"
          
        >
          <QuietEmpty
            title="Your classes will appear here"
            description="See today’s periods, subjects, rooms and teachers when your institution timetable is connected."
          />
        </DashboardPanel>
        <DashboardPanel
          id="student-focus"
          title="Next small steps"
          eyebrow="CLEAR PRIORITIES, NO CLUTTER"
          
        >
          <QuietEmpty
            title="Assignments are not connected yet"
            description="Published assignments and due dates will appear here when the assignment service is available."
          />
        </DashboardPanel>
        <DashboardPanel
          id="student-updates"
          title="From your campus"
          eyebrow="USEFUL UPDATES, IN ONE PLACE"
          action={{ label: "All notices", href: "/self-service" }}
        >
          {noticesLoading ? (
            <p role="status">Loading published campus notices…</p>
          ) : noticesError ? (
            <p role="status">{tenantId ? "Campus notices could not be loaded. Open Self-Service to retry." : "Sign in as a student to view campus notices."}</p>
          ) : notices.length === 0 ? (
            <QuietEmpty title="No published campus notices" description="There are no published notices available to your account right now." />
          ) : (
            <div className="space-y-3">
              {notices.map((notice) => (
                <article key={notice.noticeId} className="self-service-card self-service-card--notice">
                  <strong>{notice.title}</strong>
                  <p>{notice.body}</p>
                  <span className="self-service-card__meta">{notice.publishedAt ? new Date(notice.publishedAt).toLocaleDateString() : "Recently published"}</span>
                </article>
              ))}
            </div>
          )}
        </DashboardPanel>
        <DashboardPanel
          id="student-results"
          title="Recent results"
          eyebrow="LATEST PUBLISHED ASSESSMENTS"
          
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
