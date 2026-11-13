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
      <div role="status" aria-live="polite">
        <EmptyState title="Access unavailable" description="You don't have access to this." />
      </div>
    );
  }
  return <>{children}</>;
}
