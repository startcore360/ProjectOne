import { useAppSelector } from "../../../store/hooks";

export function useTenantAccess() {
  const permissions = useAppSelector((state) => state.tenantPermissions.current);
  const loading = useAppSelector((state) => state.tenantPermissions.loading);
  const error = useAppSelector((state) => state.tenantPermissions.error);
  const can = (action: string) => permissions?.allowedActions.includes(action) ?? false;
  const canAny = (actions: readonly string[]) => actions.some(can);
  return { can, canAny, loading, error, permissions };
}
