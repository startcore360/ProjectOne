import { Link } from "react-router-dom";
import { Icon } from "../../../components/ui/Icon";
import { PageHeader, Kpi, Panel } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { useNotifications } from "../../notifications/hooks/useNotifications";
import {
  roleDashboardByCode,
  roleOverrides,
  familyCopy,
  familyMetrics,
  familyQuickActions,
  NO_HERO_ROLES,
} from "../../role-previews/roleDashboardCatalog";
import { greeting, renderGreeting } from "../../../utils/greeting";

export interface RoleWorkspaceProps {
  name: string;
  roleCode: string;
}

export function RoleWorkspace({ name, roleCode }: RoleWorkspaceProps) {
  const tenantId = useAppSelector((s) => s.auth.user?.tenantId ?? null);
  const { pendingApprovalCount } = useNotifications(tenantId);

  const role = roleDashboardByCode(roleCode);
  if (!role) return null;
  const content = roleOverrides[role.code] ?? {
    ...familyCopy[role.family],
    title: `${role.name} dashboard`,
    focus: role.purpose,
    metrics: familyMetrics[role.family],
  };
  const quickActions = familyQuickActions[role.family] ?? [];
  const showHero = !NO_HERO_ROLES.has(role.code);

  return (
    <div className="app-dashboard app-page">
      {showHero ? (
        <section className="hero" aria-labelledby="workspace-hero-title">
          <div>
            <p className="kicker">{role.name}</p>
            <h2 className="hero__title" id="workspace-hero-title">
              {greeting()},{" "}
              <span className="app-greeting__name">{name}</span>.
            </h2>
            <p>{content.focus}</p>
            <a className="hero__cta" href="#workspace-actions">
              Quick navigation
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
        <PageHeader title={renderGreeting(name)} description={role.name} />
      )}

      <Panel
        title={content.title}
        description={
          pendingApprovalCount > 0
            ? `${pendingApprovalCount} pending approval${pendingApprovalCount !== 1 ? "s" : ""} · AT A GLANCE`
            : "AT A GLANCE · Key metrics for this role"
        }
      >
        <div className="app-panel__body">
          <div className="kpi-grid">
            {content.metrics.map((label, i) => (
              <Kpi
                key={label}
                label={label}
                value={i === 0 && pendingApprovalCount > 0 && label.toLowerCase().includes("approv") ? String(pendingApprovalCount) : "—"}
                context={i === 0 ? "Live data not yet connected" : "Awaiting backend integration"}
                icon={i === 1 ? "clock" : i === 2 ? "chart" : "home"}
              />
            ))}
          </div>
        </div>
      </Panel>

      <Panel
        id="workspace-actions"
        title="Where would you like to go?"
        description="QUICK NAVIGATION · Available pages for your role"
      >
        <div className="app-action-grid">
          {quickActions.map((action) => (
            <Link key={action.href} to={action.href} className="app-action-tile">
              <span className="app-action-tile__label">{action.label}</span>
              <span className="app-action-tile__desc">{action.description}</span>
            </Link>
          ))}
        </div>
        <p className="app-action-note">
          Live work-queue data will appear here once backend task integration is complete. Available actions remain controlled by your assigned role and permissions.
        </p>
      </Panel>
    </div>
  );
}
