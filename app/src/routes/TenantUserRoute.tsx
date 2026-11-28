import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAppSelector } from "../store/hooks";

export default function TenantUserRoute({ children }: { children: ReactNode }) {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  if (!tenantId) return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
}
