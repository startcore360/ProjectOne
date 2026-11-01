import type { ReactNode } from "react";

export function ErrorState({ title = "Something went wrong", message, action }: { title?: string; message: string; action?: ReactNode }) {
  return (
    <section role="alert" className="rounded-xl border border-red-200 bg-red-50 p-5">
      <h3 className="font-semibold text-red-900">{title}</h3>
      <p className="mt-1 text-sm text-red-800">{message}</p>
      {action ? <div className="mt-3">{action}</div> : null}
    </section>
  );
}
