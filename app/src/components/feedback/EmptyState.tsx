import type { ReactNode } from "react";
import { cn } from "../../lib/utils";

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
