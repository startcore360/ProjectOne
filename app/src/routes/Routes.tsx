import { Suspense, lazy } from "react";
import { Navigate, Outlet, Route, Routes } from "react-router-dom";
import { useAppSelector } from "../store/hooks";
import AppLayout from "../layout/AppLayout";
import AuthLayout from "../layout/AuthLayout";
import LoginPage from "../features/auth/pages/LoginPage";
import { Spinner } from "../design-system/components";
import RouteErrorBoundary from "./RouteErrorBoundary";
import { PLATFORM_PAGE_ACTIONS, canOpenPlatformConsole } from "../features/platform/platformAccess";
import PlatformConsoleRoute, { PlatformPageGate } from "./PlatformConsoleRoute";
import PrivateRoute from "./PrivateRoute";
import PublicRoute from "./PublicRoute";
import ActionGate from "./ActionGate";
import TenantUserRoute from "./TenantUserRoute";
import { TENANT_ROUTE_ACTIONS } from "./tenantRouteActions";

// Lazy-loaded feature pages
const ChangePasswordPage = lazy(() => import("../features/auth/pages/ChangePasswordPage"));
const DashboardPage = lazy(() => import("../features/dashboard/pages/DashboardPage"));
const RoleDashboardPage = lazy(() => import("../features/role-previews/RoleDashboardPage"));
const ProfilePage = lazy(() => import("../features/profile/pages/ProfilePage"));
const SelfServicePage = lazy(() => import("../features/self-service/pages/SelfServicePage"));
const SessionsPage = lazy(() => import("../features/sessions/pages/SessionsPage"));
const TenantSupportPage = lazy(() => import("../features/institution-admin/pages/TenantSupportPage"));
const StaffRecordsPage = lazy(() => import("../features/institution-admin/pages/StaffRecordsPage"));

// Lazy-loaded platform pages
const PlatformOverviewPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.PlatformOverviewPage })));
const TenantsPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.TenantsPage })));
const PlansPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.PlansPage })));
const InvoicesPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.InvoicesPage })));
const ReleasesPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.ReleasesPage })));
const ReportsPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.ReportsPage })));
const SecurityPolicyPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.SecurityPolicyPage })));
const TicketsPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.TicketsPage })));
const PlatformUsersPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.PlatformUsersPage })));
const PlatformRolesPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.PlatformRolesPage })));
const PlatformRoleTemplatesPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.PlatformRoleTemplatesPage })));
const SupportAccessPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.SupportAccessPage })));
const AuditLogPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.AuditLogPage })));
const UsageCountsPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.UsageCountsPage })));
const BackupStatusPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.BackupStatusPage })));
const IntegrationTypesPage = lazy(() => import("../features/platform/pages/PlatformPages").then((m) => ({ default: m.IntegrationTypesPage })));

// Lazy-loaded institution admin pages
const InstitutionsPage = lazy(() => import("../features/institution-admin/pages/InstitutionAdminPages").then((m) => ({ default: m.InstitutionsPage })));
const MissingRolesPage = lazy(() => import("../features/institution-admin/pages/InstitutionAdminPages").then((m) => ({ default: m.MissingRolesPage })));
const RolesPermissionsPage = lazy(() => import("../features/institution-admin/pages/InstitutionAdminPages").then((m) => ({ default: m.RolesPermissionsPage })));

// Lazy-loaded institution setup pages
const AcademicGovernancePage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.AcademicGovernancePage })));
const AcademicStructurePage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.AcademicStructurePage })));
const CalendarEventsPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.CalendarEventsPage })));
const DelegationsApprovalsPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.DelegationsApprovalsPage })));
const ClassTeacherAssignmentPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.ClassTeacherAssignmentPage })));
const HodAssignmentPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.HodAssignmentPage })));
const MentorAssignmentPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.MentorAssignmentPage })));
const StudentLoginSwitchPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.StudentLoginSwitchPage })));
const TermLockPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.TermLockPage })));
const WeeklyOffRulesPage = lazy(() => import("../features/institution-admin/pages/InstitutionSetupPages").then((m) => ({ default: m.WeeklyOffRulesPage })));

// Lazy-loaded user management pages
const UserListPage = lazy(() => import("../features/user-management/pages/UserManagementPages").then((m) => ({ default: m.UserListPage })));
const UserDetailPage = lazy(() => import("../features/user-management/pages/UserManagementPages").then((m) => ({ default: m.UserDetailPage })));

function LazyRouteView() {
  return (
    <RouteErrorBoundary>
      <Suspense
        fallback={
          <div
            style={{
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              padding: "var(--space-8)",
              minHeight: "200px",
              width: "100%",
            }}
          >
            <Spinner label="Loading page…" />
          </div>
        }
      >
        <Outlet />
      </Suspense>
    </RouteErrorBoundary>
  );
}

// AuthUserResponse (api/.../auth/dto/AuthUserResponse.java) only ever populates platformRole
// for a platform identity, or tenantId+userType for a tenant identity - never a generic "role"
// string. platformRole is real, so this branch can be checked for real.
function RoleHomeRedirect() {
  const platformRole = useAppSelector((state) => state.auth.user?.platformRole);
  if (canOpenPlatformConsole(platformRole)) return <Navigate to="/platform" replace />;
  // There is no tenant-side "university admin" role on the backend yet (Foundation item 7,
  // not yet built - no role field exists on AuthUserResponse beyond userType), so that branch
  // can't be satisfied by any real login right now. Default everyone else to the one route
  // that actually exists - do NOT point this at "/dashboard/workspace", which doesn't exist
  // and broke login entirely before this fix.
  return <Navigate to="/dashboard" replace />;
}

