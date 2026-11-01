import { Navigate, Outlet } from "react-router-dom";
import { useAppSelector } from "../store/hooks";

// The opposite of PrivateRoute: wraps pages meant only for signed-OUT
// visitors, like the login page. If someone is already signed in and tries
// to visit one of these pages, it redirects them to the dashboard instead.
export default function PublicRoute() {
  const { isAuthenticated } = useAppSelector((state) => state.auth);

  return !isAuthenticated ? <Outlet /> : <Navigate to="/" replace />;
}
