import { store, type AppDispatch } from "../store";
import { clearUser } from "../store/slices/authSlice";
import { setPlatformPermissions } from "../store/slices/platformPermissionsSlice";
import { clearTenantPermissions } from "../store/slices/tenantPermissionsSlice";
import { clearNotifications } from "../store/slices/notificationsSlice";

/**
 * Clears all session-scoped Redux state: user identity, tenant permissions,
 * platform permissions, and shared notifications.
 *
 * Must be invoked on every logout, session revoke, and refresh-failure path to
 * ensure no stale credentials, permissions, or notifications leak into the next session.
 */
export function clearAllSessionState(dispatch: AppDispatch = store.dispatch): void {
  dispatch(clearUser());
  dispatch(setPlatformPermissions(null));
  dispatch(clearTenantPermissions());
  dispatch(clearNotifications());
}
