import { cn } from "../../lib/utils";

export interface SpinnerProps {
  label?: string;
  className?: string;
}

export function Spinner({ label = "Loading", className }: SpinnerProps) {
  return (
    <span role="status" className={cn("inline-flex items-center gap-2 text-sm", className)}>
      <span aria-hidden="true" className="eds-spinner" />
      <span>{label}</span>
    </span>
  );
}

export default Spinner;