// Server-backed authorization for tenant and university administration routes.
// The backend enforces each endpoint independently via PermissionEvaluatorService.
function UniversityAdminRoute() {
  const user = useAppSelector((s) => s.auth.user);
  if (user?.userType === "STUDENT" || user?.userType === "GUARDIAN") {
    return <Navigate to="/dashboard" replace />;
  }
  return <Outlet />;
}

export default function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicRoute />}>
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<LoginPage />} />
        </Route>
      </Route>

      <Route element={<PrivateRoute />}>
        <Route element={<LazyRouteView />}>
          <Route path="/change-password" element={<ChangePasswordPage />} />
        </Route>
        <Route element={<UniversityAdminRoute />}>
          <Route element={<AppLayout />}>
            <Route element={<LazyRouteView />}>
              <Route path="/university/institutions" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.institutions}><InstitutionsPage /></ActionGate>} />
              <Route path="/university/staff" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.staffRecords}><StaffRecordsPage /></ActionGate>} />
              <Route path="/university/users" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.users}><UserListPage /></ActionGate>} />
              <Route path="/university/roles" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.roles}><RolesPermissionsPage /></ActionGate>} />
              <Route path="/university/missing-roles" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.missingRoles}><MissingRolesPage /></ActionGate>} />
              <Route path="/university/setup/hod" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.hodAssignment}><HodAssignmentPage /></ActionGate>} />
              <Route path="/university/setup/mentor" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.mentorAssignment}><MentorAssignmentPage /></ActionGate>} />
              <Route path="/university/setup/class-teacher" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.classTeacherAssignment}><ClassTeacherAssignmentPage /></ActionGate>} />
              <Route path="/university/setup/student-login" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.studentLogin}><StudentLoginSwitchPage /></ActionGate>} />
              <Route path="/university/setup/calendar-events" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.calendarEvents}><CalendarEventsPage /></ActionGate>} />
              <Route path="/university/setup/weekly-off" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.weeklyOff}><WeeklyOffRulesPage /></ActionGate>} />
              <Route path="/university/setup/structure" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.academicStructure}><AcademicStructurePage /></ActionGate>} />
              <Route path="/university/setup/term-lock" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.termLock}><TermLockPage /></ActionGate>} />
              <Route path="/university/setup/delegations" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.delegations}><DelegationsApprovalsPage /></ActionGate>} />
              <Route path="/university/setup/academic-governance" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.academicGovernance}><AcademicGovernancePage /></ActionGate>} />
              <Route path="/tenants/users" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.users}><UserListPage /></ActionGate>} />
              <Route path="/tenants/users/:userId" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.users}><UserDetailPage /></ActionGate>} />
            </Route>
          </Route>
        </Route>
        <Route element={<PlatformConsoleRoute />}>
          <Route element={<AppLayout />}>
            <Route element={<LazyRouteView />}>
              <Route path="/platform" element={<PlatformOverviewPage />} />
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.tenants} />}>
                <Route path="/platform/tenants" element={<TenantsPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.plans} />}>
                <Route path="/platform/plans" element={<PlansPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.invoices} />}>
                <Route path="/platform/invoices" element={<InvoicesPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.releases} />}>
                <Route path="/platform/releases" element={<ReleasesPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.reports} />}>
                <Route path="/platform/reports" element={<ReportsPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.security} />}>
                <Route path="/platform/security-policy" element={<SecurityPolicyPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.tickets} />}>
                <Route path="/platform/tickets" element={<TicketsPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.staff} />}>
                <Route path="/platform/users" element={<PlatformUsersPage />} />
                <Route path="/platform/roles" element={<PlatformRolesPage />} />
                <Route path="/platform/role-templates" element={<PlatformRoleTemplatesPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.support} />}>
                <Route path="/platform/support" element={<SupportAccessPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.audit} />}>
                <Route path="/platform/audit" element={<AuditLogPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.usage} />}>
                <Route path="/platform/usage-counts" element={<UsageCountsPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.backup} />}>
                <Route path="/platform/backup-status" element={<BackupStatusPage />} />
              </Route>
              <Route element={<PlatformPageGate anyOf={PLATFORM_PAGE_ACTIONS.integrations} />}>
                <Route path="/platform/integration-types" element={<IntegrationTypesPage />} />
              </Route>
            </Route>
          </Route>
        </Route>
        <Route path="/" element={<RoleHomeRedirect />} />
        <Route element={<AppLayout />}>
          <Route element={<LazyRouteView />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/support" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.support}><TenantSupportPage /></ActionGate>} />
            {import.meta.env.DEV && <><Route path="/role-previews" element={<RoleDashboardPage />} /><Route path="/role-preview/:roleCode" element={<RoleDashboardPage />} /></>}
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/self-service" element={<TenantUserRoute><SelfServicePage /></TenantUserRoute>} />
            <Route path="/sessions" element={<SessionsPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
