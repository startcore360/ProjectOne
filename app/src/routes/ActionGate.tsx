import type { ReactNode } from "react";
import { useTenantAccess } from "../features/profile/hooks/useTenantAccess";

type ActionGateProps = {
  anyOf: readonly string[];
  children: ReactNode;
};

export default function ActionGate({ anyOf, children }: ActionGateProps) {
  const { canAny, loading } = useTenantAccess();
  if (loading) return <p role="status">Checking access…</p>;
  if (!canAny(anyOf)) return <section role="status" aria-live="polite"><h2>Access unavailable</h2><p>You don't have access to this.</p></section>;
  return <>{children}</>;
}
