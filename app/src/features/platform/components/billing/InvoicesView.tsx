import { useEffect, useState, type FormEvent } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import {
  Button,
  DataTable,
  MetricCard,
  Notice,
  Panel,
  SearchBox,
  StatusBadge,
} from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import {
  platformOpsService,
  type InvoiceItem,
  type CreateInvoicePayload,
} from "../../api/platformOpsService";
import { platformTenantService, type TenantItem } from "../../api/platformTenantService";
import { usePlatformAccess } from "../../platformAccess";

export function InvoicesView() {
  const { can, role } = usePlatformAccess();
  const canManageInvoice = can("INVOICE_MANAGE");
  const canApplyDiscount = can("DISCOUNT_APPLY");
  const isProductOwner = role === "PRODUCT_OWNER";

  const [invoices, setInvoices] = useState<InvoiceItem[]>([]);
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  // Create Invoice Modal
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createDraft, setCreateDraft] = useState<CreateInvoicePayload>({
    tenantId: "",
    periodStart: new Date().toISOString().substring(0, 7) + "-01",
    periodEnd: new Date().toISOString().substring(0, 10),
    subtotalAmount: 50000,
    currencyCode: "INR",
  });

  // Record Payment Modal
  const [payInvoice, setPayInvoice] = useState<InvoiceItem | null>(null);
  const [paymentMode, setPaymentMode] = useState("GATEWAY");
  const [paymentRef, setPaymentRef] = useState("");
  const [recordingPay, setRecordingPay] = useState(false);

  // Discount Modal
  const [discountInvoice, setDiscountInvoice] = useState<InvoiceItem | null>(null);
  const [discountAmount, setDiscountAmount] = useState(5000);
  const [applyingDiscount, setApplyingDiscount] = useState(false);

  const loadData = async () => {
    setLoading(true);
    setError("");
    try {
      const [invs, tenantRes] = await Promise.all([
        platformOpsService.listInvoices(),
        platformTenantService.getTenants(),
      ]);
      setInvoices(invs);
      setTenants(tenantRes.data?.items ?? []);
      if (tenantRes.data?.items?.length && !createDraft.tenantId) {
        setCreateDraft((prev) => ({ ...prev, tenantId: tenantRes.data.items[0].tenantId }));
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to load invoices";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setError("");
    try {
      await platformOpsService.createInvoice(createDraft);
      setMessage("Invoice created successfully.");
      setShowCreate(false);
      loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to create invoice");
    } finally {
      setCreating(false);
    }
  };

  const handleSend = async (inv: InvoiceItem) => {
    try {
      await platformOpsService.sendInvoice(inv.invoiceId);
      setMessage(`Invoice ${inv.invoiceNumber} marked as SENT.`);
      loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to send invoice");
    }
  };

  const handleFlagOverdue = async (inv: InvoiceItem) => {
    try {
      await platformOpsService.flagOverdue(inv.invoiceId);
      setMessage(`Invoice ${inv.invoiceNumber} flagged as overdue.`);
      loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to flag overdue");
    }
  };

  const handleRecordPayment = async (e: FormEvent) => {
    e.preventDefault();
    if (!payInvoice) return;
    setRecordingPay(true);
    setError("");
    try {
      await platformOpsService.recordPayment(payInvoice.invoiceId, {
        paymentMode,
        paymentReference: paymentRef.trim() || `TXN-${Date.now()}`,
      });
      setMessage(`Payment recorded for ${payInvoice.invoiceNumber}.`);
      setPayInvoice(null);
      setPaymentRef("");
      loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to record payment");
    } finally {
      setRecordingPay(false);
    }
  };

  const handleApplyDiscount = async (e: FormEvent) => {
    e.preventDefault();
    if (!discountInvoice) return;
    setApplyingDiscount(true);
    setError("");
    try {
      await platformOpsService.applyDiscount(discountInvoice.invoiceId, Number(discountAmount));
      setMessage(`Discount applied to ${discountInvoice.invoiceNumber}.`);
      setDiscountInvoice(null);
      loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to apply discount");
    } finally {
      setApplyingDiscount(false);
    }
  };

  const filteredInvoices = invoices.filter((inv) => {
    const matchesQuery =
      inv.invoiceNumber.toLowerCase().includes(query.toLowerCase()) ||
      inv.tenantId.toLowerCase().includes(query.toLowerCase());
    const matchesStatus = statusFilter === "All" || inv.status === statusFilter;
    return matchesQuery && matchesStatus;
  });

  const totalInvoiced = invoices.reduce((sum, i) => sum + Number(i.totalAmount || 0), 0);
  const totalPaid = invoices
    .filter((i) => i.status === "PAID")
    .reduce((sum, i) => sum + Number(i.totalAmount || 0), 0);
  const overdueCount = invoices.filter((i) => !!i.overdueFlaggedAt).length;

  return (
    <div className="platform-page">
      <PageHeader
        title="Invoices & Billing"
        description="Tenant subscription invoicing, payment recording, overdue flagging and Product Owner discounts."
        actions={
          canManageInvoice && (
            <Button onClick={() => setShowCreate(true)}>Create Invoice</Button>
          )
        }
      />

      {message && <Notice tone="success">{message}</Notice>}
      {error && <Notice tone="danger">{error}</Notice>}

      <div className="kpi-grid">
        <MetricCard label="Total Invoices" value={invoices.length} note="Platform-wide" mark="book" />
        <MetricCard
          label="Total Billed"
          value={`₹${totalInvoiced.toLocaleString("en-IN")}`}
          note="Combined billing"
          mark="wallet"
        />
        <MetricCard
          label="Paid Collections"
          value={`₹${totalPaid.toLocaleString("en-IN")}`}
          note="Settled invoices"
          mark="check-circle"
        />
        <MetricCard
          label="Overdue Flagged"
          value={overdueCount}
          note="Requires attention"
          mark="alert"
        />
      </div>

      <Panel title="Platform Invoices" description="Manage invoices across all tenants">
        <div className="platform-toolbar" style={{ display: "flex", gap: "12px", marginBottom: "16px", flexWrap: "wrap" }}>
          <SearchBox
            value={query}
            onChange={(val) => setQuery(val)}
            placeholder="Search invoice number..."
          />
          <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
            <span style={{ fontSize: "var(--fs-body-sm)", color: "var(--color-text-muted)" }}>Status:</span>
            {["All", "DRAFT", "SENT", "PAID"].map((st) => (
              <Button
                key={st}
                size="sm"
                variant={statusFilter === st ? "primary" : "secondary"}
                onClick={() => setStatusFilter(st)}
              >
                {st}
              </Button>
            ))}
          </div>
        </div>

        {loading ? (
          <div className="platform-empty-inline">
            <Spinner label="Loading invoices..." />
          </div>
        ) : filteredInvoices.length === 0 ? (
          <p className="platform-empty-inline">No invoices found matching criteria.</p>
        ) : (
          <DataTable headers={["Invoice #", "Period", "Amount", "Status", "Flags", "Actions"]}>
            {filteredInvoices.map((inv) => (
              <tr key={inv.invoiceId}>
                <td>
                  <strong>{inv.invoiceNumber}</strong>
                  <small className="platform-cell-sub">Tenant: {inv.tenantId.substring(0, 8)}...</small>
                </td>
                <td>
                  {inv.periodStart} to {inv.periodEnd}
                </td>
                <td>
                  <strong>₹{Number(inv.totalAmount).toLocaleString("en-IN")}</strong>
                  {Number(inv.discountAmount) > 0 && (
                    <small className="platform-cell-sub" style={{ color: "var(--color-status-success-fg)" }}>
                      -₹{Number(inv.discountAmount).toLocaleString("en-IN")} discount
                    </small>
                  )}
                </td>
                <td>
                  <StatusBadge value={inv.status} />
                </td>
                <td>
                  {inv.overdueFlaggedAt ? (
                    <span style={{ color: "var(--color-status-danger-fg)", fontWeight: "bold" }}>OVERDUE</span>
                  ) : (
                    "—"
                  )}
                </td>
                <td>
                  <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                    {inv.status === "DRAFT" && canManageInvoice && (
                      <Button size="sm" variant="secondary" onClick={() => handleSend(inv)}>
                        Send
                      </Button>
                    )}
                    {inv.status !== "PAID" && canManageInvoice && (
                      <Button size="sm" variant="secondary" onClick={() => setPayInvoice(inv)}>
                        Record Pay
                      </Button>
                    )}
                    {inv.status !== "PAID" && !inv.overdueFlaggedAt && (
                      <Button size="sm" variant="secondary" onClick={() => handleFlagOverdue(inv)}>
                        Flag Overdue
                      </Button>
                    )}
                    {isProductOwner && inv.status !== "PAID" && canApplyDiscount && (
                      <Button size="sm" variant="secondary" onClick={() => setDiscountInvoice(inv)}>
                        Discount
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Panel>

      {/* Create Invoice Modal */}
      {showCreate && (
        <div className="platform-modal-backdrop">
          <div className="platform-modal" role="dialog" aria-label="Create Invoice">
            <header>
              <h2>Create Tenant Invoice</h2>
              <p>Issue a new billing statement for an enrolled tenant institution.</p>
            </header>
            <form onSubmit={handleCreate}>
              <div style={{ display: "grid", gap: "12px" }}>
                <label className="field-label">
                  <span>Tenant Institution</span>
                  <select
                    className="platform-input"
                    value={createDraft.tenantId}
                    onChange={(e) => setCreateDraft({ ...createDraft, tenantId: e.target.value })}
                    required
                  >
                    {tenants.map((t) => (
                      <option key={t.tenantId} value={t.tenantId}>
                        {t.tenantName} ({t.subdomain})
                      </option>
                    ))}
                  </select>
                </label>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                  <label className="field-label">
                    <span>Period Start</span>
                    <input
                      type="date"
                      className="platform-input"
                      value={createDraft.periodStart}
                      onChange={(e) => setCreateDraft({ ...createDraft, periodStart: e.target.value })}
                      required
                    />
                  </label>
                  <label className="field-label">
                    <span>Period End</span>
                    <input
                      type="date"
                      className="platform-input"
                      value={createDraft.periodEnd}
                      onChange={(e) => setCreateDraft({ ...createDraft, periodEnd: e.target.value })}
                      required
                    />
                  </label>
                </div>
                <label className="field-label">
                  <span>Subtotal Amount (₹ INR)</span>
                  <input
                    type="number"
                    className="platform-input"
                    value={createDraft.subtotalAmount}
                    onChange={(e) => setCreateDraft({ ...createDraft, subtotalAmount: Number(e.target.value) })}
                    min="1"
                    required
                  />
                </label>
              </div>
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
                <Button variant="secondary" onClick={() => setShowCreate(false)} type="button">
                  Cancel
                </Button>
                <Button type="submit" disabled={creating}>
                  {creating ? "Creating..." : "Save Draft Invoice"}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Record Payment Modal */}
      {payInvoice && (
        <div className="platform-modal-backdrop">
          <div className="platform-modal" role="dialog" aria-label="Record Payment">
            <header>
              <h2>Record Payment · {payInvoice.invoiceNumber}</h2>
              <p>Amount due: ₹{Number(payInvoice.totalAmount).toLocaleString("en-IN")}</p>
            </header>
            <form onSubmit={handleRecordPayment}>
              <div style={{ display: "grid", gap: "12px" }}>
                <label className="field-label">
                  <span>Payment Mode</span>
                  <select
                    className="platform-input"
                    value={paymentMode}
                    onChange={(e) => setPaymentMode(e.target.value)}
                  >
                    <option value="GATEWAY">Payment Gateway</option>
                    <option value="OFFLINE">Offline Bank Transfer / Cheque</option>
                  </select>
                </label>
                <label className="field-label">
                  <span>Payment Reference / UTR Number</span>
                  <input
                    type="text"
                    className="platform-input"
                    placeholder="e.g. UTR20261107009823"
                    value={paymentRef}
                    onChange={(e) => setPaymentRef(e.target.value)}
                    required
                  />
                </label>
              </div>
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
                <Button variant="secondary" onClick={() => setPayInvoice(null)} type="button">
                  Cancel
                </Button>
                <Button type="submit" disabled={recordingPay}>
                  {recordingPay ? "Recording..." : "Confirm Payment"}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Apply Discount Modal */}
      {discountInvoice && (
        <div className="platform-modal-backdrop">
          <div className="platform-modal" role="dialog" aria-label="Apply Discount">
            <header>
              <h2>Apply Discount (Product Owner only)</h2>
              <p>Invoice {discountInvoice.invoiceNumber} · Subtotal: ₹{discountInvoice.subtotalAmount}</p>
            </header>
            <form onSubmit={handleApplyDiscount}>
              <label className="field-label">
                <span>Discount Amount (₹ INR)</span>
                <input
                  type="number"
                  className="platform-input"
                  value={discountAmount}
                  onChange={(e) => setDiscountAmount(Number(e.target.value))}
                  max={discountInvoice.subtotalAmount}
                  min="1"
                  required
                />
              </label>
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
                <Button variant="secondary" onClick={() => setDiscountInvoice(null)} type="button">
                  Cancel
                </Button>
                <Button type="submit" disabled={applyingDiscount}>
                  {applyingDiscount ? "Applying..." : "Apply Discount"}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export const InvoicesPage = InvoicesView;
