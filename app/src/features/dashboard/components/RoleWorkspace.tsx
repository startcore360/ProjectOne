import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Icon } from "../../../components/ui/Icon";
import { PageHeader, Kpi, Panel } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { selectPendingApprovalCount } from "../../notifications/hooks/useNotifications";
import { dashboardService, type DashboardSummary } from "../api/dashboardService";
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

// Only this one role's dashboard is wired to real numbers so far (see RoleWorkspace's own
// metrics.map below for exactly which KPI each count fills); every other role still shows the
// "Live data not yet connected" placeholder until its own real metrics are scoped and built.
const ROLES_WITH_LIVE_SUMMARY = new Set(["SCHOOL_ADMIN"]);

export function RoleWorkspace({ name, roleCode }: RoleWorkspaceProps) {
  const pendingApprovalCount = useAppSelector(selectPendingApprovalCount);
  const tenantId = useAppSelector((s) => s.auth.user?.tenantId);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);

  const role = roleDashboardByCode(roleCode);

  useEffect(() => {
    if (!role || !tenantId || !ROLES_WITH_LIVE_SUMMARY.has(role.code)) return;
    let active = true;
    dashboardService
      .getSummary(tenantId)
      .then(({ data }) => {
        if (active) setSummary(data);
      })
      .catch(() => {
        // Placeholder values stay in place if the summary can't be loaded.
      });
    return () => {
      active = false;
    };
  }, [role, tenantId]);

  if (!role) return null;
  const content = roleOverrides[role.code] ?? {
    ...familyCopy[role.family],
    title: `${role.name} dashboard`,
    focus: role.purpose,
    metrics: familyMetrics[role.family],
  };
  const quickActions = familyQuickActions[role.family] ?? [];
  const showHero = !NO_HERO_ROLES.has(role.code);

  // School Admin's 3 KPIs, in the catalog's own order (see roleDashboardCatalog.ts):
  // "User and role tasks" -> active users, "Academic setup" -> active sections,
  // "Institution operations" -> pending approvals (already live for every role below).
  const schoolAdminMetric = (i: number): { value: string; context: string } | null => {
    if (role.code !== "SCHOOL_ADMIN") return null;
    if (i === 0) {
      return summary
        ? { value: String(summary.activeUsers), context: "Active users in your tenant" }
        : null;
    }
    if (i === 1) {
      return summary
        ? { value: String(summary.activeSections), context: "Active sections configured" }
        : null;
    }
    if (i === 2) {
      return { value: String(pendingApprovalCount), context: "Pending approvals" };
    }
    return null;
  };

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
            {content.metrics.map((label, i) => {
              const live = schoolAdminMetric(i);
              return (
                <Kpi
                  key={label}
                  label={label}
                  value={
                    live
                      ? live.value
                      : i === 0 && pendingApprovalCount > 0 && label.toLowerCase().includes("approv")
                        ? String(pendingApprovalCount)
                        : "—"
                  }
                  context={live ? live.context : i === 0 ? "Live data not yet connected" : "Awaiting backend integration"}
                  icon={i === 1 ? "clock" : i === 2 ? "chart" : "home"}
                />
              );
            })}
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
