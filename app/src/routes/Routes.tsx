import { Navigate, Outlet, Route, Routes } from "react-router-dom";
import { useAppSelector } from "../store/hooks";
import AppLayout from "../layout/AppLayout";
import AuthLayout from "../layout/AuthLayout";
import LoginPage from "../features/auth/pages/LoginPage";
import ChangePasswordPage from "../features/auth/pages/ChangePasswordPage";
import DashboardPage from "../features/dashboard/pages/DashboardPage";
import RoleDashboardPage from "../features/role-previews/RoleDashboardPage";
import ProfilePage from "../features/profile/pages/ProfilePage";
import SessionsPage from "../features/sessions/pages/SessionsPage";
import {
  AuditLogPage,
  BackupStatusPage,
  IntegrationTypesPage,
  PlatformOverviewPage,
  PlatformUsersPage,
  PlansPage,
  SupportAccessPage,
  TenantsPage,
  UsageCountsPage,
} from "../features/platform/pages/PlatformPages";
import {
  InstitutionUsersPage,
  InstitutionsPage,
  MissingRolesPage,
  RolesPermissionsPage,
} from "../features/institution-admin/pages/InstitutionAdminPages";
import {
  CalendarEventsPage,
  ClassTeacherAssignmentPage,
  HodAssignmentPage,
  MentorAssignmentPage,
  StudentLoginSwitchPage,
  WeeklyOffRulesPage,
} from "../features/institution-admin/pages/InstitutionSetupPages";
import { UserDetailPage, UserListPage } from "../features/user-management/pages/UserManagementPages";
import PrivateRoute from "./PrivateRoute";
import PublicRoute from "./PublicRoute";

// AuthUserResponse (api/.../auth/dto/AuthUserResponse.java) only ever populates platformRole
// for a platform identity, or tenantId+userType for a tenant identity - never a generic "role"
// string. PLATFORM_ADMIN is a real platformRole value, so that branch can be checked for real.
function RoleHomeRedirect() {
  const platformRole = useAppSelector((state) => state.auth.user?.platformRole);
  if (platformRole === "PLATFORM_ADMIN") return <Navigate to="/platform" replace />;
  // There is no tenant-side "university admin" role on the backend yet (Foundation item 7,
  // not yet built - no role field exists on AuthUserResponse beyond userType), so that branch
  // can't be satisfied by any real login right now. Default everyone else to the one route
  // that actually exists - do NOT point this at "/dashboard/workspace", which doesn't exist
  // and broke login entirely before this fix.
  return <Navigate to="/dashboard" replace />;
}

// Blocked on the same missing tenant role model as above: there is nothing in AuthUserResponse
// to check for "university admin" yet, so this cannot actually gate anything today. Left as a
// pass-through (not a hard redirect to /dashboard) so the /university/* screens stay reachable
// by direct URL for now, instead of being deleted, until Foundation item 7 lands.
function UniversityAdminRoute() {
  return <Outlet />;
}

function PlatformAdminRoute() {
  const platformRole = useAppSelector((state) => state.auth.user?.platformRole);
  return platformRole === "PLATFORM_ADMIN" ? <Outlet /> : <Navigate to="/dashboard" replace />;
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
            <Route path="/university/institutions" element={<InstitutionsPage />} />
            <Route path="/university/users" element={<InstitutionUsersPage />} />
            <Route path="/university/roles" element={<RolesPermissionsPage />} />
            <Route path="/university/missing-roles" element={<MissingRolesPage />} />
            <Route path="/university/setup/hod" element={<HodAssignmentPage />} />
            <Route path="/university/setup/mentor" element={<MentorAssignmentPage />} />
            <Route path="/university/setup/class-teacher" element={<ClassTeacherAssignmentPage />} />
            <Route path="/university/setup/student-login" element={<StudentLoginSwitchPage />} />
            <Route path="/university/setup/calendar-events" element={<CalendarEventsPage />} />
            <Route path="/university/setup/weekly-off" element={<WeeklyOffRulesPage />} />
          </Route>
        </Route>
        <Route element={<PlatformAdminRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/platform" element={<PlatformOverviewPage />} />
            <Route path="/platform/tenants" element={<TenantsPage />} />
            <Route path="/platform/plans" element={<PlansPage />} />
            <Route path="/platform/users" element={<PlatformUsersPage />} />
            <Route path="/platform/support" element={<SupportAccessPage />} />
            <Route path="/platform/audit" element={<AuditLogPage />} />
            <Route path="/platform/usage-counts" element={<UsageCountsPage />} />
            <Route path="/platform/backup-status" element={<BackupStatusPage />} />
            <Route path="/platform/integration-types" element={<IntegrationTypesPage />} />
          </Route>
        </Route>
        <Route path="/" element={<RoleHomeRedirect />} />
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          {import.meta.env.DEV && <><Route path="/role-previews" element={<RoleDashboardPage />} /><Route path="/role-preview/:roleCode" element={<RoleDashboardPage />} /></>}
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/sessions" element={<SessionsPage />} />
          <Route path="/tenants/users" element={<UserListPage />} />
          <Route path="/tenants/users/:userId" element={<UserDetailPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
