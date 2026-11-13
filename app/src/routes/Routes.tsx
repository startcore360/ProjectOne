import { Navigate, Outlet, Route, Routes } from "react-router-dom";
import { useAppSelector } from "../store/hooks";
import AppLayout from "../layout/AppLayout";
import AuthLayout from "../layout/AuthLayout";
import LoginPage from "../features/auth/pages/LoginPage";
import ChangePasswordPage from "../features/auth/pages/ChangePasswordPage";
import DashboardPage from "../features/dashboard/pages/DashboardPage";
import RoleDashboardPage from "../features/role-previews/RoleDashboardPage";
import ProfilePage from "../features/profile/pages/ProfilePage";
import SelfServicePage from "../features/self-service/pages/SelfServicePage";
import SessionsPage from "../features/sessions/pages/SessionsPage";
import {
  AuditLogPage,
  BackupStatusPage,
  IntegrationTypesPage,
  PlatformOverviewPage,
  PlatformRolesPage,
  PlatformRoleTemplatesPage,
  PlatformUsersPage,
  PlansPage,
  SupportAccessPage,
  TenantsPage,
  UsageCountsPage,
  InvoicesPage,
  ReleasesPage,
  ReportsPage,
  SecurityPolicyPage,
  TicketsPage,
} from "../features/platform/pages/PlatformPages";
import {
  InstitutionsPage,
  MissingRolesPage,
  RolesPermissionsPage,
} from "../features/institution-admin/pages/InstitutionAdminPages";
import {
  AcademicGovernancePage,
  AcademicStructurePage,
  CalendarEventsPage,
  DelegationsApprovalsPage,
  ClassTeacherAssignmentPage,
  HodAssignmentPage,
  MentorAssignmentPage,
  StudentLoginSwitchPage,
  TermLockPage,
  WeeklyOffRulesPage,
} from "../features/institution-admin/pages/InstitutionSetupPages";
import { UserDetailPage, UserListPage } from "../features/user-management/pages/UserManagementPages";
import TenantSupportPage from "../features/institution-admin/pages/TenantSupportPage";
import { PLATFORM_PAGE_ACTIONS, canOpenPlatformConsole } from "../features/platform/platformAccess";
import PlatformConsoleRoute, { PlatformPageGate } from "./PlatformConsoleRoute";
import PrivateRoute from "./PrivateRoute";
import PublicRoute from "./PublicRoute";
import ActionGate from "./ActionGate";
import TenantUserRoute from "./TenantUserRoute";
import { TENANT_ROUTE_ACTIONS } from "./tenantRouteActions";

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
        <Route path="/change-password" element={<ChangePasswordPage />} />
        <Route element={<UniversityAdminRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/university/institutions" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.institutions}><InstitutionsPage /></ActionGate>} />
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
        <Route element={<PlatformConsoleRoute />}>
          <Route element={<AppLayout />}>
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
        <Route path="/" element={<RoleHomeRedirect />} />
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/support" element={<ActionGate anyOf={TENANT_ROUTE_ACTIONS.support}><TenantSupportPage /></ActionGate>} />
          {import.meta.env.DEV && <><Route path="/role-previews" element={<RoleDashboardPage />} /><Route path="/role-preview/:roleCode" element={<RoleDashboardPage />} /></>}
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/self-service" element={<TenantUserRoute><SelfServicePage /></TenantUserRoute>} />
          <Route path="/sessions" element={<SessionsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
