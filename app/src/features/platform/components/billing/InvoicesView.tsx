import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import {
  Button,
  DataTable,
  MetricCard,
  Modal,
  Notice,
  Panel,
  SearchBox,
  SelectField,
  StatusBadge,
  TextField,
} from "../../../../design-system/components";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import { Spinner } from "../../../../components/ui";
import {
  platformOpsService,
  type InvoiceItem,
  type CreateInvoicePayload,
  type InvoiceLineItem,
  type BillingCycleResponse,
} from "../../api/platformOpsService";
import { platformTenantService, type TenantItem } from "../../api/platformTenantService";
import { usePlatformAccess } from "../../platformAccess";

function RowMenu({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, [open]);
  return (
    <div ref={ref} style={{ position: "relative", display: "inline-block" }}>
      <Button variant="secondary" size="sm" onClick={() => setOpen((v) => !v)} aria-haspopup="true" aria-expanded={open}>
        ⋯
      </Button>
      {open && (
        <div
          role="menu"
          style={{
            position: "absolute", right: 0, top: "calc(100% + 4px)", zIndex: 50,
            background: "var(--color-bg-surface)", border: "1px solid var(--color-border-divider)",
            borderRadius: "var(--radius-md)", boxShadow: "var(--elevation-2)",
            minWidth: "140px", padding: "4px 0",
          }}
          onClick={() => setOpen(false)}
        >
          {children}
        </div>
      )}
    </div>
  );
}

