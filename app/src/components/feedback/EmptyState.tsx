import type { ReactNode } from "react";

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <section className="flex min-h-48 flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center" aria-live="polite">
      <h3 className="text-base font-semibold text-slate-900">{title}</h3>
      {description ? <p className="mt-2 max-w-md text-sm text-slate-500">{description}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </section>
  );
}
