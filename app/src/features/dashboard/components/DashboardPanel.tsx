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
    <section className="student-panel" id={id}>
      <header className="student-panel__header">
        <div>
          <p className="student-eyebrow">{eyebrow}</p>
          <h2>{title}</h2>
        </div>
        {action && (
          <a className="student-panel__link" href={action.href}>
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
    <div className="student-empty">
      <span className="student-empty__mark">
        <Icon name="book" size={20} />
      </span>
      <div>
        <strong>{title}</strong>
        <p>{description}</p>
      </div>
    </div>
  );
}
