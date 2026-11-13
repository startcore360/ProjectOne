import { Icon } from "../../../components/ui/Icon";
import { PageHeader, Kpi } from "../../../design-system/components";
import {
  roleDashboardByCode,
  roleOverrides,
  familyCopy,
  familyMetrics,
  NO_HERO_ROLES,
} from "../../role-previews/roleDashboardCatalog";

export interface RoleWorkspaceProps {
  name: string;
  roleCode: string;
}

export function RoleWorkspace({ name, roleCode }: RoleWorkspaceProps) {
  const role = roleDashboardByCode(roleCode);
  if (!role) return null;
  const content = roleOverrides[role.code] ?? {
    ...familyCopy[role.family],
    title: `${role.name} dashboard`,
    focus: role.purpose,
    metrics: familyMetrics[role.family],
  };
  const showHero = !NO_HERO_ROLES.has(role.code);
  return (
    <div className="workspace-dashboard">
      {showHero ? (
        <section className="hero" aria-labelledby="workspace-hero-title">
          <div>
            <p className="kicker">{role.name}</p>
            <h2 className="hero__title" id="workspace-hero-title">
              Good morning, {name}.
            </h2>
            <p>{content.focus}</p>
            <a className="hero__cta" href="#workspace-queue">
              View work queue status
              <Icon name="send" size={16} />
            </a>
          </div>
          <div className="hero__art" aria-hidden="true">
            <span className="halo" />
            <span className="leaf" />
            <span className="star" />
          </div>
        </section>
      ) : (
        <PageHeader title={`Good morning, ${name}.`} description={role.name} />
      )}
      <section className="student-section" aria-labelledby="workspace-glance">
        <div className="student-section__heading">
          <div>
            <p className="student-eyebrow">AT A GLANCE</p>
            <h2 id="workspace-glance">{content.title}</h2>
          </div>
        </div>
        <div className="kpi-grid">
          {content.metrics.map((label, i) => (
            <Kpi
              key={label}
              label={label}
              value="—"
              context={i === 0 ? "Awaiting connected data" : "No live records loaded"}
              icon={i === 1 ? "clock" : i === 2 ? "chart" : "home"}
            />
          ))}
        </div>
      </section>
      <section className="student-panel workspace-state" id="workspace-queue">
        <span className="student-panel__chevron">
          <Icon name="home" size={20} />
        </span>
        <h2>Work queue is not connected yet</h2>
        <p>
          Only tasks assigned to {role.name} within its authorised scope will be shown. No sample
          tasks are presented as live records. Available actions remain controlled by your assigned
          role and permissions.
        </p>
      </section>
    </div>
  );
}
