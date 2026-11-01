import { cn } from "../../lib/utils";

// A small spinning "loading" icon with a text label, shown while something is loading.
export function Spinner({ label = "Loading", className }: { label?: string; className?: string }) {
  return (
    <span role="status" className={cn("inline-flex items-center gap-2 text-sm", className)}>
      <span aria-hidden="true" className="eds-spinner" />
      <span>{label}</span>
    </span>
  );
}
