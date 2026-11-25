export type FeeReceiptDetails = {
  receiptLabel: string;
  paymentId: string;
  studentName: string;
  institutionName: string;
  paidAt: string;
  paymentMethod: string;
  amount: number;
  currency?: string;
  allocations: Array<{ installmentNumber: number; dueDate: string; amount: number }>;
};

type Props = { receipt: FeeReceiptDetails };

export default function PrintableFeeReceipt({ receipt }: Props) {
  const currency = receipt.currency || "INR";
  const money = (value: number) => new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(Number(value || 0));

  return (
    <article className="relative mx-auto w-full max-w-3xl overflow-hidden rounded-xl border border-slate-200 bg-white p-6 shadow-sm print:max-w-none print:rounded-none print:border-0 print:shadow-none">
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 flex items-center justify-center overflow-hidden">
        <span className="rotate-[-28deg] select-none text-6xl font-black tracking-widest text-slate-100 print:text-slate-200">EDU 360</span>
      </div>
      <div className="relative">
        <header className="flex flex-col justify-between gap-4 border-b border-slate-200 pb-5 sm:flex-row">
          <div><p className="text-xs font-semibold uppercase tracking-[0.2em] text-indigo-600">Official fee receipt</p><h1 className="mt-2 text-2xl font-bold text-slate-900">{receipt.institutionName}</h1><p className="mt-1 text-sm text-slate-500">Student finance and accounts</p></div>
          <div className="sm:text-right"><p className="text-xs uppercase text-slate-500">Receipt number</p><p className="mt-1 text-lg font-semibold text-slate-900">{receipt.receiptLabel}</p><p className="mt-1 text-xs text-slate-500">Payment ID: {receipt.paymentId}</p></div>
        </header>
        <dl className="grid gap-4 py-5 text-sm sm:grid-cols-2">
          <div><dt className="text-slate-500">Received from</dt><dd className="mt-1 font-semibold text-slate-900">{receipt.studentName}</dd></div>
          <div><dt className="text-slate-500">Payment date</dt><dd className="mt-1 font-semibold text-slate-900">{new Date(receipt.paidAt).toLocaleString("en-IN")}</dd></div>
          <div><dt className="text-slate-500">Payment method</dt><dd className="mt-1 font-semibold text-slate-900">{receipt.paymentMethod}</dd></div>
          <div><dt className="text-slate-500">Amount received</dt><dd className="mt-1 text-xl font-bold text-slate-900">{money(receipt.amount)}</dd></div>
        </dl>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-y border-slate-200 bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="px-3 py-3">Installment</th><th className="px-3 py-3">Due date</th><th className="px-3 py-3 text-right">Amount allocated</th></tr></thead>
            <tbody className="divide-y divide-slate-100">{receipt.allocations.map((item) => <tr key={item.installmentNumber}><td className="px-3 py-3">Installment {item.installmentNumber}</td><td className="px-3 py-3">{item.dueDate}</td><td className="px-3 py-3 text-right tabular-nums">{money(item.amount)}</td></tr>)}</tbody>
            <tfoot><tr><td colSpan={2} className="px-3 py-4 text-right font-semibold text-slate-700">Total received</td><td className="px-3 py-4 text-right font-bold tabular-nums text-slate-900">{money(receipt.amount)}</td></tr></tfoot>
          </table>
        </div>
        <footer className="mt-8 border-t border-dashed border-slate-300 pt-4 text-xs leading-5 text-slate-500">
          <p>This is a system-generated receipt. Verify the receipt number with the institution before relying on a printed copy.</p>
          <p className="mt-1">No signature is required for a digitally generated receipt.</p>
        </footer>
        <div className="mt-5 flex justify-end print:hidden"><button type="button" onClick={() => window.print()} className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700">Print / Save as PDF</button></div>
      </div>
    </article>
  );
}
