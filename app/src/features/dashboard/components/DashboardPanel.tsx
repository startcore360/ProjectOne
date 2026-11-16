import type { ReactNode } from "react";
import { Icon } from "../../../components/ui/Icon";

export interface DashboardPanelProps {
  id: string;
  title: string;
  eyebrow: string;
  action?: { label: string; href: string };
  children: ReactNode;
}

export function DashboardPanel({
  id,
  title,
  eyebrow,
  action,
  children,
}: DashboardPanelProps) {
  return (
    <section className="app-dash-card" id={id}>
      <header className="app-dash-card__header">
        <div>
          <p className="app-eyebrow">{eyebrow}</p>
          <h2>{title}</h2>
        </div>
        {action && (
          <a className="app-dash-card__link" href={action.href}>
            {action.label}
          </a>
        )}
      </header>
      {children}
    </section>
  );
}

export function QuietEmpty({ title, description }: { title: string; description: string }) {
  return (
    <div className="app-empty">
      <span className="app-empty__mark">
        <Icon name="book" size={20} />
      </span>
      <div>
        <strong>{title}</strong>
        <p>{description}</p>
      </div>
    </div>
  );
}
