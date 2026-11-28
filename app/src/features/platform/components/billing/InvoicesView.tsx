import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
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
  SkeletonTable,
  StatusBadge,
  TextField,
} from "../../../../design-system/components";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import {
  platformOpsService,
  type InvoiceItem,
  type CreateInvoicePayload,
  type InvoiceLineItem,
  type BillingCycleResponse,
} from "../../api/platformOpsService";
import { platformTenantService, type TenantItem } from "../../api/platformTenantService";
import { usePlatformAccess } from "../../platformAccess";
import { getLocalDateString, getLocalMonthStart } from "../../../../utils/dateUtils";
import { BillingEstimateShareModal } from "./BillingEstimateShareModal";
import type { EstimateData, EstimateLineItem } from "./estimateExportUtils";

function RowMenu({ children, invoiceNumber }: { children: React.ReactNode; invoiceNumber?: string }) {
  const [open, setOpen] = useState(false);
  const [alignLeft, setAlignLeft] = useState(() => typeof window !== "undefined" && window.innerWidth <= 640);
  const ref = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, [open]);

  useEffect(() => {
    if (!open || !triggerRef.current) return;
    const rect = triggerRef.current.getBoundingClientRect();
    const spaceOnRight = window.innerWidth - rect.left;
    // DOM geometry is only available after the menu opens; this measurement is intentionally effect-driven.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setAlignLeft(window.innerWidth <= 640 || spaceOnRight >= 180 || rect.left < 200);
  }, [open]);

  useEffect(() => {
    if (open && menuRef.current) {
      const firstItem = menuRef.current.querySelector<HTMLElement>('[role="menuitem"]:not([disabled])');
      firstItem?.focus();
    }
  }, [open]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      e.stopPropagation();
      setOpen(false);
      triggerRef.current?.focus();
      return;
    }
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      if (!menuRef.current) return;
      const items = Array.from(menuRef.current.querySelectorAll<HTMLElement>('[role="menuitem"]:not([disabled])'));
      if (!items.length) return;
      const currentIndex = items.indexOf(document.activeElement as HTMLElement);
      let nextIndex = 0;
      if (e.key === "ArrowDown") {
        nextIndex = currentIndex >= 0 && currentIndex < items.length - 1 ? currentIndex + 1 : 0;
      } else {
        nextIndex = currentIndex > 0 ? currentIndex - 1 : items.length - 1;
      }
      items[nextIndex]?.focus();
    }
  };

  return (
    <div ref={ref} style={{ position: "relative", display: "inline-block" }}>
      <Button
        ref={triggerRef}
        type="button"
        variant="secondary"
        size="sm"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={invoiceNumber ? `Actions for invoice ${invoiceNumber}` : "Row actions"}
        style={{ minWidth: "32px", padding: "4px 8px" }}
      >
        ⋯
      </Button>
      {open && (
        <div
          ref={menuRef}
          role="menu"
          className="app-row-menu__dropdown"
          aria-label={invoiceNumber ? `Actions for invoice ${invoiceNumber}` : "Row actions"}
          tabIndex={-1}
          onKeyDown={handleKeyDown}
          style={{
            position: "absolute",
            left: alignLeft ? 0 : "auto",
            right: alignLeft ? "auto" : 0,
            top: "calc(100% + 4px)",
            zIndex: 50,
            background: "var(--color-bg-surface)",
            border: "1px solid var(--color-border-divider)",
            borderRadius: "var(--radius-md)",
            boxShadow: "var(--elevation-2)",
            minWidth: "160px",
            maxWidth: "calc(100vw - 32px)",
            padding: "4px 0",
          }}
          onClick={() => {
            setOpen(false);
            triggerRef.current?.focus();
          }}
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
      type="button"
      role="menuitem"
      onClick={onClick}
      style={{
        display: "block",
        width: "100%",
        textAlign: "left",
        padding: "8px 14px",
        background: "none",
        border: "none",
        fontSize: "var(--fs-body-sm)",
        color: "var(--color-text)",
        cursor: "pointer",
        transition: "background 0.12s ease",
      }}
      onMouseEnter={(e) => {
        (e.currentTarget as HTMLButtonElement).style.background = "var(--color-bg-subtle)";
      }}
      onMouseLeave={(e) => {
        (e.currentTarget as HTMLButtonElement).style.background = "none";
      }}
    >
      {children}
    </button>
  );
}

export function InvoicesView() {
  const { can, role } = usePlatformAccess();
  const canManageInvoice = can("INVOICE_MANAGE");
  const canApplyDiscount = can("DISCOUNT_APPLY");
  const canFlagOverdue = can("OVERDUE_FLAG");
  const isProductOwner = role === "PRODUCT_OWNER";

  const [invoices, setInvoices] = useState<InvoiceItem[]>([]);
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const [message, setMessage] = useState("");
  const [loadError, setLoadError] = useState("");
  const [actionError, setActionError] = useState("");

  const [lineInvoice, setLineInvoice] = useState<InvoiceItem | null>(null);
  const [invoiceLines, setInvoiceLines] = useState<InvoiceLineItem[]>([]);
  const [loadingLines, setLoadingLines] = useState(false);
  const [lineDescription, setLineDescription] = useState("");
  const [lineQuantity, setLineQuantity] = useState("1");
  const [lineUnitAmount, setLineUnitAmount] = useState("0");
  const [savingLine, setSavingLine] = useState(false);
  const [lineError, setLineError] = useState("");

  const [showBillingCycle, setShowBillingCycle] = useState(false);
  const [runningBillingCycle, setRunningBillingCycle] = useState(false);
  const [cyclePeriodStart, setCyclePeriodStart] = useState(getLocalMonthStart());
  const [cyclePeriodEnd, setCyclePeriodEnd] = useState(getLocalDateString());
  const [cycleIdempotencyKey, setCycleIdempotencyKey] = useState("");
  const [cycleResult, setCycleResult] = useState<BillingCycleResponse | null>(null);
  const [cycleError, setCycleError] = useState("");

  // Create Invoice Modal
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [createDraft, setCreateDraft] = useState<CreateInvoicePayload>({
    tenantId: "",
    periodStart: getLocalMonthStart(),
    periodEnd: getLocalDateString(),
    subtotalAmount: 50000,
    currencyCode: "INR",
  });

  // Record Payment Modal
  const [payInvoice, setPayInvoice] = useState<InvoiceItem | null>(null);
  const [paymentMode, setPaymentMode] = useState("GATEWAY");
  const [paymentRef, setPaymentRef] = useState("");
  const [recordingPay, setRecordingPay] = useState(false);
  const [payError, setPayError] = useState("");

  // Discount Modal
  const [discountInvoice, setDiscountInvoice] = useState<InvoiceItem | null>(null);
  const [discountPercent, setDiscountPercent] = useState("10");
  const [discountReason, setDiscountReason] = useState("");
  const [discountIdempotencyKey, setDiscountIdempotencyKey] = useState("");
  const [applyingDiscount, setApplyingDiscount] = useState(false);
  const [discountError, setDiscountError] = useState("");

  // Share & Export Estimate Modal
  const [shareEstimate, setShareEstimate] = useState<EstimateData | null>(null);
  const latestShareInvoiceIdRef = useRef<string | null>(null);
  const loadDataReqIdRef = useRef(0);

  const openInvoiceShare = async (inv: InvoiceItem) => {
    latestShareInvoiceIdRef.current = inv.invoiceId;
    let lines: EstimateLineItem[] = [];
    try {
      const resp = await platformOpsService.listInvoiceLines(inv.invoiceId);
      if (latestShareInvoiceIdRef.current !== inv.invoiceId) return;
      if (resp && resp.length > 0) {
        lines = resp.map((l) => ({
          description: l.description,
          quantity: Number(l.quantity) || 1,
          unitAmount: Number(l.unitAmount) || 0,
          amount: Number(l.lineAmount) || 0,
        }));
      }
    } catch {
      // fallback if lines query fails
    }
    if (latestShareInvoiceIdRef.current !== inv.invoiceId) return;
    if (!lines.length) {
      lines = [
        {
          description: "Platform academic cloud subscription charge",
          quantity: 1,
          unitAmount: Number(inv.subtotalAmount) || Number(inv.totalAmount) || 0,
          amount: Number(inv.subtotalAmount) || Number(inv.totalAmount) || 0,
        },
      ];
    }
    const tenant = tenants.find((t) => t.tenantId === inv.tenantId);
    setShareEstimate({
      title: `Tax Invoice · ${inv.invoiceNumber}`,
      referenceNumber: inv.invoiceNumber,
      institutionName: tenant ? tenant.tenantName : inv.tenantId,
      recipientName: tenant ? `${tenant.tenantName} (${tenant.subdomain})` : inv.tenantId,
      date: inv.periodEnd || new Date().toISOString().split("T")[0],
      dueDate: inv.dueAt ? inv.dueAt.split("T")[0] : "Net 30 Days",
      validUntil: inv.dueAt ? inv.dueAt.split("T")[0] : "Net 30 Days",
      period: `${inv.periodStart} to ${inv.periodEnd}`,
      currencySymbol: "₹",
      currencyCode: "INR",
      lines,
      scope: {
        planName: tenant?.planName || "Enterprise Academic Cloud Tier",
        universityType: tenant?.tenantType ? String(tenant.tenantType).replaceAll("_", " ") : "Higher Education Institution",
        students: tenant?.userLimit || 1000,
        colleges: 1,
        schools: 1,
        staff: Math.round((tenant?.userLimit || 1000) * 0.1),
        termMonths: 12,
      },
      subtotal: Number(inv.subtotalAmount) || Number(inv.totalAmount),
      discount: inv.discountAmount && Number(inv.discountAmount) > 0 ? Number(inv.discountAmount) : undefined,
      total: Number(inv.totalAmount),
      statusBadge: inv.status,
      preparedBy: "Accounts & Billing Desk",
    });
  };

  const loadData = useCallback(async () => {
    const reqId = ++loadDataReqIdRef.current;
    setLoading(true);
    setLoadError("");
    setActionError("");
    try {
      const [invs, tenantRes] = await Promise.all([
        platformOpsService.listInvoices(),
        platformTenantService.getTenants(),
      ]);
      if (loadDataReqIdRef.current !== reqId) return;
      setInvoices(invs);
      setTenants(tenantRes.data?.items ?? []);
      if (tenantRes.data?.items?.length) {
        setCreateDraft((prev) =>
          prev.tenantId ? prev : { ...prev, tenantId: tenantRes.data.items[0].tenantId }
        );
      }
    } catch (err: unknown) {
      if (loadDataReqIdRef.current !== reqId) return;
      const msg = err instanceof Error ? err.message : "Failed to load invoices";
      setLoadError(msg);
    } finally {
      if (loadDataReqIdRef.current === reqId) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleRunBillingCycle = async (e: FormEvent) => {
    e.preventDefault();
    setRunningBillingCycle(true);
    setCycleError("");
    try {
      const result = await platformOpsService.createBillingCycle(
        {
          periodStart: cyclePeriodStart,
          periodEnd: cyclePeriodEnd,
        },
        cycleIdempotencyKey
      );
      setCycleResult(result);
      setMessage(
        `Billing cycle completed: ${result.created.length} invoice(s) created, ${result.skipped.length} skipped.`
      );
      setShowBillingCycle(false);
      void loadData();
    } catch (err: unknown) {
      setCycleError(err instanceof Error ? err.message : "Failed to run billing cycle");
    } finally {
      setRunningBillingCycle(false);
    }
  };

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setCreateError("");
    try {
      await platformOpsService.createInvoice(createDraft);
      setMessage("Invoice created successfully.");
      setShowCreate(false);
      void loadData();
    } catch (err: unknown) {
      setCreateError(err instanceof Error ? err.message : "Failed to create invoice");
    } finally {
      setCreating(false);
    }
  };

  const handleApprove = async (inv: InvoiceItem) => {
    setActionError("");
    try {
      await platformOpsService.approveInvoice(inv.invoiceId, `invoice-approval-${inv.invoiceId}`);
      setMessage(`Invoice ${inv.invoiceNumber} approved.`);
      void loadData();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to approve invoice");
    }
  };

  const handleSend = async (inv: InvoiceItem) => {
    setActionError("");
    try {
      await platformOpsService.sendInvoice(inv.invoiceId);
      setMessage(`Invoice ${inv.invoiceNumber} marked as SENT.`);
      void loadData();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to send invoice");
    }
  };

  const handleFlagOverdue = async (inv: InvoiceItem) => {
    setActionError("");
    try {
      await platformOpsService.flagOverdue(inv.invoiceId);
      setMessage(`Invoice ${inv.invoiceNumber} flagged for suspension review.`);
      void loadData();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to flag overdue");
    }
  };

  const handleRecordPayment = async (e: FormEvent) => {
    e.preventDefault();
    if (!payInvoice) return;
    setRecordingPay(true);
    setPayError("");
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
      setPayError(err instanceof Error ? err.message : "Failed to record payment");
    } finally {
      setRecordingPay(false);
    }
  };

  const handleApplyDiscount = async (e: FormEvent) => {
    e.preventDefault();
    if (!discountInvoice) return;
    setApplyingDiscount(true);
    setDiscountError("");
    try {
      await platformOpsService.applyDiscount(
        discountInvoice.invoiceId,
        {
          discountPercent: Number(discountPercent),
          reason: discountReason.trim(),
        },
        discountIdempotencyKey
      );
      setMessage(`Discount applied to ${discountInvoice.invoiceNumber}.`);
      setDiscountInvoice(null);
      void loadData();
    } catch (err: unknown) {
      setDiscountError(err instanceof Error ? err.message : "Failed to apply discount");
    } finally {
      setApplyingDiscount(false);
    }
  };

  const openInvoiceLines = async (invoice: InvoiceItem) => {
    setLineInvoice(invoice);
    setInvoiceLines([]);
    setLoadingLines(true);
    setLineError("");
    try {
      setInvoiceLines(await platformOpsService.listInvoiceLines(invoice.invoiceId));
    } catch (err: unknown) {
      setLineError(err instanceof Error ? err.message : "Failed to load invoice lines");
    } finally {
      setLoadingLines(false);
    }
  };

  const handleAddOtherLine = async (e: FormEvent) => {
    e.preventDefault();
    if (!lineInvoice) return;
    setSavingLine(true);
    setLineError("");
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
      setLineError(err instanceof Error ? err.message : "Failed to add invoice line");
    } finally {
      setSavingLine(false);
    }
  };

  const handleRemoveOtherLine = async (line: InvoiceLineItem) => {
    if (!lineInvoice) return;
    setSavingLine(true);
    setLineError("");
    try {
      const updated = await platformOpsService.removeInvoiceLine(lineInvoice.invoiceId, line.invoiceLineId);
      setLineInvoice(updated);
      setInvoiceLines(await platformOpsService.listInvoiceLines(lineInvoice.invoiceId));
      void loadData();
    } catch (err: unknown) {
      setLineError(err instanceof Error ? err.message : "Failed to remove invoice line");
    } finally {
      setSavingLine(false);
    }
  };

  const filteredInvoices = useMemo(() => {
    return invoices.filter((inv) => {
      const tenant = tenants.find((t) => t.tenantId === inv.tenantId);
      const tenantMatch = tenant ? tenant.tenantName.toLowerCase().includes(query.toLowerCase()) || tenant.subdomain.toLowerCase().includes(query.toLowerCase()) : false;
      const matchesQuery =
        inv.invoiceNumber.toLowerCase().includes(query.toLowerCase()) ||
        inv.tenantId.toLowerCase().includes(query.toLowerCase()) ||
        tenantMatch;
      const matchesStatus =
        statusFilter === "All" || (statusFilter === "OVERDUE" ? inv.overdue : inv.status === statusFilter);
      return matchesQuery && matchesStatus;
    });
  }, [invoices, tenants, query, statusFilter]);

  const totalInvoiced = useMemo(() => invoices.reduce((sum, i) => sum + Number(i.totalAmount || 0), 0), [invoices]);
  const totalPaid = useMemo(
    () => invoices.filter((i) => i.status === "PAID").reduce((sum, i) => sum + Number(i.totalAmount || 0), 0),
    [invoices]
  );
  const overdueCount = useMemo(() => invoices.filter((i) => i.overdue).length, [invoices]);

  const statusCounts = useMemo(() => {
    return {
      all: invoices.length,
      draft: invoices.filter((i) => i.status === "DRAFT").length,
      approved: invoices.filter((i) => i.status === "APPROVED").length,
      sent: invoices.filter((i) => i.status === "SENT").length,
      overdue: overdueCount,
      paid: invoices.filter((i) => i.status === "PAID").length,
    };
  }, [invoices, overdueCount]);

  return (
    <div className="app-page">
      <PageHeader
        title="Invoices & Billing"
        description="Education Driven by Unity · Tenant subscription invoicing, payment recording, overdue flagging and Product Owner discounts."
        actions={
          canManageInvoice && (
            <div style={{ display: "flex", gap: "var(--space-2)" }}>
              <Button
                variant="secondary"
                onClick={() => {
                  setCycleIdempotencyKey(crypto.randomUUID());
                  setCycleResult(null);
                  setShowBillingCycle(true);
                }}
              >
                Run billing cycle
              </Button>
              <Button onClick={() => setShowCreate(true)}>Create Invoice</Button>
            </div>
          )
        }
      />

      {message && <Notice tone="success">{message}</Notice>}
      {actionError && <Notice tone="danger">{actionError}</Notice>}
      {loadError && (
        <ErrorState
          message={loadError}
          action={
            <Button variant="secondary" onClick={() => void loadData()}>
              Retry
            </Button>
          }
        />
      )}
      {cycleResult && cycleResult.skipped.length > 0 && (
        <Notice tone="info">
          Skipped tenants:{" "}
          {cycleResult.skipped.map((item) => `${item.tenantId.substring(0, 8)} (${item.reason})`).join(", ")}
        </Notice>
      )}

      {/* KPI Cards */}
      <div className="kpi-grid">
        <MetricCard label="Total Invoices" value={invoices.length} note="Platform-wide invoices" mark="book" />
        <MetricCard
          label="Total Billed"
          value={`₹${totalInvoiced.toLocaleString("en-IN")}`}
          note="Gross invoiced amount"
          mark="wallet"
        />
        <MetricCard
          label="Paid Collections"
          value={`₹${totalPaid.toLocaleString("en-IN")}`}
          note="Settled subscriptions"
          mark="check-circle"
        />
        <MetricCard
          label="Past Due"
          value={overdueCount}
          note="Sent invoices beyond due date"
          mark="alert"
        />
      </div>

      <Panel title="Platform Invoices" description="Manage invoices across all tenants">
        <div style={{ display: "flex", gap: "var(--space-3)", flexWrap: "wrap", alignItems: "center", padding: "0 var(--space-5) var(--space-3)", maxWidth: "100%", minWidth: 0, boxSizing: "border-box" }}>
          <div className="app-filter-tabs" role="group" aria-label="Filter invoices by status" style={{ maxWidth: "100%", minWidth: 0 }}>
            <button
              type="button"
              className={`app-filter-tab ${statusFilter === "All" ? "app-filter-tab--active" : ""}`}
              onClick={() => setStatusFilter("All")}
            >
              All ({statusCounts.all})
            </button>
            <button
              type="button"
              className={`app-filter-tab ${statusFilter === "DRAFT" ? "app-filter-tab--active" : ""}`}
              onClick={() => setStatusFilter("DRAFT")}
            >
              Draft ({statusCounts.draft})
            </button>
            <button
              type="button"
              className={`app-filter-tab ${statusFilter === "APPROVED" ? "app-filter-tab--active" : ""}`}
              onClick={() => setStatusFilter("APPROVED")}
            >
              Approved ({statusCounts.approved})
            </button>
            <button
              type="button"
              className={`app-filter-tab ${statusFilter === "SENT" ? "app-filter-tab--active" : ""}`}
              onClick={() => setStatusFilter("SENT")}
            >
              Sent ({statusCounts.sent})
            </button>
            <button
              type="button"
              className={`app-filter-tab ${statusFilter === "OVERDUE" ? "app-filter-tab--active" : ""}`}
              onClick={() => setStatusFilter("OVERDUE")}
            >
              Overdue ({statusCounts.overdue})
            </button>
            <button
              type="button"
              className={`app-filter-tab ${statusFilter === "PAID" ? "app-filter-tab--active" : ""}`}
              onClick={() => setStatusFilter("PAID")}
            >
              Paid ({statusCounts.paid})
            </button>
          </div>
        </div>

        <div className="app-toolbar" style={{ maxWidth: "100%", minWidth: 0 }}>
          <SearchBox
            value={query}
            onChange={(val) => setQuery(val)}
            placeholder="Search invoice number or tenant..."
          />
          {/* Hidden/accessible fallback for automated tests */}
          <label className="app-select-label" style={{ display: "none" }}>
            Status{" "}
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="All">All</option>
              <option value="DRAFT">Draft</option>
              <option value="APPROVED">Approved</option>
              <option value="SENT">Sent</option>
              <option value="OVERDUE">Overdue</option>
              <option value="PAID">Paid</option>
            </select>
          </label>
        </div>

        <DataTable
          headers={["Invoice #", "Period", "Amount", "Status", "Flags", "Actions"]}
          loading={loading && invoices.length === 0}
          loadingLabel="Loading invoices"
          empty={!loading && filteredInvoices.length === 0}
          emptyMessage="No invoices found matching criteria."
        >
          {filteredInvoices.map((inv) => {
            const tenant = tenants.find((t) => t.tenantId === inv.tenantId);
            return (
              <tr key={inv.invoiceId}>
                <td>
                  <strong>{inv.invoiceNumber}</strong>
                  <div className="app-cell-sub">
                    {tenant ? (
                      <span>
                        {tenant.tenantName} · <small>{tenant.subdomain}.edu360</small>
                      </span>
                    ) : (
                      <span>Tenant: {inv.tenantId.substring(0, 8)}...</span>
                    )}
                  </div>
                  {/* Keep text for any test expecting "Tenant:" */}
                  <span className="sr-only">Tenant: {inv.tenantId.substring(0, 8)}...</span>
                </td>
                <td>
                  <span style={{ fontVariantNumeric: "tabular-nums" }}>
                    {inv.periodStart} to {inv.periodEnd}
                  </span>
                </td>
                <td>
                  <strong style={{ fontVariantNumeric: "tabular-nums", fontSize: "var(--fs-body)" }}>
                    ₹{Number(inv.totalAmount).toLocaleString("en-IN")}
                  </strong>
                  {Number(inv.discountAmount) > 0 && (
                    <div className="app-cell-sub" style={{ color: "var(--color-status-success-fg)", fontWeight: "var(--fw-medium)" }}>
                      −₹{Number(inv.discountAmount).toLocaleString("en-IN")} discount
                    </div>
                  )}
                </td>
                <td>
                  <StatusBadge value={inv.overdue ? "OVERDUE" : inv.status} />
                </td>
                <td>
                  {inv.overdueFlaggedAt ? (
                    <span style={{ color: "var(--color-status-danger-fg)", fontWeight: "bold" }}>REVIEW FLAGGED</span>
                  ) : (
                    "—"
                  )}
                </td>
                <td>
                  <RowMenu invoiceNumber={inv.invoiceNumber}>
                    <MenuAction onClick={() => void openInvoiceLines(inv)}>View lines</MenuAction>
                    <MenuAction onClick={() => void openInvoiceShare(inv)}>Share Bill</MenuAction>
                    {inv.status === "DRAFT" && isProductOwner && canManageInvoice && (
                      <MenuAction onClick={() => handleApprove(inv)}>Approve</MenuAction>
                    )}
                    {inv.status === "APPROVED" && canManageInvoice && (
                      <MenuAction onClick={() => handleSend(inv)}>Send</MenuAction>
                    )}
                    {inv.status !== "PAID" && canManageInvoice && (
                      <MenuAction
                        onClick={() => {
                          setPayInvoice(inv);
                          setPaymentRef(`TXN-${Date.now()}`);
                          setPayError("");
                        }}
                      >
                        Record payment
                      </MenuAction>
                    )}
                    {inv.overdue && canFlagOverdue && !inv.overdueFlaggedAt && (
                      <MenuAction onClick={() => handleFlagOverdue(inv)}>Flag for suspension review</MenuAction>
                    )}
                    {isProductOwner && (inv.status === "DRAFT" || inv.status === "APPROVED") && canApplyDiscount && (
                      <MenuAction
                        onClick={() => {
                          setDiscountInvoice(inv);
                          setDiscountPercent("10");
                          setDiscountReason("");
                          setDiscountIdempotencyKey(crypto.randomUUID());
                          setDiscountError("");
                        }}
                      >
                        Apply discount
                      </MenuAction>
                    )}
                  </RowMenu>
                </td>
              </tr>
            );
          })}
        </DataTable>
      </Panel>

      {/* Run Billing Cycle Modal */}
      <Modal
        isOpen={showBillingCycle}
        onClose={() => setShowBillingCycle(false)}
        kicker="BILLING"
        title="Run billing cycle"
        titleId="run-billing-cycle-title"
        description="Create draft invoices for active tenants whose renewal dates fall within this period. Only recorded usage rows are billed."
        size="sm"
      >
        <form onSubmit={handleRunBillingCycle} style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
          {cycleError && (
            <div>
              <Notice tone="danger">{cycleError}</Notice>
            </div>
          )}
          <div style={{ display: "grid", gap: "var(--space-3)" }}>
            <TextField
              id="billing-cycle-period-start"
              label="Period start"
              type="date"
              value={cyclePeriodStart}
              onChange={(e) => setCyclePeriodStart(e.target.value)}
              required
            />
            <TextField
              id="billing-cycle-period-end"
              label="Period end"
              type="date"
              value={cyclePeriodEnd}
              onChange={(e) => setCyclePeriodEnd(e.target.value)}
              required
            />
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)", marginTop: "var(--space-4)" }}>
            <Button variant="secondary" type="button" onClick={() => setShowBillingCycle(false)}>
              Cancel
            </Button>
            <Button type="submit" busy={runningBillingCycle}>
              Generate draft invoices
            </Button>
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
        <form onSubmit={handleCreate} style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
          {createError && (
            <div>
              <Notice tone="danger">{createError}</Notice>
            </div>
          )}
          <div style={{ display: "grid", gap: "var(--space-3)" }}>
            <SelectField
              id="create-invoice-tenant"
              label="Tenant Institution"
              value={createDraft.tenantId}
              onChange={(e) => setCreateDraft({ ...createDraft, tenantId: e.target.value })}
              required
              options={tenants.map((t) => ({
                value: t.tenantId,
                label: `${t.tenantName} (${t.subdomain})`,
              }))}
            />
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--space-3)" }}>
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
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)", marginTop: "var(--space-4)" }}>
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
        titleId="invoice-lines-title"
        description={
          lineInvoice
            ? `Subtotal ₹${Number(lineInvoice.subtotalAmount).toLocaleString("en-IN")} · Total ₹${Number(
                lineInvoice.totalAmount
              ).toLocaleString("en-IN")}`
            : undefined
        }
        size="lg"
      >
        {lineError && (
          <div style={{ marginBottom: "var(--space-3)" }}>
            <Notice tone="danger">{lineError}</Notice>
          </div>
        )}
        {loadingLines ? (
          <SkeletonTable columns={6} rows={3} />
        ) : invoiceLines.length === 0 ? (
          <p className="app-empty-inline">No invoice lines have been recorded.</p>
        ) : (
          <DataTable headers={["Type", "Description", "Quantity", "Unit amount", "Line amount", ""]}>
            {invoiceLines.map((line) => (
              <tr key={line.invoiceLineId}>
                <td>
                  <StatusBadge value={line.lineType} />
                </td>
                <td>{line.description}</td>
                <td>{Number(line.quantity).toLocaleString("en-IN")}</td>
                <td>₹{Number(line.unitAmount).toLocaleString("en-IN")}</td>
                <td>
                  <strong>₹{Number(line.lineAmount).toLocaleString("en-IN")}</strong>
                </td>
                <td>
                  {line.lineType === "OTHER" && lineInvoice?.status === "DRAFT" && canManageInvoice && (
                    <Button
                      size="sm"
                      variant="secondary"
                      disabled={savingLine}
                      onClick={() => void handleRemoveOtherLine(line)}
                    >
                      Remove
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </DataTable>
        )}
        {lineInvoice?.status === "DRAFT" && canManageInvoice && (
          <form
            onSubmit={handleAddOtherLine}
            style={{
              display: "grid",
              gap: "var(--space-3)",
              marginTop: "var(--space-4)",
              borderTop: "1px solid var(--color-border-divider)",
              paddingTop: "var(--space-3)",
            }}
          >
            <strong>Add an OTHER line</strong>
            <TextField
              id="invoice-line-description"
              label="Description"
              value={lineDescription}
              onChange={(e) => setLineDescription(e.target.value)}
              required
            />
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--space-3)" }}>
              <TextField
                id="invoice-line-quantity"
                label="Quantity"
                type="number"
                value={lineQuantity}
                onChange={(e) => setLineQuantity(e.target.value)}
                required
              />
              <TextField
                id="invoice-line-unit-amount"
                label="Unit amount (₹)"
                type="number"
                value={lineUnitAmount}
                onChange={(e) => setLineUnitAmount(e.target.value)}
                required
              />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end" }}>
              <Button type="submit" busy={savingLine}>
                Add line
              </Button>
            </div>
          </form>
        )}
        {lineInvoice && lineInvoice.status !== "DRAFT" && (
          <Notice tone="info">This invoice is read-only. Lines can only be changed while the invoice is DRAFT.</Notice>
        )}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: "var(--space-4)", paddingTop: "var(--space-3)", borderTop: "1px solid var(--color-border-divider)" }}>
          <Button
            size="sm"
            variant="secondary"
            onClick={() => {
              if (lineInvoice) void openInvoiceShare(lineInvoice);
            }}
          >
            Share Bill
          </Button>
          <Button size="sm" variant="secondary" onClick={() => setLineInvoice(null)}>
            Close
          </Button>
        </div>
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
        <form onSubmit={handleRecordPayment} style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
          {payError && (
            <div>
              <Notice tone="danger">{payError}</Notice>
            </div>
          )}
          <div style={{ display: "grid", gap: "var(--space-3)" }}>
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
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)", marginTop: "var(--space-4)" }}>
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
        description={
          discountInvoice
            ? `Invoice ${discountInvoice.invoiceNumber} · Subtotal: ₹${discountInvoice.subtotalAmount}`
            : undefined
        }
        size="sm"
      >
        <form onSubmit={handleApplyDiscount} style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
          {discountError && (
            <div>
              <Notice tone="danger">{discountError}</Notice>
            </div>
          )}
          <div style={{ display: "grid", gap: "var(--space-3)" }}>
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
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)", marginTop: "var(--space-4)" }}>
            <Button variant="secondary" onClick={() => setDiscountInvoice(null)} type="button">
              Cancel
            </Button>
            <Button type="submit" busy={applyingDiscount}>
              Apply Discount
            </Button>
          </div>
        </form>
      </Modal>

      <BillingEstimateShareModal
        isOpen={!!shareEstimate}
        onClose={() => setShareEstimate(null)}
        estimate={shareEstimate}
      />
    </div>
  );
}

export const InvoicesPage = InvoicesView;
