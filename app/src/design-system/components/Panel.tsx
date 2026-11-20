import type { ReactNode } from "react";

export interface PanelProps {
  title: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  id?: string;
}

export function Panel({ title, description, action, children, className = "", id }: PanelProps) {
  return (
    <section id={id} className={`app-panel ${className}`.trim()}>
      <header className="app-panel__header">
        <div>
          <h2>{title}</h2>
          {description && <p>{description}</p>}
        </div>
        {action}
      </header>
      {children}
    </section>
  );
}
