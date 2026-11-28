import type { ReactNode } from "react";
import Button from "../design-system/components/Button";
import EmptyState from "../design-system/components/EmptyState";
import ErrorState from "../design-system/components/ErrorState";
import Spinner from "../design-system/components/Spinner";
import { tenantPermissionsService } from "../features/profile/api/tenantPermissionsService";
import { useTenantAccess } from "../features/profile/hooks/useTenantAccess";
import { useSupportSession } from "../features/support/useSupportSession";
import { TENANT_ROUTE_ACTIONS } from "./tenantRouteActions";

type ActionGateProps = {
  anyOf: readonly string[];
  children: ReactNode;
  /** Confidential tenant workflows must not inherit the support-session UI bypass. */
  allowSupportSession?: boolean;
};

export default function ActionGate({ anyOf, children, allowSupportSession = true }: ActionGateProps) {
  const { canAny, loading, error } = useTenantAccess();
  const { isSupportSession } = useSupportSession();
  const isConfidentialWorkflow = anyOf.some((action) =>
    (TENANT_ROUTE_ACTIONS.confidentialGrievances as readonly string[]).includes(action),
  );
  const supportBypassAllowed = allowSupportSession && !isConfidentialWorkflow;
  if (isSupportSession && supportBypassAllowed) return <>{children}</>;
  if (isSupportSession && !supportBypassAllowed) {
    return (
      <div className="eds-access-denied" role="status" aria-live="polite">
        <EmptyState variant="page" icon="shield" title="Access unavailable" description="Support sessions cannot access this confidential tenant workflow." />
      </div>
    );
  }
  if (loading) return <Spinner label="Checking access…" />;

  if (error) {
    return (
      <div className="eds-access-error" role="alert">
        <ErrorState
          title="Could not verify access"
          message={error}
          action={
            <Button
              variant="secondary"
              onClick={() => void tenantPermissionsService.reload()}
            >
              Retry
            </Button>
          }
        />
      </div>
    );
  }

  if (!canAny(anyOf)) {
    return (
      <div className="eds-access-denied" role="status" aria-live="polite">
        <EmptyState variant="page" icon="shield" title="Access unavailable" description="Your current role does not have permission to view this page. Contact your administrator if you believe you need access." />
      </div>
    );
  }
  return <>{children}</>;
}