function MenuAction({ onClick, children }: { onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      role="menuitem"
      onClick={onClick}
      style={{
        display: "block", width: "100%", textAlign: "left",
        padding: "8px 14px", background: "none", border: "none",
        fontSize: "var(--fs-body-sm)", color: "var(--color-text)", cursor: "pointer",
      }}
      onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.background = "var(--color-bg-subtle)"; }}
      onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.background = "none"; }}
    >
      {children}
    </button>
  );
}

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
  const [lineInvoice, setLineInvoice] = useState<InvoiceItem | null>(null);
  const [invoiceLines, setInvoiceLines] = useState<InvoiceLineItem[]>([]);
  const [loadingLines, setLoadingLines] = useState(false);
  const [lineDescription, setLineDescription] = useState("");
  const [lineQuantity, setLineQuantity] = useState("1");
  const [lineUnitAmount, setLineUnitAmount] = useState("0");
  const [savingLine, setSavingLine] = useState(false);
  const [showBillingCycle, setShowBillingCycle] = useState(false);
  const [runningBillingCycle, setRunningBillingCycle] = useState(false);
  const [cyclePeriodStart, setCyclePeriodStart] = useState(new Date().toISOString().substring(0, 7) + "-01");
  const [cyclePeriodEnd, setCyclePeriodEnd] = useState(new Date().toISOString().substring(0, 10));
  const [cycleIdempotencyKey, setCycleIdempotencyKey] = useState("");
  const [cycleResult, setCycleResult] = useState<BillingCycleResponse | null>(null);

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
  const [discountPercent, setDiscountPercent] = useState("10");
  const [discountReason, setDiscountReason] = useState("");
  const [discountIdempotencyKey, setDiscountIdempotencyKey] = useState("");
  const [applyingDiscount, setApplyingDiscount] = useState(false);

  const loadData = useCallback(async () => {
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
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleRunBillingCycle = async (e: FormEvent) => {
    e.preventDefault();
    setRunningBillingCycle(true);
    setError("");
    try {
      const result = await platformOpsService.createBillingCycle({
        periodStart: cyclePeriodStart,
        periodEnd: cyclePeriodEnd,
      }, cycleIdempotencyKey);
      setCycleResult(result);
      setMessage(`Billing cycle completed: ${result.created.length} invoice(s) created, ${result.skipped.length} skipped.`);
      setShowBillingCycle(false);
      void loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to run billing cycle");
    } finally {
      setRunningBillingCycle(false);
    }
  };

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setError("");
    try {
      await platformOpsService.createInvoice(createDraft);
      setMessage("Invoice created successfully.");
      setShowCreate(false);
      void loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to create invoice");
    } finally {
      setCreating(false);
    }
  };

  const handleApprove = async (inv: InvoiceItem) => {
    try {
      await platformOpsService.approveInvoice(inv.invoiceId, `invoice-approval-${inv.invoiceId}`);
      setMessage(`Invoice ${inv.invoiceNumber} approved.`);
      void loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to approve invoice");
    }
  };

  const handleSend = async (inv: InvoiceItem) => {
    try {
      await platformOpsService.sendInvoice(inv.invoiceId);
      setMessage(`Invoice ${inv.invoiceNumber} marked as SENT.`);
      void loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to send invoice");
    }
  };

  const handleFlagOverdue = async (inv: InvoiceItem) => {
    try {
      await platformOpsService.flagOverdue(inv.invoiceId);
      setMessage(`Invoice ${inv.invoiceNumber} flagged as overdue.`);
      void loadData();
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
      void loadData();
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
      await platformOpsService.applyDiscount(discountInvoice.invoiceId, {
        discountPercent: Number(discountPercent),
        reason: discountReason.trim(),
      }, discountIdempotencyKey);
      setMessage(`Discount applied to ${discountInvoice.invoiceNumber}.`);
      setDiscountInvoice(null);
      void loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to apply discount");
    } finally {
      setApplyingDiscount(false);
    }
  };

  const openInvoiceLines = async (invoice: InvoiceItem) => {
    setLineInvoice(invoice);
    setInvoiceLines([]);
    setLoadingLines(true);
    setError("");
    try {
      setInvoiceLines(await platformOpsService.listInvoiceLines(invoice.invoiceId));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load invoice lines");
    } finally {
      setLoadingLines(false);
    }
  };

  const handleAddOtherLine = async (e: FormEvent) => {
    e.preventDefault();
    if (!lineInvoice) return;
    setSavingLine(true);
    setError("");
    try {
      const updated = await platformOpsService.addInvoiceLine(lineInvoice.invoiceId, {
        description: lineDescription.trim(),
        quantity: Number(lineQuantity),
        unitAmount: Number(lineUnitAmount),
      });
      setLineInvoice(updated);
      setInvoiceLines(await platformOpsService.listInvoiceLines(lineInvoice.invoiceId));
      setLineDescription("");
      setLineQuantity("1");
      setLineUnitAmount("0");
      void loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to add invoice line");
    } finally {
      setSavingLine(false);
    }
  };

  const handleRemoveOtherLine = async (line: InvoiceLineItem) => {
    if (!lineInvoice) return;
    setSavingLine(true);
    setError("");
    try {
      const updated = await platformOpsService.removeInvoiceLine(lineInvoice.invoiceId, line.invoiceLineId);
      setLineInvoice(updated);
      setInvoiceLines(await platformOpsService.listInvoiceLines(lineInvoice.invoiceId));
      void loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to remove invoice line");
    } finally {
      setSavingLine(false);
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
    <div className="app-page">
      <PageHeader
        title="Invoices & Billing"
        description="Tenant subscription invoicing, payment recording, overdue flagging and Product Owner discounts."
        actions={
          canManageInvoice && (
            <>
              <Button variant="secondary" onClick={() => { setCycleIdempotencyKey(crypto.randomUUID()); setCycleResult(null); setShowBillingCycle(true); }}>Run billing cycle</Button>
              <Button onClick={() => setShowCreate(true)}>Create Invoice</Button>
            </>
          )
        }
      />

      {message && <Notice tone="success">{message}</Notice>}
      {error && <ErrorState message={error} action={<Button variant="secondary" onClick={() => void loadData()}>Retry</Button>} />}
      {cycleResult && cycleResult.skipped.length > 0 && (
        <Notice tone="info">Skipped tenants: {cycleResult.skipped.map((item) => `${item.tenantId.substring(0, 8)} (${item.reason})`).join(", ")}</Notice>
      )}

      <div className="kpi-grid">
        <MetricCard label="Total Invoices" value={invoices.length} note="app-wide" mark="book" />
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
        <div className="app-toolbar" style={{ display: "flex", gap: "12px", marginBottom: "16px", flexWrap: "wrap" }}>
          <SearchBox
            value={query}
            onChange={(val) => setQuery(val)}
            placeholder="Search invoice number..."
          />
          <label className="app-select-label">
            Status{" "}
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="All">All</option>
              <option value="DRAFT">Draft</option>
              <option value="APPROVED">Approved</option>
              <option value="SENT">Sent</option>
              <option value="PAID">Paid</option>
            </select>
          </label>
        </div>

        {loading ? (
          <div className="app-empty-inline">
            <Spinner label="Loading invoices..." />
          </div>
        ) : filteredInvoices.length === 0 ? (
          <p className="app-empty-inline">No invoices found matching criteria.</p>
        ) : (
          <DataTable headers={["Invoice #", "Period", "Amount", "Status", "Flags", "Actions"]}>
            {filteredInvoices.map((inv) => (
              <tr key={inv.invoiceId}>
                <td>
                  <strong>{inv.invoiceNumber}</strong>
                  <small className="app-cell-sub">Tenant: {inv.tenantId.substring(0, 8)}...</small>
                </td>
                <td>
                  {inv.periodStart} to {inv.periodEnd}
                </td>
                <td>
                  <strong>₹{Number(inv.totalAmount).toLocaleString("en-IN")}</strong>
                  {Number(inv.discountAmount) > 0 && (
                    <small className="app-cell-sub" style={{ color: "var(--color-status-success-fg)" }}>
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
                  <RowMenu>
                    <MenuAction onClick={() => void openInvoiceLines(inv)}>View lines</MenuAction>
                    {inv.status === "DRAFT" && isProductOwner && canManageInvoice && (
                      <MenuAction onClick={() => handleApprove(inv)}>Approve</MenuAction>
                    )}
                    {inv.status === "APPROVED" && canManageInvoice && (
                      <MenuAction onClick={() => handleSend(inv)}>Send</MenuAction>
                    )}
                    {inv.status !== "PAID" && canManageInvoice && (
                      <MenuAction onClick={() => setPayInvoice(inv)}>Record payment</MenuAction>
                    )}
                    {inv.status !== "PAID" && !inv.overdueFlaggedAt && (
                      <MenuAction onClick={() => handleFlagOverdue(inv)}>Flag overdue</MenuAction>
                    )}
                    {isProductOwner && (inv.status === "DRAFT" || inv.status === "APPROVED") && canApplyDiscount && (
                      <MenuAction onClick={() => {
                        setDiscountInvoice(inv);
                        setDiscountPercent("10");
                        setDiscountReason("");
                        setDiscountIdempotencyKey(crypto.randomUUID());
                      }}>Apply discount</MenuAction>
                    )}
                  </RowMenu>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Panel>

      <Modal isOpen={showBillingCycle} onClose={() => setShowBillingCycle(false)} kicker="BILLING"
        title="Run billing cycle"
        description="Create draft invoices for active tenants whose renewal dates fall within this period. Only recorded usage rows are billed."
        size="sm">
        <form onSubmit={handleRunBillingCycle}>
          <div style={{ display: "grid", gap: "12px" }}>
            <TextField id="billing-cycle-period-start" label="Period start" type="date" value={cyclePeriodStart} onChange={(e) => setCyclePeriodStart(e.target.value)} required />
            <TextField id="billing-cycle-period-end" label="Period end" type="date" value={cyclePeriodEnd} onChange={(e) => setCyclePeriodEnd(e.target.value)} required />
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
            <Button variant="secondary" type="button" onClick={() => setShowBillingCycle(false)}>Cancel</Button>
            <Button type="submit" busy={runningBillingCycle}>Generate draft invoices</Button>
          </div>
        </form>
      </Modal>

      {/* Create Invoice Modal */}
      <Modal
        isOpen={showCreate}
        onClose={() => setShowCreate(false)}
        kicker="BILLING"
        title="Create Tenant Invoice"
        titleId="create-invoice-title"
        description="Issue a new billing statement for an enrolled tenant institution."
        size="sm"
      >
        <form onSubmit={handleCreate}>
          <div style={{ display: "grid", gap: "12px" }}>
            {/* Raw <select> kept — dynamic tenant options not suitable for static DS SelectField options array */}
            <label className="field-label">
              <span>Tenant Institution</span>
              <select
                className="app-input"
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
              <TextField
                id="invoice-period-start"
                label="Period Start"
                type="date"
                value={createDraft.periodStart}
                onChange={(e) => setCreateDraft({ ...createDraft, periodStart: e.target.value })}
                required
              />
              <TextField
                id="invoice-period-end"
                label="Period End"
                type="date"
                value={createDraft.periodEnd}
                onChange={(e) => setCreateDraft({ ...createDraft, periodEnd: e.target.value })}
                required
              />
            </div>
            <TextField
              id="invoice-subtotal"
              label="Subtotal Amount (₹ INR)"
              type="number"
              value={String(createDraft.subtotalAmount)}
              onChange={(e) => setCreateDraft({ ...createDraft, subtotalAmount: Number(e.target.value) })}
              required
            />
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
            <Button variant="secondary" onClick={() => setShowCreate(false)} type="button">
              Cancel
            </Button>
            <Button type="submit" busy={creating}>
              Save Draft Invoice
            </Button>
          </div>
        </form>
      </Modal>

      {/* Invoice lines drawer */}
      <Modal
        isOpen={!!lineInvoice}
        onClose={() => setLineInvoice(null)}
        kicker="INVOICE REVIEW"
        title={lineInvoice ? `Invoice lines · ${lineInvoice.invoiceNumber}` : "Invoice lines"}
        description={lineInvoice ? `Subtotal ₹${Number(lineInvoice.subtotalAmount).toLocaleString("en-IN")} · Total ₹${Number(lineInvoice.totalAmount).toLocaleString("en-IN")}` : undefined}
        size="lg"
      >
        {loadingLines ? <Spinner label="Loading invoice lines..." /> : invoiceLines.length === 0 ? (
          <p className="app-empty-inline">No invoice lines have been recorded.</p>
        ) : (
          <DataTable headers={["Type", "Description", "Quantity", "Unit amount", "Line amount", ""]}>
            {invoiceLines.map((line) => (
              <tr key={line.invoiceLineId}>
                <td><StatusBadge value={line.lineType} /></td>
                <td>{line.description}</td>
                <td>{Number(line.quantity).toLocaleString("en-IN")}</td>
                <td>₹{Number(line.unitAmount).toLocaleString("en-IN")}</td>
                <td><strong>₹{Number(line.lineAmount).toLocaleString("en-IN")}</strong></td>
                <td>{line.lineType === "OTHER" && lineInvoice?.status === "DRAFT" && canManageInvoice && (
                  <Button size="sm" variant="secondary" disabled={savingLine} onClick={() => void handleRemoveOtherLine(line)}>Remove</Button>
                )}</td>
              </tr>
            ))}
          </DataTable>
        )}
        {lineInvoice?.status === "DRAFT" && canManageInvoice && (
          <form onSubmit={handleAddOtherLine} style={{ display: "grid", gap: "12px", marginTop: "20px", borderTop: "1px solid var(--color-border-divider)", paddingTop: "16px" }}>
            <strong>Add an OTHER line</strong>
            <TextField id="invoice-line-description" label="Description" value={lineDescription} onChange={(e) => setLineDescription(e.target.value)} required />
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
              <TextField id="invoice-line-quantity" label="Quantity" type="number" value={lineQuantity} onChange={(e) => setLineQuantity(e.target.value)} required />
              <TextField id="invoice-line-unit-amount" label="Unit amount (₹)" type="number" value={lineUnitAmount} onChange={(e) => setLineUnitAmount(e.target.value)} required />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end" }}>
              <Button type="submit" busy={savingLine}>Add line</Button>
            </div>
          </form>
        )}
        {lineInvoice && lineInvoice.status !== "DRAFT" && (
          <Notice tone="info">This invoice is read-only. Lines can only be changed while the invoice is DRAFT.</Notice>
        )}
      </Modal>

      {/* Record Payment Modal */}
      <Modal
        isOpen={!!payInvoice}
        onClose={() => setPayInvoice(null)}
        kicker="PAYMENT"
        title={payInvoice ? `Record Payment · ${payInvoice.invoiceNumber}` : "Record Payment"}
        titleId="record-payment-title"
        description={payInvoice ? `Amount due: ₹${Number(payInvoice.totalAmount).toLocaleString("en-IN")}` : undefined}
        size="sm"
      >
        <form onSubmit={handleRecordPayment}>
          <div style={{ display: "grid", gap: "12px" }}>
            <SelectField
              id="payment-mode"
              label="Payment Mode"
              value={paymentMode}
              onChange={(e) => setPaymentMode(e.target.value)}
              options={[
                { value: "GATEWAY", label: "Payment Gateway" },
                { value: "OFFLINE", label: "Offline Bank Transfer / Cheque" },
              ]}
            />
            <TextField
              id="payment-ref"
              label="Payment Reference / UTR Number"
              placeholder="e.g. UTR20261107009823"
              value={paymentRef}
              onChange={(e) => setPaymentRef(e.target.value)}
              required
            />
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
            <Button variant="secondary" onClick={() => setPayInvoice(null)} type="button">
              Cancel
            </Button>
            <Button type="submit" busy={recordingPay}>
              Confirm Payment
            </Button>
          </div>
        </form>
      </Modal>

      {/* Apply Discount Modal */}
      <Modal
        isOpen={!!discountInvoice}
        onClose={() => setDiscountInvoice(null)}
        kicker="DISCOUNT"
        title="Apply Discount (Product Owner only)"
        titleId="apply-discount-title"
        description={discountInvoice ? `Invoice ${discountInvoice.invoiceNumber} · Subtotal: ₹${discountInvoice.subtotalAmount}` : undefined}
        size="sm"
      >
        <form onSubmit={handleApplyDiscount}>
          <div style={{ display: "grid", gap: "12px" }}>
            <TextField
              id="discount-percent"
              label="Discount percentage (%)"
              type="number"
              value={discountPercent}
              onChange={(e) => setDiscountPercent(e.target.value)}
              required
            />
            <TextField
              id="discount-reason"
              label="Reason"
              value={discountReason}
              onChange={(e) => setDiscountReason(e.target.value)}
              required
            />
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
            <Button variant="secondary" onClick={() => setDiscountInvoice(null)} type="button">
              Cancel
            </Button>
            <Button type="submit" busy={applyingDiscount}>
              Apply Discount
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

export const InvoicesPage = InvoicesView;
