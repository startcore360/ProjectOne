import type { PermissionDenialDetails } from "../../services/apiHelper";
import { cn } from "../../lib/utils";
import { Icon } from "../../components/ui/Icon";

export interface PermissionDeniedStateProps {
  details: PermissionDenialDetails;
  className?: string;
  variant?: "inline" | "page";
}

/**
 * The CLAUDE.md "Permission Denied" screen state: a lock card naming the blocked action and,
 * where the backend allowed it (never for a sensitive/locked action - see
 * PermissionEvaluatorService.heldByRoleNames), up to 2 roles that could do it instead. Renders
 * from a live 403's PermissionDenialDetails, not a static sentence, so it differs from
 * ActionGate's page-level gate (which has no per-action API detail to show).
 */
export function PermissionDeniedState({ details, className, variant = "inline" }: PermissionDeniedStateProps) {
  return (
    <section
      className={cn("eds-empty-state", variant === "page" && "eds-empty-state--page", className)}
      role="status"
      aria-live="polite"
    >
      <span className="eds-empty-state__icon" aria-hidden="true"><Icon name="shield" size={22} /></span>
      <h2 className="eds-empty-state__title">You can't do this</h2>
      {details.actionDescription ? (
        <p className="eds-empty-state__description">Action: {details.actionDescription}</p>
      ) : null}
      <p className="eds-empty-state__description">{details.message}</p>
    </section>
  );
}
export default PermissionDeniedState;
