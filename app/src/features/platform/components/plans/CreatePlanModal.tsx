/* eslint-disable react-refresh/only-export-components */
import { useMemo, useState, type FormEvent } from "react";
import { Button, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";
import type { CommercialCatalogItemDto, PlanComponentDraft } from "../../api/platformTenantService";

export interface CreatePlanDraft {
  planName: string;
  pricingType: string;
  priceAmount: number;
  currencyCode: string;
  userLimit: number;
  storageLimitGb: number;
  smsLimit: number;
  apiLimit: number;
  maxInstitutions: number;
  maxColleges: number;
  maxSchools: number;
  maxStudents: number;
  maxStaff: number;
  customDomainAllowed: boolean;
  brandingColorsAllowed: boolean;
  billingCycleMonths: number;
  smsUnitAmount: number;
  storageUnitAmountPerGb: number;
  setupFeeAmount: number;
  discountType: "NONE" | "PERCENT" | "FIXED";
  discountValue: number;
  contractDurationMonths: number;
  gracePeriodDays: number;
  renewalMode: "AUTOMATIC" | "MANUAL";
  selectedModules?: string[];
}

export const AVAILABLE_SYSTEM_MODULES = [
  { code: "USERS_ACCESS", name: "Users, Roles & Access", category: "Core Academic", phase: 1 },
  { code: "INSTITUTION_SETUP", name: "Academic Structure & Setup", category: "Core Academic", phase: 1 },
  { code: "TIMETABLE", name: "Timetable & Scheduling", category: "Core Academic", phase: 1 },
  { code: "ATTENDANCE", name: "Attendance Management", category: "Core Academic", phase: 1 },
  { code: "EXAMINATIONS", name: "Examinations & Tests", category: "Core Academic", phase: 1 },
  { code: "RESULTS", name: "Results & Gradebooks", category: "Core Academic", phase: 1 },
  { code: "ASSIGNMENTS", name: "Assignments & Homework", category: "Core Academic", phase: 1 },
  { code: "ADMISSIONS", name: "Admissions & Enrolment", category: "Core Academic", phase: 2 },
  { code: "FEES", name: "Fees & Invoicing", category: "Operations", phase: 1 },
  { code: "LEAVE", name: "Staff & Student Leave", category: "Operations", phase: 1 },
  { code: "STAFF_RECORDS", name: "Staff & HR Records", category: "Operations", phase: 1 },
  { code: "PAYROLL", name: "Payroll Management", category: "Operations", phase: 2 },
  { code: "COMMUNICATION", name: "Parent & Campus Messaging", category: "Communication", phase: 1 },
  { code: "COMMUNITY", name: "Community Feed & Events", category: "Communication", phase: 1 },
  { code: "COMPLAINTS", name: "Complaints & Grievances", category: "Communication", phase: 1 },
  { code: "STUDENT_NOTES", name: "Student Notes & Welfare", category: "Communication", phase: 1 },
  { code: "LIBRARY", name: "Library & Circulation", category: "Campus Facilities", phase: 2 },
  { code: "PLACEMENT", name: "Placement & Careers", category: "Campus Facilities", phase: 2 },
  { code: "HOSTEL", name: "Hostel & Accommodation", category: "Campus Facilities", phase: 3 },
  { code: "TRANSPORT", name: "Campus Transport & Fleet", category: "Campus Facilities", phase: 3 },
  { code: "ALUMNI", name: "Alumni Network", category: "Campus Facilities", phase: 3 },
  { code: "RESEARCH", name: "Research & Publications", category: "Campus Facilities", phase: 3 },
  { code: "REPORTS", name: "Reports & Analytics", category: "Analytics", phase: 1 },
];

export interface CreatePlanModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
  createDraft: CreatePlanDraft;
  setCreateDraft: React.Dispatch<React.SetStateAction<CreatePlanDraft>>;
  creating: boolean;
  createError: string;
  canConfigurePricing: boolean;
  isEditing: boolean;
  catalog: CommercialCatalogItemDto[];
  catalogLoading: boolean;
  catalogError: string;
  components: PlanComponentDraft[];
  setComponents: React.Dispatch<React.SetStateAction<PlanComponentDraft[]>>;
}

