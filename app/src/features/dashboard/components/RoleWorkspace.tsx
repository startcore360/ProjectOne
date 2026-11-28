import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Icon } from "../../../components/ui/Icon";
import { PageHeader, Kpi, Panel } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { selectPendingApprovalCount } from "../../notifications/hooks/useNotifications";
import { dashboardService, type DashboardSummary } from "../api/dashboardService";
import { institutionService } from "../../institution-admin/api/institutionService";
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

type InstitutionSummary = { total: number; active: number };

// The dashboard summary endpoint is explicitly tenant-wide. It is accurate for the
// single-institution School and College tenants, but must not be used for a university
// group with several institutions (see DashboardSummaryController).
const SINGLE_INSTITUTION_SUMMARY_ROLES = new Set([
  "SCHOOL_ADMIN",
  "COLLEGE_ADMIN",
  "SCHOOL_PRINCIPAL",
  "PRINCIPAL_COLLEGE",
]);

const ADMIN_ROLES = new Set(["UNIVERSITY_ADMIN", "COLLEGE_ADMIN", "SCHOOL_ADMIN"]);
const PRINCIPAL_ROLES = new Set(["PRINCIPAL_COLLEGE", "SCHOOL_PRINCIPAL"]);

export function RoleWorkspace({ name, roleCode }: RoleWorkspaceProps) {
  const pendingApprovalCount = useAppSelector(selectPendingApprovalCount);
  const tenantId = useAppSelector((s) => s.auth.user?.tenantId);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [institutionSummary, setInstitutionSummary] = useState<InstitutionSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [refreshVersion, setRefreshVersion] = useState(0);

  const role = roleDashboardByCode(roleCode);

  const refresh = useCallback(() => setRefreshVersion((version) => version + 1), []);

  useEffect(() => {
    if (!role || !tenantId) return undefined;
    const shouldLoadTenantSummary = SINGLE_INSTITUTION_SUMMARY_ROLES.has(role.code);
    const shouldLoadInstitutions = role.code === "UNIVERSITY_ADMIN";
    if (!shouldLoadTenantSummary && !shouldLoadInstitutions) return undefined;

    let active = true;
    setLoading(true);
    setLoadError(null);

    const load = async () => {
      try {
        if (shouldLoadTenantSummary) {
          const response = await dashboardService.getSummary(tenantId);
          if (active) {
            setSummary(response.data);
            setInstitutionSummary(null);
          }
        } else {
          const response = await institutionService.listInstitutions(tenantId);
          if (active) {
            const items = Array.isArray(response.items) ? response.items : [];
            setInstitutionSummary({
              total: Number.isFinite(response.total) ? response.total : items.length,
              active: items.filter((item) => item.status === "ACTIVE").length,
            });
            setSummary(null);
          }
        }
      } catch {
        if (active) {
          setLoadError("Live dashboard data could not be loaded. Retry to check the API again.");
          setSummary(null);
          setInstitutionSummary(null);
        }
      } finally {
        if (active) setLoading(false);
      }
    };

    void load();
    return () => {
      active = false;
    };
  }, [role?.code, tenantId, refreshVersion]);

  if (!role) return null;
  const content = roleOverrides[role.code] ?? {
    ...familyCopy[role.family],
    title: `${role.name} dashboard`,
    focus: role.purpose,
    metrics: familyMetrics[role.family],
  };
  const quickActions = PRINCIPAL_ROLES.has(role.code)
    ? [
        { label: "Attendance", href: "/academic/attendance", description: "Review attendance registers and exceptions" },
        { label: "Results", href: "/academic/report-cards", description: "Review academic outcomes and publication readiness" },
        { label: "Notices", href: "/notices", description: "Institution-wide announcements and notices" },
      ]
    : role.code === "SCHOOL_ADMIN"
      ? [
          { label: "Classes & sections", href: "/university/setup/structure", description: "School academic structure and sections" },
          { label: "Users & roles", href: "/university/users", description: "Manage school identities and access" },
          { label: "Staff directory", href: "/university/staff", description: "Staff records and assignments" },
        ]
      : role.code === "COLLEGE_ADMIN"
        ? [
            { label: "Academic setup", href: "/university/setup/structure", description: "Departments, programmes and academic structure" },
            { label: "Users & roles", href: "/university/users", description: "Manage college identities and access" },
            { label: "Staff directory", href: "/university/staff", description: "Faculty and staff records" },
          ]
        : familyQuickActions[role.family] ?? [];
  const showHero = !NO_HERO_ROLES.has(role.code);

  const liveMetrics = (() => {
    if (summary && SINGLE_INSTITUTION_SUMMARY_ROLES.has(role.code)) {
      return [
        { label: "Active users", value: String(summary.activeUsers), context: "Active accounts in this institution", icon: "users" },
        { label: "Active sections", value: String(summary.activeSections), context: "Configured active sections", icon: "layers" },
        { label: "Approvals assigned", value: String(pendingApprovalCount), context: "Pending items in your approval inbox", icon: "file-text" },
      ];
    }
    if (institutionSummary && role.code === "UNIVERSITY_ADMIN") {
      return [
        { label: "Institutions", value: String(institutionSummary.total), context: "Institutions in your university scope", icon: "building" },
        { label: "Active institutions", value: String(institutionSummary.active), context: "Institutions currently active", icon: "check-circle" },
        { label: "Approvals assigned", value: String(pendingApprovalCount), context: "Pending items in your approval inbox", icon: "file-text" },
      ];
    }
    return content.metrics.map((label, index) => ({
      label,
      value: "—",
      context: index === 0 ? "Live data not connected for this metric" : "Awaiting an implemented, permission-scoped API",
      icon: index === 1 ? "clock" : index === 2 ? "chart" : "home",
    }));
  })();

  const isLeadershipDashboard = ADMIN_ROLES.has(role.code) || PRINCIPAL_ROLES.has(role.code);

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
        description={isLeadershipDashboard ? "LIVE OVERVIEW · Metrics available within your role and institution scope" : "AT A GLANCE · Key metrics for this role"}
      >
        <div className="app-panel__body">
          <div className="kpi-grid">
            {liveMetrics.map((metric) => (
              <Kpi
                key={metric.label}
                label={metric.label}
                value={metric.value}
                context={metric.context}
                icon={metric.icon}
              />
            ))}
          </div>
          {isLeadershipDashboard && (
            <div className="app-dashboard__data-status" aria-live="polite">
              <span>
                {loading ? "Updating live data…" : loadError ? loadError : "Approval count is supplied by the live approval inbox."}
              </span>
              <button type="button" className="app-dashboard__refresh" onClick={refresh} disabled={loading}>
                <Icon name="refresh" size={15} />
                {loading ? "Refreshing" : "Refresh data"}
              </button>
            </div>
          )}
        </div>
      </Panel>

      {content.panels.length > 0 && (
        <section className="app-role-insights" aria-label={`${role.name} work areas`}>
          <div className="app-role-insights__heading">
            <div>
              <p className="app-eyebrow">{isLeadershipDashboard ? "LEADERSHIP & OPERATIONS" : "ROLE WORKSPACE"}</p>
              <h2>{PRINCIPAL_ROLES.has(role.code) ? "Institution oversight" : ADMIN_ROLES.has(role.code) ? "Administration work areas" : "Your work areas"}</h2>
            </div>
          </div>
          <div className="app-role-insight-grid">
            {content.panels.map(([title, eyebrow, description]) => (
              <article className="app-role-insight" key={title}>
                <p className="app-eyebrow">{eyebrow}</p>
                <h3>{title}</h3>
                <p>{description}</p>
              </article>
            ))}
          </div>
        </section>
      )}

      <Panel
        id="workspace-actions"
        title="Quick navigation"
        description="AVAILABLE WORKSPACES · Links remain subject to the permissions on each destination"
      >
        <div className="app-action-grid">
          {quickActions.map((action) => (
            <Link key={action.href} to={action.href} className="app-action-tile">
              <span className="app-action-tile__label">{action.label}</span>
              <span className="app-action-tile__desc">{action.description}</span>
            </Link>
          ))}
        </div>
      </Panel>
    </div>
  );
}
