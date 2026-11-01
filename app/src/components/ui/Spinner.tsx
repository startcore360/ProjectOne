export function Spinner({ label = "Loading", className = "" }: { label?: string; className?: string }) {
  return (
    <span role="status" className={`inline-flex items-center gap-2 text-sm text-slate-600 ${className}`}>
      <span aria-hidden="true" className="size-5 animate-spin rounded-full border-2 border-indigo-600 border-r-transparent" />
      <span>{label}</span>
    </span>
  );
}
