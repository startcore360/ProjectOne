import { useState } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";

type Invoice = {
  invoiceId: string;
  installmentNumber: number;
  dueDate: string;
  baseAmount: number;
  concessionAmount: number;
  scholarshipAmount: number;
  lateFeeAmount: number;
  paidAmount: number;
  netDue: number;
  outstandingAmount: number;
  status: string;
};

const inputClass = "w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100";

export default function StudentFeeInvoicesPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [accountId, setAccountId] = useState("");
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function loadInvoices(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId) return;
    setBusy(true);
    setError("");
    try {
      const response = await api.get<Invoice[]>(`/tenants/${tenantId}/student-fee-accounts/${accountId}/invoices`);
      setInvoices(response.data);
    } catch {
      setError("Unable to retrieve invoices. Confirm the fee account and your access permissions.");
      setInvoices([]);
    } finally {
      setBusy(false);
    }
  }

  const money = (value: number) => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(Number(value || 0));

  return (
    <main className="mx-auto max-w-6xl space-y-6 p-4 md:p-6">
      <header>
        <p className="text-sm font-semibold uppercase tracking-wide text-indigo-600">Finance · Student accounts</p>
        <h1 className="mt-1 text-2xl font-semibold text-slate-900">Fee invoices</h1>
        <p className="mt-2 text-sm text-slate-600">Review installment invoices, concessions, scholarships, late fees, payments and outstanding balances.</p>
      </header>
      <form onSubmit={loadInvoices} className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:flex-row sm:items-end">
        <label className="flex-1"><span className="mb-1 block text-sm font-medium text-slate-700">Student fee account ID</span><input className={inputClass} required value={accountId} onChange={(e) => setAccountId(e.target.value)} placeholder="UUID" /></label>
        <button disabled={busy} className="rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50">{busy ? "Loading…" : "Load invoices"}</button>
      </form>
      {error && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>}
      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 px-4 py-3"><h2 className="font-semibold text-slate-900">Installment schedule</h2></div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="px-4 py-3">Installment</th><th className="px-4 py-3">Due date</th><th className="px-4 py-3">Base</th><th className="px-4 py-3">Concession / scholarship</th><th className="px-4 py-3">Late fee</th><th className="px-4 py-3">Paid</th><th className="px-4 py-3">Outstanding</th><th className="px-4 py-3">Status</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {invoices.map((invoice) => <tr key={invoice.invoiceId}>
                <td className="px-4 py-3">#{invoice.installmentNumber}</td>
                <td className="px-4 py-3">{invoice.dueDate}</td>
                <td className="px-4 py-3 tabular-nums">{money(invoice.baseAmount)}</td>
                <td className="px-4 py-3 tabular-nums">{money(Number(invoice.concessionAmount) + Number(invoice.scholarshipAmount))}</td>
                <td className="px-4 py-3 tabular-nums">{money(invoice.lateFeeAmount)}</td>
                <td className="px-4 py-3 tabular-nums">{money(invoice.paidAmount)}</td>
                <td className="px-4 py-3 font-semibold tabular-nums">{money(invoice.outstandingAmount)}</td>
                <td className="px-4 py-3"><span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700">{invoice.status}</span></td>
              </tr>)}
              {invoices.length === 0 && <tr><td colSpan={8} className="px-4 py-8 text-center text-slate-500">Enter a fee account ID to view its invoices.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  );
}
