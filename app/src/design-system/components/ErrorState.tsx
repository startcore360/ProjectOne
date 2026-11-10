import type { ReactNode } from "react";
import { cn } from "../../lib/utils";

export interface ErrorStateProps {
  title?: string;
  message: string;
  action?: ReactNode;
  className?: string;
}

export function ErrorState({
  title = "Something went wrong",
  message,
  action,
  className,
}: ErrorStateProps) {
  return (
    <section role="alert" className={cn("eds-error-state", className)}>
      <h3 className="eds-error-state__title">{title}</h3>
      <p className="eds-error-state__message">{message}</p>
      {action ? <div className="mt-3">{action}</div> : null}
    </section>
  );
}

export default ErrorState;
