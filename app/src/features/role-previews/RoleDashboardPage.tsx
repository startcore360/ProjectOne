import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Icon } from "../../components/ui/Icon";
import {
  roleDashboardCatalog,
  roleDashboardByCode,
  familyCopy,
  familyMetrics,
  roleOverrides,
  NO_HERO_ROLES,
} from "./roleDashboardCatalog";
import { StudentDashboard } from "../dashboard/pages/DashboardPage";
import { RoleCard, RoleHero, RoleDirectoryTools } from "./components";

function RoleEmptyState({ title, text }: { title: string; text: string }) {
  return (
    <div className="role-preview-empty">
      <span>
        <Icon name="book" size={18} />
      </span>
      <div>
        <strong>{title}</strong>
        <p>{text}</p>
      </div>
    </div>
  );
}

export default function RoleDashboardPage() {
  const { roleCode } = useParams();
  const [query, setQuery] = useState("");
  const [level, setLevel] = useState("All");
  const role = roleDashboardByCode(roleCode);
  const filtered = useMemo(
    () =>
      roleDashboardCatalog.filter(
        (item) =>
          (level === "All" || item.level === level) &&
          (item.name.toLowerCase().includes(query.toLowerCase()) ||
            item.code.toLowerCase().includes(query.toLowerCase()))
      ),
    [query, level]
  );

  if (roleCode && !role) {
    return (
      <section className="role-preview-page">
        <h1>Role not found</h1>
        <Link to="/role-previews">Browse all role dashboards</Link>
      </section>
    );
  }

  if (!role) {
    return (
      <section className="role-preview-page">
        <header className="role-preview-heading">
          <div>
            <p className="student-eyebrow">EDU 360 · V4.0 ROLE CATALOGUE</p>
            <h1>Dashboard experiences for every role.</h1>
            <p>
              71 source-defined roles, organised into consistent dashboard families. Each role has its own
              purpose and work context.
            </p>
          </div>
          <span className="role-preview-count">
            71 <small>roles</small>
          </span>
        </header>
        <RoleDirectoryTools
          query={query}
          onQueryChange={setQuery}
          level={level}
          onLevelChange={setLevel}
        />
        <div className="role-directory-grid">
          {filtered.map((item) => (
            <RoleCard key={item.code} role={item} />
          ))}
        </div>
        <p className="role-preview-footnote">
          Design preview only. Standard-role templates are not active assignments and do not grant access. Live
          permissions remain enforced by the server for the signed-in role, scope and record state.
        </p>
      </section>
    );
  }

  if (role.family === "student") {
    return (
      <section className="role-preview-page">
        <div className="role-preview-student-head">
          <span className="role-preview-tag">DESIGN PREVIEW · {role.name}</span>
          <Link to="/role-previews" className="student-text-link">
            All roles <Icon name="more" size={15} />
          </Link>
        </div>
        <StudentDashboard name="Student" />
      </section>
    );
  }

  const content =
    roleOverrides[role.code] ?? {
      ...familyCopy[role.family],
      title: `${role.name} dashboard`,
      focus: role.purpose,
      metrics: familyMetrics[role.family],
    };
  const showHero = !NO_HERO_ROLES.has(role.code);

  return (
    <section className="role-preview-page">
      {showHero ? (
        <RoleHero
          level={role.level}
          code={role.code}
          name={role.name}
          focus={content.focus}
        />
      ) : (
        <div className="role-preview-banner">
          <div>
            <p className="student-eyebrow">
              GOOD MORNING · {role.level.toUpperCase()} · {role.code}
            </p>
            <h1>{role.name}</h1>
            <p>{role.purpose}</p>
          </div>
          <span className="role-preview-tag">DESIGN PREVIEW</span>
        </div>
      )}
      <div className="role-preview-intro">
        <div>
          <p className="student-eyebrow">ROLE DASHBOARD</p>
          <h2>{content.title}</h2>
          <p>{content.focus}</p>
        </div>
        <Link to="/role-previews" className="student-text-link">
          All roles <Icon name="more" size={15} />
        </Link>
      </div>
      <section className="role-preview-queue student-panel" id="role-queue">
        <header className="student-panel__header">
          <div>
            <p className="student-eyebrow">YOUR ASSIGNED WORK</p>
            <h2>Work queue</h2>
          </div>
          <span className="role-preview-tag">ROLE & SCOPE AWARE</span>
        </header>
        <RoleEmptyState
          title="Your assigned work will appear here"
          text={
            "Only tasks assigned to " +
            role.name +
            " within its authorised scope will be shown. No sample tasks are presented as live records."
          }
        />
      </section>
      <div className="role-preview-metrics">
        {content.metrics.map((label, i) => (
          <article className="role-preview-metric" key={label}>
            <span>{label}</span>
            <strong>—</strong>
            <small>{i === 0 ? "Awaiting connected data" : "No live records loaded"}</small>
          </article>
        ))}
      </div>
      <div className="role-preview-panels">
        {content.panels.map(([title, eyebrow, description], i) => (
          <article className="student-panel" key={title}>
            <header className="student-panel__header">
              <div>
                <p className="student-eyebrow">{eyebrow}</p>
                <h2>{title}</h2>
              </div>
              <span className="student-panel__chevron">
                <Icon name={i === 1 ? "clock" : i === 2 ? "chart" : "home"} size={17} />
              </span>
            </header>
            <RoleEmptyState
              title={`${title} will appear here`}
              text={
                description +
                ". Live records will be shown when the relevant module and role-scoped API are connected."
              }
            />
          </article>
        ))}
      </div>
      <div className="role-preview-note">
        <Icon name="shield" size={17} />
        <p>
          This is a visual dashboard specification preview based on the role catalogue. It does not activate
          this role, switch the signed-in session, or bypass action-level permissions, tenant scope, approval
          chains or record-state rules.
        </p>
      </div>
    </section>
  );
}
