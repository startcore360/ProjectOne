import type { ReactNode } from "react";
import { cn } from "../../lib/utils";

export function PageHeader({
  title,
  description,
  actions,
  className,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
  className?: string;
}) {
  return (
    <header className={cn("eds-page-header", className)}>
      <div>
        <h1 className="eds-page-header__title">{title}</h1>
        {description ? <p className="eds-page-header__description">{description}</p> : null}
      </div>
      {actions ? <div className="eds-page-header__actions">{actions}</div> : null}
    </header>
  );
}
