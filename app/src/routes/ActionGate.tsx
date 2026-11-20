import type { ReactNode } from "react";
import Button from "../design-system/components/Button";
import EmptyState from "../design-system/components/EmptyState";
import ErrorState from "../design-system/components/ErrorState";
import Spinner from "../design-system/components/Spinner";
import { tenantPermissionsService } from "../features/profile/api/tenantPermissionsService";
import { useTenantAccess } from "../features/profile/hooks/useTenantAccess";
import { useSupportSession } from "../features/support/useSupportSession";

type ActionGateProps = {
  anyOf: readonly string[];
  children: ReactNode;
};

export default function ActionGate({ anyOf, children }: ActionGateProps) {
  const { canAny, loading, error } = useTenantAccess();
  const { isSupportSession } = useSupportSession();
  if (isSupportSession) return <>{children}</>;
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
