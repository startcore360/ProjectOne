import { useCallback, useEffect, useMemo, useState } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";
import RazorpayCheckoutModal, { type CheckoutSession } from "../components/RazorpayCheckoutModal";
import PrintableFeeReceipt, { type FeeReceiptDetails } from "../components/PrintableFeeReceipt";

type Invoice = {
  invoiceId: string;
  studentInstallmentId?: string;
  installmentNumber: number;
  dueDate: string;
  outstandingAmount: number;
  status: string;
};

type Provider = "RAZORPAY" | "STRIPE";
type FeeAccountOption = { accountId: string; studentName: string; institutionName: string };

const inputClass = "w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100";

export default function FeePaymentPortalPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [accountId, setAccountId] = useState("");
  const [accounts, setAccounts] = useState<FeeAccountOption[]>([]);
  const [provider, setProvider] = useState<Provider>("RAZORPAY");
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [session, setSession] = useState<CheckoutSession | null>(null);
  const [receipt, setReceipt] = useState<FeeReceiptDetails | null>(null);
  const [receiptPaymentId, setReceiptPaymentId] = useState(() => new URLSearchParams(window.location.search).get("paymentId") || "");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(() => new URLSearchParams(window.location.search).get("checkout") === "success"
    ? "Stripe checkout returned. Payment status is pending until the signed provider webhook is reconciled."
    : "");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!tenantId) return;
    void api.get<FeeAccountOption[]>(`/tenants/${tenantId}/me/student-fee-accounts`)
      .then((response) => setAccounts(response.data))
      .catch(() => setAccounts([]));
  }, [tenantId]);

  const loadInvoices = useCallback(async () => {
    if (!tenantId || !accountId) return;
    try {
      const response = await api.get<Invoice[]>(`/tenants/${tenantId}/student-fee-accounts/${accountId}/invoices`);
      setInvoices(response.data);
      setSelected((current) => current.filter((id) => response.data.some((invoice) => invoice.invoiceId === id && invoice.outstandingAmount > 0)));
      setError("");
    } catch {
      setInvoices([]);
      setError("Unable to load this fee account. Verify the account ID and access.");
    }
  }, [tenantId, accountId]);

  useEffect(() => { if (accountId) void loadInvoices(); }, [accountId, loadInvoices]);

  const payable = useMemo(() => invoices.filter((invoice) => selected.includes(invoice.invoiceId) && invoice.outstandingAmount > 0), [invoices, selected]);
  const total = payable.reduce((sum, invoice) => sum + Number(invoice.outstandingAmount), 0);
  const money = (value: number) => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(value);

  function toggle(id: string) {
    setSelected((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]);
  }

  async function startCheckout() {
    if (!tenantId || !accountId || payable.length === 0) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const response = await api.post<CheckoutSession & { checkoutUrl?: string; status?: string }>(`/tenants/${tenantId}/payments/checkout`, {
        studentFeeAccountId: accountId,
        installmentIds: payable.map((invoice) => invoice.studentInstallmentId || invoice.invoiceId),
        provider,
        requestKey: crypto.randomUUID(),
      });
      if (response.data.status && response.data.status !== "PENDING") {
        setNotice(`This payment attempt is already ${response.data.status.toLowerCase()}.`);
        void loadInvoices();
      } else {
        setSession(response.data);
      }
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message;
      setError(message || "Unable to start checkout. Please refresh the invoices and retry.");
    } finally {
      setBusy(false);
    }
  }

  function paymentSubmitted() {
    if (session) setReceiptPaymentId(session.paymentId);
    setSession(null);
    setNotice("Payment submitted. The account will update after the provider's signed server notification is verified.");
    setSelected([]);
    void loadInvoices();
  }

  async function loadReceipt() {
    if (!tenantId || !receiptPaymentId.trim()) return;
    setError("");
    try {
      const response = await api.get<FeeReceiptDetails>(`/tenants/${tenantId}/payments/${receiptPaymentId}/receipt`);
      setReceipt(response.data);
    } catch {
      setReceipt(null);
      setError("The issued receipt is not available yet. Wait for the signed payment confirmation and retry.");
    }
  }

  return (
    <main className="mx-auto max-w-5xl space-y-6 p-4 md:p-6">
      <header>
        <p className="text-sm font-semibold uppercase tracking-wide text-indigo-600">Finance · Online payments</p>
        <h1 className="mt-1 text-2xl font-semibold text-slate-900">Fee payment portal</h1>
        <p className="mt-2 text-sm text-slate-600">Select outstanding installments and pay the exact amount due through the configured secure gateway.</p>
      </header>
      <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <label className="block"><span className="mb-1 block text-sm font-medium text-slate-700">Student fee account ID</span><div className="flex flex-col gap-2 sm:flex-row"><input className={inputClass} list="payment-fee-account-options" value={accountId} onChange={(e) => setAccountId(e.target.value)} placeholder="Choose an account or enter its UUID" /><datalist id="payment-fee-account-options">{accounts.map((account) => <option key={account.accountId} value={account.accountId}>{account.studentName} · {account.institutionName}</option>)}</datalist><button type="button" onClick={() => void loadInvoices()} className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700">Refresh</button></div></label>
      </section>
      {error && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>}
      {notice && <div role="status" className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</div>}
      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 px-4 py-3"><h2 className="font-semibold text-slate-900">Outstanding installments</h2></div>
        <div className="divide-y divide-slate-100">
          {invoices.map((invoice) => {
            const due = Number(invoice.outstandingAmount);
            const canPay = due > 0 && invoice.status !== "PAID" && invoice.status !== "CANCELLED";
            return <label key={invoice.invoiceId} className={`flex items-center gap-3 px-4 py-4 ${canPay ? "cursor-pointer hover:bg-slate-50" : "opacity-60"}`}>
              <input type="checkbox" disabled={!canPay} checked={selected.includes(invoice.invoiceId)} onChange={() => toggle(invoice.invoiceId)} className="h-4 w-4 rounded border-slate-300 text-indigo-600" />
              <span className="min-w-0 flex-1"><span className="block font-medium text-slate-900">Installment #{invoice.installmentNumber}</span><span className="block text-xs text-slate-500">Due {invoice.dueDate} · {invoice.status}</span></span>
              <span className="font-semibold tabular-nums text-slate-900">{money(due)}</span>
            </label>;
          })}
          {invoices.length === 0 && <p className="px-4 py-8 text-center text-sm text-slate-500">Enter a fee account ID to load outstanding installments.</p>}
        </div>
      </section>
      {invoices.length > 0 && <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm md:p-6">
        <div className="grid gap-4 md:grid-cols-[1fr_auto] md:items-end">
          <label><span className="mb-1 block text-sm font-medium text-slate-700">Payment provider</span><select className={inputClass} value={provider} onChange={(e) => setProvider(e.target.value as Provider)}><option value="RAZORPAY">Razorpay</option><option value="STRIPE">Stripe</option></select></label>
          <div className="text-left md:text-right"><p className="text-sm text-slate-500">Selected amount</p><p className="text-2xl font-bold text-slate-900">{money(total)}</p></div>
        </div>
        <button type="button" disabled={busy || payable.length === 0} onClick={() => void startCheckout()} className="mt-4 w-full rounded-lg bg-indigo-600 px-4 py-3 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50">{busy ? "Creating secure checkout…" : `Continue to payment · ${money(total)}`}</button>
      </section>}
      <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <h2 className="font-semibold text-slate-900">Retrieve an issued receipt</h2>
        <div className="mt-3 flex flex-col gap-2 sm:flex-row">
          <input className={inputClass} value={receiptPaymentId} onChange={(e) => setReceiptPaymentId(e.target.value)} placeholder="Payment ID" aria-label="Payment ID for receipt" />
          <button type="button" onClick={() => void loadReceipt()} className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700">Load receipt</button>
        </div>
      </section>
      {receipt && <PrintableFeeReceipt receipt={receipt} />}
      {session && <RazorpayCheckoutModal session={session} onClose={() => setSession(null)} onSubmitted={paymentSubmitted} />}
    </main>
  );
}
