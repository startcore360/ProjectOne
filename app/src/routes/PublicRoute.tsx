import { Navigate, Outlet } from "react-router-dom";
import { useAppSelector } from "../store/hooks";

export default function PublicRoute() {
  const { isAuthenticated } = useAppSelector((state) => state.auth);

  return !isAuthenticated ? <Outlet /> : <Navigate to="/" replace />;
}
