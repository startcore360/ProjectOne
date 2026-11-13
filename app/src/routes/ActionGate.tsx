import type { ReactNode } from "react";
import EmptyState from "../design-system/components/EmptyState";
import Spinner from "../design-system/components/Spinner";
import { useTenantAccess } from "../features/profile/hooks/useTenantAccess";

type ActionGateProps = {
  anyOf: readonly string[];
  children: ReactNode;
};

export default function ActionGate({ anyOf, children }: ActionGateProps) {
  const { canAny, loading } = useTenantAccess();
  if (loading) return <Spinner label="Checking access…" />;
  if (!canAny(anyOf)) {
    return (
      <div className="eds-access-denied" role="status" aria-live="polite">
        <EmptyState variant="page" icon="shield" title="Access unavailable" description="Your current role does not have permission to view this page. Contact your administrator if you believe you need access." />
      </div>
    );
  }
  return <>{children}</>;
}
