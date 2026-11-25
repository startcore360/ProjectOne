import { useEffect, useState } from "react";

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => { open: () => void };
  }
}

export type CheckoutSession = {
  paymentId: string;
  provider: "RAZORPAY" | "STRIPE";
  providerOrderId: string;
  amount: number;
  currency: "INR";
  keyId?: string;
  checkoutUrl?: string;
  description?: string;
};

type Props = {
  session: CheckoutSession;
  onClose: () => void;
  onSubmitted: () => void;
};

const RAZORPAY_SCRIPT = "https://checkout.razorpay.com/v1/checkout.js";

export default function RazorpayCheckoutModal({ session, onClose, onSubmitted }: Props) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (session.provider !== "RAZORPAY" || window.Razorpay) return;
    const script = document.createElement("script");
    script.src = RAZORPAY_SCRIPT;
    script.async = true;
    script.dataset.edu360Gateway = "razorpay";
    script.onerror = () => setError("The payment provider could not be loaded. Please try again.");
    document.body.appendChild(script);
    return () => {
      // Keep the provider SDK available for a later retry; do not remove a script while it loads.
    };
  }, [session.provider]);

  async function openCheckout() {
    setError("");
    if (session.provider === "STRIPE") {
      if (!session.checkoutUrl) {
        setError("The secure Stripe checkout URL is missing.");
        return;
      }
      window.location.assign(session.checkoutUrl);
      return;
    }
    if (!session.keyId) {
      setError("The Razorpay public key is not configured.");
      return;
    }
    if (!window.Razorpay) {
      setError("The payment provider is still loading. Please retry in a moment.");
      return;
    }
    setLoading(true);
    try {
      const checkout = new window.Razorpay({
        key: session.keyId,
        order_id: session.providerOrderId,
        amount: Math.round(Number(session.amount) * 100),
        currency: session.currency,
        name: "EDU 360",
        description: session.description || "Student fee payment",
        modal: { ondismiss: onClose },
        handler: () => {
          // Browser callback is not proof of payment. Only the verified server webhook reconciles it.
          onSubmitted();
        },
      });
      checkout.open();
    } catch {
      setError("Unable to open the secure checkout window.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section role="dialog" aria-modal="true" aria-labelledby="checkout-title" className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-indigo-600">Secure payment</p>
            <h2 id="checkout-title" className="mt-1 text-xl font-semibold text-slate-900">{session.provider === "RAZORPAY" ? "Razorpay checkout" : "Stripe checkout"}</h2>
          </div>
          <button type="button" aria-label="Close checkout" onClick={onClose} className="rounded-lg px-2 py-1 text-slate-500 hover:bg-slate-100">✕</button>
        </div>
        <div className="my-5 rounded-xl bg-slate-50 p-4">
          <p className="text-sm text-slate-500">Amount payable</p>
          <p className="mt-1 text-2xl font-semibold text-slate-900">{new Intl.NumberFormat("en-IN", { style: "currency", currency: session.currency }).format(session.amount)}</p>
          <p className="mt-2 break-all text-xs text-slate-500">Payment reference: {session.paymentId}</p>
        </div>
        {error && <p role="alert" className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
        <p className="mb-4 text-xs leading-5 text-slate-500">Payment status is confirmed only after the provider's signed server notification is verified. Do not close or refresh this page until the provider opens.</p>
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700">Cancel</button>
          <button type="button" disabled={loading} onClick={() => void openCheckout()} className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50">{loading ? "Opening…" : session.provider === "RAZORPAY" ? "Pay securely" : "Continue to Stripe"}</button>
        </div>
      </section>
    </div>
  );
}
