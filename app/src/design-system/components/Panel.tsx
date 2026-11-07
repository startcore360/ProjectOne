import type { ReactNode } from "react";

export interface PanelProps {
  title: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}

export function Panel({ title, description, action, children, className = "" }: PanelProps) {
  return (
    <section className={`platform-panel ${className}`.trim()}>
      <header className="platform-panel__header">
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
