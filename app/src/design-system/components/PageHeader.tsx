import type { ReactNode } from "react";
import { cn } from "../../lib/utils";

export interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  className?: string;
}

export function PageHeader({
  title,
  description,
  actions,
  className,
}: PageHeaderProps) {
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

export default PageHeader;
