import type { ReactNode } from "react";
import { Icon, type IconName } from "../../components/ui/Icon";
import { cn } from "../../lib/utils";

export interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: IconName;
  action?: ReactNode;
  className?: string;
  variant?: "inline" | "page";
}

export function EmptyState({ title, description, icon = "search", action, className, variant = "inline" }: EmptyStateProps) {
  return (
    <section className={cn("eds-empty-state", variant === "page" && "eds-empty-state--page", className)}>
      <span className="eds-empty-state__icon" aria-hidden="true"><Icon name={icon} size={22} /></span>
      <h2 className="eds-empty-state__title">{title}</h2>
      {description ? <p className="eds-empty-state__description">{description}</p> : null}
      {action ? <div className="eds-empty-state__action">{action}</div> : null}
    </section>
  );
}
export default EmptyState;
