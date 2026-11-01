import { Navigate, Outlet } from "react-router-dom";
import { useAppSelector } from "../store/hooks";

// Wraps pages that require a signed-in user. If nobody's signed in, it sends
// them to /login instead of showing the page. Kept as its own tiny component
// (rather than one combined route guard) so each route can simply say
// "this needs PrivateRoute" or "this needs PublicRoute" and read clearly.
export default function PrivateRoute() {
  const { isAuthenticated } = useAppSelector((state) => state.auth);

  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />;
}
