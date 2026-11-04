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

function RoleHomeRedirect() {
  const role = useAppSelector((state) => state.auth.user?.role || "");
  const normalized = role.toUpperCase().replaceAll(" ", "_");
  if (normalized === "PLATFORM_ADMIN" || normalized === "PLATFORM_ADMINISTRATOR") return <Navigate to="/platform" replace />;
  if (normalized === "UNIVERSITY_ADMIN") return <Navigate to="/university/institutions" replace />;
  return <Navigate to="/dashboard/workspace" replace />;
}

function UniversityAdminRoute() {
  const role = useAppSelector((state) => state.auth.user?.role || "");
  const normalized = role.toUpperCase().replaceAll(" ", "_");
  return normalized === "UNIVERSITY_ADMIN" ? <Outlet /> : <Navigate to="/dashboard" replace />;
}

function PlatformAdminRoute() {
  const role = useAppSelector((state) => state.auth.user?.role || "");
  const normalized = role.toUpperCase().replaceAll(" ", "_");
  const allowed = normalized === "PLATFORM_ADMIN" || normalized === "PLATFORM_ADMINISTRATOR";
  return allowed ? <Outlet /> : <Navigate to="/dashboard" replace />;
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
        <Route element={<AppLayout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
