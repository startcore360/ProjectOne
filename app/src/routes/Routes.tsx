import { Navigate, Outlet, Route, Routes } from "react-router-dom";
import { useAppSelector } from "../store/hooks";
import AppLayout from "../layout/AppLayout";
import AuthLayout from "../layout/AuthLayout";
import LoginPage from "../features/auth/pages/LoginPage";
import ChangePasswordPage from "../features/auth/pages/ChangePasswordPage";
import DashboardPage from "../features/dashboard/pages/DashboardPage";
import {
  AuditLogPage,
  PlatformOverviewPage,
  PlatformUsersPage,
  PlansPage,
  SupportAccessPage,
  TenantsPage,
} from "../features/platform/pages/PlatformPages";
import {
  InstitutionUsersPage,
  InstitutionsPage,
  MissingRolesPage,
  RolesPermissionsPage,
} from "../features/institution-admin/pages/InstitutionAdminPages";
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
          </Route>
        </Route>
        <Route path="/" element={<RoleHomeRedirect />} />
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