export function CreatePlanModal({
  isOpen,
  onClose,
  onSubmit,
  createDraft,
  setCreateDraft,
  creating,
  createError,
  canConfigurePricing,
  isEditing,
  catalog,
  catalogLoading,
  catalogError,
  components,
  setComponents,
}: CreatePlanModalProps) {
  const [selectedCatalogId, setSelectedCatalogId] = useState("");
  const [previewStudents, setPreviewStudents] = useState(100);
  const [previewModules, setPreviewModules] = useState(1);
  const [activeTab, setActiveTab] = useState<"all" | "pricing" | "limits" | "components" | "terms">("all");
  const [showEstimateBreakdown, setShowEstimateBreakdown] = useState(false);

  const selectedModules = createDraft.selectedModules || [];

  const toggleModule = (code: string) => {
    const next = selectedModules.includes(code)
      ? selectedModules.filter((c) => c !== code)
      : [...selectedModules, code];
    setCreateDraft((curr) => ({ ...curr, selectedModules: next }));
    setPreviewModules(Math.max(1, next.length));
  };

  const selectAllCore = () => {
    const core = AVAILABLE_SYSTEM_MODULES.filter((m) => m.category === "Core Academic").map((m) => m.code);
    setCreateDraft((curr) => ({ ...curr, selectedModules: core }));
    setPreviewModules(core.length);
  };

  const selectAllModules = () => {
    const all = AVAILABLE_SYSTEM_MODULES.map((m) => m.code);
    setCreateDraft((curr) => ({ ...curr, selectedModules: all }));
    setPreviewModules(all.length);
  };

  const clearAllModules = () => {
    setCreateDraft((curr) => ({ ...curr, selectedModules: [] }));
    setPreviewModules(1);
  };

  const defaultModuleItems: CommercialCatalogItemDto[] = useMemo(() => {
    return AVAILABLE_SYSTEM_MODULES.map((mod) => ({
      catalogItemId: `mod-${mod.code.toLowerCase()}`,
      itemCode: mod.code,
      itemName: mod.name,
      category: "MODULE",
      unitCode: "MODULE",
      pricingBasis: "PER_MODULE_YEAR",
      unitAmount: createDraft.priceAmount || 499,
      unitsPerQuantity: 1,
      billingIntervalMonths: 12,
      moduleCode: mod.code,
      description: `${mod.name} platform module`,
      available: true,
      rowVersion: 1,
    }));
  }, [createDraft.priceAmount]);

  const combinedCatalog = useMemo(() => {
    if (catalog && catalog.length > 0) {
      return catalog;
    }
    return defaultModuleItems;
  }, [catalog, defaultModuleItems]);

  const selectedIds = useMemo(() => new Set(components.map((component) => component.catalogItemId)), [components]);
  const availableItems = combinedCatalog.filter((item) => item.available && !selectedIds.has(item.catalogItemId));

  const componentSubtotal = components.reduce((total, component) => {
    if (component.enabled === false) return total;
    const item = combinedCatalog.find((candidate) => candidate.catalogItemId === component.catalogItemId);
    if (!item || item.pricingBasis === "USAGE") return total;
    const quantity = Number(component.includedQuantity) || 0;
    const unitAmount = component.unitAmountOverride ?? item.unitAmount;
    let pricedQuantity = quantity;
    if (item.pricingBasis === "PER_STUDENT_YEAR") pricedQuantity *= previewStudents * createDraft.billingCycleMonths / 12;
    else if (item.pricingBasis === "PER_MODULE_YEAR") pricedQuantity *= createDraft.billingCycleMonths / 12;
    else if (item.pricingBasis === "RECURRING_FIXED" || item.pricingBasis === "PER_UNIT") {
      pricedQuantity *= createDraft.billingCycleMonths / (item.billingIntervalMonths || 1);
    }
    return total + pricedQuantity * unitAmount;
  }, 0);

  const baseAmount = createDraft.pricingType === "PER_STUDENT_YEAR"
    ? createDraft.priceAmount * previewStudents * createDraft.billingCycleMonths / 12
    : createDraft.pricingType === "PER_MODULE"
      ? createDraft.priceAmount * previewModules * createDraft.billingCycleMonths / 12
      : createDraft.priceAmount * createDraft.billingCycleMonths;
  const setupAmount = Math.max(0, createDraft.setupFeeAmount);
  const subtotal = Math.max(0, baseAmount) + componentSubtotal + setupAmount;
  const discount = createDraft.discountType === "PERCENT"
    ? subtotal * Math.max(0, createDraft.discountValue) / 100
    : createDraft.discountType === "FIXED" ? Math.max(0, createDraft.discountValue) : 0;
  const estimatedTotal = Math.max(0, subtotal - Math.min(subtotal, discount));

  const currency = new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: createDraft.currencyCode || "INR",
    maximumFractionDigits: 2,
  });

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker={isEditing ? "EDIT SUBSCRIPTION PLAN" : "NEW SUBSCRIPTION PLAN"}
      title={isEditing ? "Edit subscription plan" : "Add subscription plan"}
      titleId="plan-create-title"
      description="Configure plan pricing, limits, billing cycle and commercial allowances. Pricing is controlled by the Product Owner."
      size="lg"
    >
      <form onSubmit={onSubmit} style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
        {canConfigurePricing && (activeTab === "all" || activeTab === "pricing") && (
          <section
            className="app-plan-estimate"
            aria-live="polite"
            style={{
              borderRadius: "var(--radius-lg)",
              padding: "var(--space-4)",
              background: "var(--color-bg-surface)",
              border: "var(--border-width) solid var(--color-border-divider)",
              boxShadow: "var(--elevation-card)",
            }}
          >
            <div className="app-plan-estimate__heading" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div>
                <strong style={{ display: "block", fontSize: "var(--fs-body)", color: "var(--color-text)" }}>
                  Live subscription calculation
                </strong>
                <p style={{ margin: "var(--space-1) 0 0", fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>
                  Recalculates based on billing term, base pricing, package add-ons, and discounts.
                </p>
              </div>
              <div style={{ textAlign: "right" }}>
                <span style={{ display: "block", fontSize: "11px", textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--color-text-muted)" }}>
                  Est. Total ({createDraft.billingCycleMonths}m)
                </span>
                <strong className="app-plan-estimate__total" style={{ color: "var(--color-action-primary)" }}>
                  {currency.format(estimatedTotal)}
                </strong>
              </div>
            </div>

            {(createDraft.pricingType === "PER_STUDENT_YEAR" || createDraft.pricingType === "PER_MODULE") && (
              <div className="app-form-grid" style={{ marginTop: "var(--space-2)" }}>
                {createDraft.pricingType === "PER_STUDENT_YEAR" && (
                  <TextField
                    id="plan-preview-students"
                    label="Estimate at student count"
                    type="number"
                    min="0"
                    value={String(previewStudents)}
                    onChange={(event) => setPreviewStudents(Math.max(0, Number(event.target.value) || 0))}
                  />
                )}
                {createDraft.pricingType === "PER_MODULE" && (
                  <TextField
                    id="plan-preview-modules"
                    label="Estimate at module count"
                    type="number"
                    min="1"
                    value={String(previewModules)}
                    onChange={(event) => setPreviewModules(Math.max(1, Number(event.target.value) || 1))}
                  />
                )}
              </div>
            )}

            <div style={{ marginTop: "var(--space-2)" }}>
              <button
                type="button"
                onClick={() => setShowEstimateBreakdown(!showEstimateBreakdown)}
                style={{
                  background: "none",
                  border: "none",
                  padding: 0,
                  color: "var(--color-action-primary)",
                  fontSize: "var(--fs-body-xs)",
                  fontWeight: "var(--fw-medium)",
                  cursor: "pointer",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "var(--space-1)",
                }}
              >
                {showEstimateBreakdown ? "▲ Hide breakdown" : "▼ Show line item breakdown"}
              </button>
            </div>

            {showEstimateBreakdown && (
              <dl className="app-plan-estimate__breakdown" style={{ marginTop: "var(--space-3)" }}>
                <div><dt>Plan base fee</dt><dd>{currency.format(baseAmount)}</dd></div>
                <div><dt>Included package items</dt><dd>{currency.format(componentSubtotal)}</dd></div>
                {setupAmount > 0 && <div><dt>One-time setup fee</dt><dd>{currency.format(setupAmount)}</dd></div>}
                {discount > 0 && <div><dt>Applied discount</dt><dd>−{currency.format(Math.min(subtotal, discount))}</dd></div>}
                <div className="app-plan-estimate__due">
                  <dt>Net payable estimate ({createDraft.billingCycleMonths} months)</dt>
                  <dd>{currency.format(estimatedTotal)}</dd>
                </div>
              </dl>
            )}
          </section>
        )}

        {/* Section Navigation Tabs */}
        <div className="app-modal-nav" role="tablist" aria-label="Plan configuration sections" style={{ flexWrap: "wrap", overflowX: "visible" }}>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "all"}
            className={`app-modal-nav__tab ${activeTab === "all" ? "app-modal-nav__tab--active" : ""}`}
            onClick={() => setActiveTab("all")}
          >
            All Sections
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "pricing"}
            className={`app-modal-nav__tab ${activeTab === "pricing" ? "app-modal-nav__tab--active" : ""}`}
            onClick={() => setActiveTab("pricing")}
          >
            Pricing &amp; Billing
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "limits"}
            className={`app-modal-nav__tab ${activeTab === "limits" ? "app-modal-nav__tab--active" : ""}`}
            onClick={() => setActiveTab("limits")}
          >
            Capacity Limits
          </button>
          {canConfigurePricing && (
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === "components"}
              className={`app-modal-nav__tab ${activeTab === "components" ? "app-modal-nav__tab--active" : ""}`}
              onClick={() => setActiveTab("components")}
            >
              Package Items ({components.length})
            </button>
          )}
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "terms"}
            className={`app-modal-nav__tab ${activeTab === "terms" ? "app-modal-nav__tab--active" : ""}`}
            onClick={() => setActiveTab("terms")}
          >
            {canConfigurePricing ? "Terms &amp; Renewal" : "Features &amp; Terms"}
          </button>
        </div>

        {/* Tab 1: General & Pricing */}
        <div style={{ display: activeTab === "all" || activeTab === "pricing" ? "flex" : "none", flexDirection: "column", gap: "var(--space-3)" }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", borderBottom: "1px solid var(--color-border-divider)", paddingBottom: "var(--space-2)" }}>
            <strong style={{ fontSize: "var(--fs-body)", color: "var(--color-text)" }}>1. Plan Name &amp; Base Pricing</strong>
            <span style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>Core identity and subscription rates</span>
          </div>

          <TextField
            id="plan-name"
            label="Plan Name"
            required
            value={createDraft.planName}
            onChange={(e) => setCreateDraft({ ...createDraft, planName: e.target.value })}
            placeholder="e.g. Standard School Plan"
          />

          {canConfigurePricing ? (
            <fieldset style={{ border: 0, padding: 0, margin: 0, minWidth: 0, display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
              <div className="app-form-grid">
                <SelectField
                  id="plan-pricing-type"
                  label="Pricing Type"
                  value={createDraft.pricingType}
                  onChange={(e) => setCreateDraft({ ...createDraft, pricingType: e.target.value })}
                  options={[
                    { value: "PER_STUDENT_YEAR", label: "Per Student Per Year" },
                    { value: "FLAT", label: "Flat Fee" },
                    { value: "PER_MODULE", label: "Per Module" },
                    { value: "COMBO", label: "Combo Package" },
                    { value: "CUSTOM", label: "Custom Package" },
                  ]}
                />
                <TextField
                  id="plan-price"
                  label="Price Amount (₹)"
                  type="number"
                  value={String(createDraft.priceAmount)}
                  onChange={(e) => setCreateDraft({ ...createDraft, priceAmount: Number(e.target.value) })}
                />
              </div>

              {createDraft.pricingType === "PER_MODULE" && (
                <div
                  style={{
                    padding: "var(--space-4)",
                    background: "var(--color-bg-surface)",
                    borderRadius: "var(--radius-lg)",
                    border: "var(--border-width) solid var(--color-border-divider)",
                    boxShadow: "var(--elevation-card)",
                    display: "flex",
                    flexDirection: "column",
                    gap: "var(--space-3)",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "var(--space-2)" }}>
                    <div>
                      <strong style={{ fontSize: "var(--fs-body)", color: "var(--color-text)", display: "block" }}>
                        Choose Included Modules
                      </strong>
                      <span style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>
                        Select the modules enabled in this plan. Pricing scales by the number of selected modules.
                      </span>
                    </div>
                    <div style={{ display: "flex", gap: "var(--space-2)", alignItems: "center", flexWrap: "wrap" }}>
                      <span style={{ fontSize: "var(--fs-body-xs)", fontWeight: "var(--fw-semibold)", color: "var(--color-action-primary)" }}>
                        {selectedModules.length} module{selectedModules.length === 1 ? "" : "s"} chosen
                      </span>
                      <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={selectAllCore}
                      >
                        Core (8)
                      </Button>
                      <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={selectAllModules}
                      >
                        All (23)
                      </Button>
                      <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={clearAllModules}
                      >
                        Clear
                      </Button>
                    </div>
                  </div>

                  <div
                    style={{
                      display: "grid",
                      gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))",
                      gap: "var(--space-2)",
                      maxHeight: "260px",
                      overflowY: "auto",
                      padding: "var(--space-2)",
                      background: "var(--color-bg-subtle)",
                      borderRadius: "var(--radius-md)",
                      border: "var(--border-width) solid var(--color-border-divider)",
                    }}
                  >
                    {AVAILABLE_SYSTEM_MODULES.map((mod) => {
                      const isChecked = selectedModules.includes(mod.code);
                      return (
                        <label
                          key={mod.code}
                          style={{
                            display: "flex",
                            alignItems: "flex-start",
                            gap: "var(--space-2)",
                            padding: "var(--space-2) var(--space-3)",
                            background: isChecked ? "var(--color-bg-brand-soft)" : "var(--color-bg-surface)",
                            border: `1px solid ${isChecked ? "var(--color-action-primary)" : "var(--color-border-divider)"}`,
                            borderRadius: "var(--radius-md)",
                            cursor: "pointer",
                            transition: "all 0.15s ease",
                          }}
                        >
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => toggleModule(mod.code)}
                            style={{ marginTop: "var(--space-1)" }}
                          />
                          <div style={{ display: "grid", gap: "var(--space-1)", minWidth: 0 }}>
                            <strong style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text)", lineHeight: 1.2 }}>
                              {mod.name}
                            </strong>
                            <small style={{ fontSize: "10px", color: "var(--color-text-muted)" }}>
                              {mod.code} · {mod.category}
                            </small>
                          </div>
                        </label>
                      );
                    })}
                  </div>
                </div>
              )}

              <div className="app-form-grid">
                <SelectField
                  id="plan-billing-cycle"
                  label="Billing cycle"
                  value={String(createDraft.billingCycleMonths)}
                  onChange={(e) => setCreateDraft({ ...createDraft, billingCycleMonths: Number(e.target.value) })}
                  options={[
                    { value: "1", label: "Monthly (1 month)" },
                    { value: "3", label: "Quarterly (3 months)" },
                    { value: "6", label: "Half-yearly (6 months)" },
                    { value: "12", label: "Yearly (12 months)" },
                    ...Array.from({ length: 24 }, (_, index) => ({
                      value: String(index + 13),
                      label: `${index + 13} months`,
                    })),
                  ]}
                />
                <TextField
                  id="plan-setup-fee"
                  label="One-time setup fee (₹)"
                  type="number"
                  min="0"
                  value={String(createDraft.setupFeeAmount)}
                  onChange={(e) => setCreateDraft({ ...createDraft, setupFeeAmount: Number(e.target.value) })}
                />
              </div>

              <div className="app-form-grid">
                <TextField
                  id="plan-sms-unit-amount"
                  label="SMS overage unit rate (₹)"
                  type="number"
                  value={String(createDraft.smsUnitAmount)}
                  onChange={(e) => setCreateDraft({ ...createDraft, smsUnitAmount: Number(e.target.value) })}
                />
                <TextField
                  id="plan-storage-unit-amount"
                  label="Storage overage unit rate per GB (₹)"
                  type="number"
                  value={String(createDraft.storageUnitAmountPerGb)}
                  onChange={(e) => setCreateDraft({ ...createDraft, storageUnitAmountPerGb: Number(e.target.value) })}
                />
              </div>
            </fieldset>
          ) : (
            <>
              <div className="app-form-grid">
                <SelectField
                  id="plan-billing-cycle"
                  label="Billing cycle"
                  value={String(createDraft.billingCycleMonths)}
                  onChange={(e) => setCreateDraft({ ...createDraft, billingCycleMonths: Number(e.target.value) })}
                  options={[
                    { value: "1", label: "Monthly" },
                    { value: "3", label: "Quarterly" },
                    { value: "6", label: "Half-yearly" },
                    { value: "12", label: "Yearly" },
                  ]}
                />
              </div>
              <Notice tone="info">Pricing, currency and pricing type can only be changed by the Product Owner.</Notice>
            </>
          )}
        </div>

        {/* Tab 2: Capacity & Limits */}
        <div style={{ display: activeTab === "all" || activeTab === "limits" ? "flex" : "none", flexDirection: "column", gap: "var(--space-4)" }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", borderBottom: "1px solid var(--color-border-divider)", paddingBottom: "var(--space-2)" }}>
            <div>
              <strong style={{ fontSize: "var(--fs-body)", color: "var(--color-text)", display: "block" }}>Capacity &amp; Resource Limits</strong>
              <span style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>Enrolment ceilings and cloud usage allowances</span>
            </div>
            <span style={{ fontSize: "11px", padding: "var(--space-1) var(--space-2)", background: "var(--color-bg-subtle)", borderRadius: "var(--radius-pill)", color: "var(--color-text-muted)", border: "1px solid var(--color-border-divider)" }}>
              0 = Unlimited
            </span>
          </div>

          {/* Group 1: Campus Hierarchy & Enrolment */}
          <div
            style={{
              padding: "var(--space-4)",
              background: "var(--color-bg-surface)",
              borderRadius: "var(--radius-lg)",
              border: "var(--border-width) solid var(--color-border-divider)",
              boxShadow: "var(--elevation-card)",
              display: "flex",
              flexDirection: "column",
              gap: "var(--space-3)",
            }}
          >
            <div>
              <strong style={{ fontSize: "var(--fs-body-sm)", color: "var(--color-text)", display: "block" }}>
                Campus Hierarchy &amp; Enrolment
              </strong>
              <span style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>
                Institution, college, school and active user maximums (set 0 for unlimited)
              </span>
            </div>
            <div className="app-form-grid">
              <TextField
                id="plan-institution-limit"
                label="Maximum institutions"
                type="number"
                value={String(createDraft.maxInstitutions)}
                onChange={(e) => setCreateDraft({ ...createDraft, maxInstitutions: Number(e.target.value) })}
              />
              <TextField
                id="plan-college-limit"
                label="Maximum colleges"
                type="number"
                min="0"
                value={String(createDraft.maxColleges)}
                onChange={(e) => setCreateDraft({ ...createDraft, maxColleges: Number(e.target.value) })}
              />
              <TextField
                id="plan-school-limit"
                label="Maximum schools"
                type="number"
                min="0"
                value={String(createDraft.maxSchools)}
                onChange={(e) => setCreateDraft({ ...createDraft, maxSchools: Number(e.target.value) })}
              />
            </div>
            <div className="app-form-grid">
              <TextField
                id="plan-student-limit"
                label="Maximum active students"
                type="number"
                min="0"
                value={String(createDraft.maxStudents)}
                onChange={(e) => setCreateDraft({ ...createDraft, maxStudents: Number(e.target.value) })}
              />
              <TextField
                id="plan-staff-limit"
                label="Maximum active staff"
                type="number"
                min="0"
                value={String(createDraft.maxStaff)}
                onChange={(e) => setCreateDraft({ ...createDraft, maxStaff: Number(e.target.value) })}
              />
            </div>
          </div>

          {/* Group 2: Cloud Resources & Quotas */}
          <div
            style={{
              padding: "var(--space-4)",
              background: "var(--color-bg-surface)",
              borderRadius: "var(--radius-lg)",
              border: "var(--border-width) solid var(--color-border-divider)",
              boxShadow: "var(--elevation-card)",
              display: "flex",
              flexDirection: "column",
              gap: "var(--space-3)",
            }}
          >
            <div>
              <strong style={{ fontSize: "var(--fs-body-sm)", color: "var(--color-text)", display: "block" }}>
                Cloud Resources &amp; System Quotas
              </strong>
              <span style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>
                User limits, storage volumes, SMS quotas, and API consumption ceilings
              </span>
            </div>
            <div className="app-form-grid">
              <TextField
                id="plan-user-limit"
                label="User Limit"
                type="number"
                value={String(createDraft.userLimit)}
                onChange={(e) => setCreateDraft({ ...createDraft, userLimit: Number(e.target.value) })}
              />
              <TextField
                id="plan-storage-limit"
                label="Storage Limit (GB)"
                type="number"
                value={String(createDraft.storageLimitGb)}
                onChange={(e) => setCreateDraft({ ...createDraft, storageLimitGb: Number(e.target.value) })}
              />
            </div>
            <div className="app-form-grid">
              <TextField
                id="plan-sms-limit"
                label="SMS allowance"
                type="number"
                value={String(createDraft.smsLimit)}
                onChange={(e) => setCreateDraft({ ...createDraft, smsLimit: Number(e.target.value) })}
              />
              <TextField
                id="plan-api-limit"
                label="API allowance"
                type="number"
                value={String(createDraft.apiLimit)}
                onChange={(e) => setCreateDraft({ ...createDraft, apiLimit: Number(e.target.value) })}
              />
            </div>
          </div>
        </div>

        {/* Tab 3: Package Items & Commercial Catalog (Product Owner only) */}
        {canConfigurePricing && (
          <div style={{ display: activeTab === "all" || activeTab === "components" ? "flex" : "none", flexDirection: "column", gap: "var(--space-3)" }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", borderBottom: "1px solid var(--color-border-divider)", paddingBottom: "var(--space-2)" }}>
              <strong style={{ fontSize: "var(--fs-body)", color: "var(--color-text)" }}>Package Items &amp; Add-ons</strong>
              <span style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>Bundle specific modules or capacity packs</span>
            </div>

            <section className="app-plan-components" aria-labelledby="plan-components-title">
              <div className="app-plan-components__heading">
                <div>
                  <h3 id="plan-components-title">Bundled catalog items</h3>
                  <p>Select catalog items to include in this plan tier. Override unit rates as needed.</p>
                </div>
              </div>

              {catalogLoading ? <Notice tone="info">Loading available package items…</Notice> : null}
              {catalogError && !catalogError.includes("404") ? <Notice tone="danger">{catalogError}</Notice> : null}
              {catalogError && catalogError.includes("404") && (
                <div style={{ padding: "var(--space-3)", background: "var(--color-bg-surface)", border: "var(--border-width) solid var(--color-border-divider)", borderRadius: "var(--radius-md)", fontSize: "var(--fs-body-sm)", color: "var(--color-text-muted)" }}>
                  Commercial catalog is not configured on this server. You can create plans with base limits and add catalog modules later.
                </div>
              )}

              {!catalogLoading && (!catalogError || catalogError.includes("404")) && (
                <div className="app-plan-components__add">
                  <SelectField
                    id="plan-component-item"
                    label="Add a catalog item"
                    value={selectedCatalogId}
                    onChange={(event) => setSelectedCatalogId(event.target.value)}
                    options={[
                      { value: "", label: "Choose an item" },
                      ...availableItems.map((item) => ({ value: item.catalogItemId, label: `${item.itemName} · ${item.category}` })),
                    ]}
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={!selectedCatalogId}
                    onClick={() => {
                      if (!selectedCatalogId) return;
                      const item = combinedCatalog.find((candidate) => candidate.catalogItemId === selectedCatalogId);
                      setComponents((current) => [...current, {
                        catalogItemId: selectedCatalogId,
                        includedQuantity: item?.category === "MODULE" ? 1 : 1,
                        enabled: true,
                      }]);
                      setSelectedCatalogId("");
                    }}
                  >
                    Add item
                  </Button>
                </div>
              )}

              {components.map((component, idx) => {
                const item = combinedCatalog.find((candidate) => candidate.catalogItemId === component.catalogItemId);
                if (!item) return null;
                const isEnabled = component.enabled !== false;
                return (
                  <div
                    className={`app-plan-component-row ${!isEnabled ? "app-plan-component-row--disabled" : ""}`}
                    key={component.catalogItemId || `comp-${idx}`}
                  >
                    <div className="app-plan-component-row__header">
                      <label className="app-plan-component-row__name">
                        <input
                          type="checkbox"
                          checked={isEnabled}
                          onChange={(event) => setComponents((current) => current.map((entry) =>
                            entry.catalogItemId === component.catalogItemId ? { ...entry, enabled: event.target.checked } : entry))}
                        />
                        <div className="app-plan-component-row__title-wrap">
                          <strong className="app-plan-component-row__title">{item.itemName}</strong>
                          <span className="app-plan-component-row__badge">
                            {item.pricingBasis.replaceAll("_", " ")} · {currency.format(component.unitAmountOverride ?? item.unitAmount)} / unit
                          </span>
                        </div>
                      </label>
                      <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={() => setComponents((current) => current.filter((entry) => entry.catalogItemId !== component.catalogItemId))}
                      >
                        Remove
                      </Button>
                    </div>

                    <div className="app-plan-component-row__fields">
                      <TextField
                        id={`component-quantity-${item.catalogItemId}`}
                        label="Quantity"
                        type="number"
                        min="0"
                        value={String(component.includedQuantity)}
                        disabled={item.category === "MODULE"}
                        onChange={(event) => setComponents((current) => current.map((entry) =>
                          entry.catalogItemId === component.catalogItemId ? { ...entry, includedQuantity: Math.max(0, Number(event.target.value) || 0) } : entry))}
                      />
                      <TextField
                        id={`component-price-${item.catalogItemId}`}
                        label="Unit price override (₹)"
                        type="number"
                        min="0"
                        value={component.unitAmountOverride == null ? "" : String(component.unitAmountOverride)}
                        onChange={(event) => setComponents((current) => current.map((entry) =>
                          entry.catalogItemId === component.catalogItemId
                            ? { ...entry, unitAmountOverride: event.target.value === "" ? null : Math.max(0, Number(event.target.value) || 0) }
                            : entry))}
                      />
                    </div>
                  </div>
                );
              })}
              {!components.length && !catalogLoading && <p className="app-empty-inline">No package items selected yet.</p>}
            </section>
          </div>
        )}

        {/* Tab 4: Terms, Discounts & Feature Toggles */}
        <div style={{ display: activeTab === "all" || activeTab === "terms" ? "flex" : "none", flexDirection: "column", gap: "var(--space-3)" }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", borderBottom: "1px solid var(--color-border-divider)", paddingBottom: "var(--space-2)" }}>
            <strong style={{ fontSize: "var(--fs-body)", color: "var(--color-text)" }}>
              {canConfigurePricing ? "Commercial Terms & Features" : "Features & Allowance Toggles"}
            </strong>
            <span style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>Domain, branding and renewal rules</span>
          </div>

          <div className="app-form-grid" style={{ padding: "var(--space-4)", background: "var(--color-bg-surface)", borderRadius: "var(--radius-lg)", border: "var(--border-width) solid var(--color-border-divider)", boxShadow: "var(--elevation-card)" }}>
            <label className="app-form-field" style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", margin: 0, cursor: "pointer" }}>
              <input
                type="checkbox"
                checked={createDraft.customDomainAllowed}
                onChange={(e) => setCreateDraft({ ...createDraft, customDomainAllowed: e.target.checked })}
              />
              <span>
                <strong style={{ display: "block", color: "var(--color-text)" }}>Allow custom domain</strong>
                <small style={{ color: "var(--color-text-muted)" }}>Permit institutions to bind their own vanity domain</small>
              </span>
            </label>
            <label className="app-form-field" style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", margin: 0, cursor: "pointer" }}>
              <input
                type="checkbox"
                checked={createDraft.brandingColorsAllowed}
                onChange={(e) => setCreateDraft({ ...createDraft, brandingColorsAllowed: e.target.checked })}
              />
              <span>
                <strong style={{ display: "block", color: "var(--color-text)" }}>Allow branding color overrides</strong>
                <small style={{ color: "var(--color-text-muted)" }}>Permit institution-specific logo and palette customisation</small>
              </span>
            </label>
          </div>

          {canConfigurePricing && (
            <>
              <div className="app-form-grid">
                <SelectField
                  id="plan-discount-type"
                  label="Plan discount"
                  value={createDraft.discountType}
                  onChange={(e) => setCreateDraft({ ...createDraft, discountType: e.target.value as "NONE" | "PERCENT" | "FIXED" })}
                  options={[
                    { value: "NONE", label: "No discount" },
                    { value: "PERCENT", label: "Percentage (%)" },
                    { value: "FIXED", label: "Fixed amount (₹)" },
                  ]}
                />
                <TextField
                  id="plan-discount-value"
                  label="Discount value"
                  type="number"
                  min="0"
                  value={String(createDraft.discountValue)}
                  onChange={(e) => setCreateDraft({ ...createDraft, discountValue: Number(e.target.value) })}
                />
              </div>

              <div className="app-form-grid">
                <TextField
                  id="plan-contract-duration"
                  label="Contract length (months)"
                  type="number"
                  min="1"
                  max="360"
                  value={String(createDraft.contractDurationMonths)}
                  onChange={(e) => setCreateDraft({ ...createDraft, contractDurationMonths: Number(e.target.value) })}
                />
                <TextField
                  id="plan-grace-period"
                  label="Grace period (days)"
                  type="number"
                  min="0"
                  max="365"
                  value={String(createDraft.gracePeriodDays)}
                  onChange={(e) => setCreateDraft({ ...createDraft, gracePeriodDays: Number(e.target.value) })}
                />
                <SelectField
                  id="plan-renewal-mode"
                  label="Renewal mode"
                  value={createDraft.renewalMode}
                  onChange={(e) => setCreateDraft({ ...createDraft, renewalMode: e.target.value as "AUTOMATIC" | "MANUAL" })}
                  options={[
                    { value: "AUTOMATIC", label: "Schedule automatically" },
                    { value: "MANUAL", label: "Manual scheduling" },
                  ]}
                />
              </div>
            </>
          )}
        </div>

        {createError && <Notice tone="danger">{createError}</Notice>}

        <footer style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "var(--space-3)", marginTop: "var(--space-4)", flexWrap: "wrap" }}>
          {canConfigurePricing && (
            <div style={{ display: "inline-flex", alignItems: "center", gap: "var(--space-2)", padding: "var(--space-2) var(--space-3)", background: "var(--color-bg-surface)", border: "var(--border-width) solid var(--color-border-divider)", borderRadius: "var(--radius-md)", boxShadow: "var(--elevation-card)" }}>
              <span style={{ fontSize: "var(--fs-caption)", color: "var(--color-text-muted)" }}>
                Est. Total ({createDraft.billingCycleMonths}m):
              </span>
              <strong style={{ fontSize: "var(--fs-body)", color: "var(--color-action-primary)" }}>
                {currency.format(estimatedTotal)}
              </strong>
            </div>
          )}
          <div style={{ display: "flex", gap: "var(--space-2)", marginLeft: "auto" }}>
            <Button variant="secondary" onClick={onClose} type="button">
              Cancel
            </Button>
            <Button type="submit" busy={creating}>
              {isEditing ? "Save plan" : "Create plan"}
            </Button>
          </div>
        </footer>
      </form>
    </Modal>
  );
}
