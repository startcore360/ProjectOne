import type { ReactNode } from "react";
import { cn } from "../../lib/utils";

// A reusable "there's nothing here yet" message, used instead of showing a
// blank box when a list or page has no data to show.
export function EmptyState({
  title,
  description,
  action,
  className,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("eds-empty-state", className)} aria-live="polite">
      <h3 className="eds-empty-state__title">{title}</h3>
      {description ? <p className="eds-empty-state__description">{description}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </section>
  );
}
