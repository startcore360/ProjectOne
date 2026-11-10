import { Navigate, Outlet } from "react-router-dom";
import { EmptyState } from "../design-system/components";
import { canOpenPlatformConsole, usePlatformAccess } from "../features/platform/platformAccess";
import { useAppSelector } from "../store/hooks";

// Anyone with a platform role can enter the console. Non-platform users go to their dashboard.
export default function PlatformConsoleRoute() {
  const platformRole = useAppSelector((state) => state.auth.user?.platformRole);
  return canOpenPlatformConsole(platformRole) ? <Outlet /> : <Navigate to="/dashboard" replace />;
}

// One page of the console. Shows a neutral state instead of redirecting (D9 P2), so someone who
// follows an old link can see why the page didn't open.
export function PlatformPageGate({ anyOf }: { anyOf: string[] }) {
  const { canAny } = usePlatformAccess();
  if (canAny(anyOf)) return <Outlet />;
  return (
    <EmptyState
      icon="shield"
      title="You don't have access to this"
      description="Your platform role doesn't include this page. Ask your Product Owner if you need it."
    />
  );
}
