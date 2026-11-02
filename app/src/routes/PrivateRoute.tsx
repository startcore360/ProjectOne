import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAppSelector } from "../store/hooks";

// Wraps pages that require a signed-in user. If nobody's signed in, it sends them to /login
// instead of showing the page. It also enforces the forced first-time password change here,
// client-side: if the signed-in user still owes a password change, every private page except
// /change-password itself redirects there - the backend's MustChangePasswordFilter enforces
// the same rule server-side, so this is a UX convenience, not the real security boundary.
export default function PrivateRoute() {
  const { isAuthenticated, user } = useAppSelector((state) => state.auth);
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (user?.mustChangePassword && location.pathname !== "/change-password") {
    return <Navigate to="/change-password" replace />;
  }
  return <Outlet />;
}
