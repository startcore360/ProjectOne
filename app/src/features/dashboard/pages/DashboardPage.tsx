import { Kicker } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
// Placeholder cards shown on the dashboard. They're fixed text for now — no
// real attendance/course/score data is wired up to this screen yet.
const items = [
  {
    n: "01",
    title: "Your learning workspace",
    body: "Your courses, activities and updates will appear here as connected modules become available.",
  },
  {
    n: "02",
    title: "Your institution",
    body: "Your workspace is scoped by the role and permissions assigned by your institution.",
  },
  {
    n: "03",
    title: "Stay up to date",
    body: "Notices, tasks and items that need your attention will be surfaced in this space.",
  },
];
// The home page a user sees after logging in. Shows their name and role,
// plus placeholder cards (see above) until real data is connected.
export default function DashboardPage() {
  const { user } = useAppSelector((s) => s.auth);
  const name = user?.name?.trim() || "there";
  const role = user?.role?.replaceAll("_", " ") || "Member";
  return (
    <div className="dashboard-page">
      <div className="dashboard-breadcrumb">
        <span>Workspace</span>
        <span>/</span>
        <strong>Overview</strong>
      </div>
      <section className="dashboard-hero">
        <div className="dashboard-hero__content">
          <p className="dashboard-hero__kicker">YOUR EDU 360 WORKSPACE</p>
          <h1>Welcome, {name}.</h1>
          <p>A clear place to see what matters and take your next step.</p>
          <span className="dashboard-role">{role}</span>
        </div>
        <div className="dashboard-hero__art" aria-hidden="true">
          <span />
          <i />
          <b />
        </div>
      </section>
      <section className="dashboard-section" aria-labelledby="overview-heading">
        <div className="dashboard-section__heading">
          <div>
            <Kicker>AT A GLANCE</Kicker>
            <h2 id="overview-heading">Your overview</h2>
          </div>
          <p>Information available to your account</p>
        </div>
        <div className="dashboard-overview-grid">
          {items.map((item) => (
            <article className="dashboard-info-card" key={item.n}>
              <span className="dashboard-info-card__mark">{item.n}</span>
              <h3>{item.title}</h3>
              <p>{item.body}</p>
            </article>
          ))}
        </div>
      </section>
      <section className="dashboard-note" aria-label="Workspace status">
        <span className="dashboard-note__icon">i</span>
        <div>
          <h2>Your workspace is ready</h2>
          <p>
            Live course, attendance, performance and task summaries will appear here when their
            services are connected. No sample figures are shown.
          </p>
        </div>
      </section>
    </div>
  );
}
