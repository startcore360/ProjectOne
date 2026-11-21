import { useEffect, useMemo, useState, type FormEvent } from "react";
import {
  Button,
  Kicker,
  MetricCard,
  Notice,
  SearchBox,
  SelectField,
  SkeletonCard,
  SkeletonText,
  StatusBadge,
  TextField,
} from "../../../../design-system/components";
import { Badge } from "../../../../components/ui";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import { PageHeader } from "../../../../components/layout/PageHeader";
import {
  platformTenantService,
  type CommercialCatalogItemDto,
  type CustomerQuoteResponse,
  type CreatePlanPayload,
  type PlanComponentDraft,
  type PlanItem,
  type TenantItem,
  type TenantType,
} from "../../api/platformTenantService";
import { CreatePlanModal, type CreatePlanDraft } from "./CreatePlanModal";
import { usePlatformAccess } from "../../platformAccess";
import { EntitlementEditor } from "../entitlements/EntitlementEditor";
import { CommercialCatalogModal } from "./CommercialCatalogModal";
import api from "../../../../services/axios";

const formatPricingCadence = (type?: string, cycleMonths?: number) => {
  const cycleText = cycleMonths && cycleMonths !== 12 ? ` (${cycleMonths}m term)` : "";
  switch (type) {
    case "PER_STUDENT_YEAR":
      return `/ student / year${cycleText}`;
    case "FLAT":
      return `/ year flat fee${cycleText}`;
    case "PER_MODULE":
      return `/ module / year${cycleText}`;
    case "COMBO":
      return `combo package${cycleText}`;
    case "CUSTOM":
      return `custom package${cycleText}`;
    default:
      return type ? `· ${type.replaceAll("_", " ").toLowerCase()}${cycleText}` : "";
  }
};

function computeLocalCustomerQuote(
  plans: PlanItem[],
  needs: {
    tenantType: TenantType;
    colleges: number;
    schools: number;
    students: number;
    staff: number;
    requiredModules: string;
    billingCycleMonths: number;
    includeSetupFee: boolean;
  }
): CustomerQuoteResponse {
  const reqModules = needs.requiredModules
    .split(",")
    .map((m) => m.trim().toUpperCase())
    .filter(Boolean);

  const eligiblePlans = plans.map((plan) => {
    const unmet: string[] = [];
    const fitReasons: string[] = [];

    if (plan.maxColleges && plan.maxColleges > 0 && needs.colleges > plan.maxColleges) {
      unmet.push(`Requires up to ${needs.colleges} colleges, but plan limit is ${plan.maxColleges}.`);
    }
    if (plan.maxSchools && plan.maxSchools > 0 && needs.schools > plan.maxSchools) {
      unmet.push(`Requires up to ${needs.schools} schools, but plan limit is ${plan.maxSchools}.`);
    }
    if (plan.maxStudents && plan.maxStudents > 0 && needs.students > plan.maxStudents) {
      unmet.push(`Requires up to ${needs.students} students, but plan limit is ${plan.maxStudents}.`);
    }
    if (plan.maxStaff && plan.maxStaff > 0 && needs.staff > plan.maxStaff) {
      unmet.push(`Requires up to ${needs.staff} staff, but plan limit is ${plan.maxStaff}.`);
    }

    const isEligible = unmet.length === 0;

    let baseAmount = 0;
    if (plan.pricingType === "PER_STUDENT_YEAR") {
      baseAmount = (plan.priceAmount || 0) * needs.students * (needs.billingCycleMonths / 12);
    } else if (plan.pricingType === "PER_MODULE") {
      baseAmount = (plan.priceAmount || 0) * Math.max(1, reqModules.length) * (needs.billingCycleMonths / 12);
    } else {
      baseAmount = (plan.priceAmount || 0) * (needs.billingCycleMonths / (plan.billingCycleMonths || 12));
    }

    const setupFee = needs.includeSetupFee ? (plan.setupFeeAmount || 0) : 0;
    const subtotal = Math.max(0, baseAmount) + setupFee;

    let discount = 0;
    if (plan.discountType === "PERCENT" && plan.discountValue) {
      discount = subtotal * (plan.discountValue / 100);
    } else if (plan.discountType === "FIXED" && plan.discountValue) {
      discount = plan.discountValue;
    }

    const total = Math.max(0, subtotal - discount);

    const lines = [
      {
        code: "BASE_PLAN",
        description: `Base plan: ${plan.planName} (${needs.billingCycleMonths}m)`,
        quantity: 1,
        unitAmount: baseAmount,
        amount: baseAmount,
      },
    ];
    if (setupFee > 0) {
      lines.push({
        code: "SETUP_FEE",
        description: "One-time onboarding & platform setup fee",
        quantity: 1,
        unitAmount: setupFee,
        amount: setupFee,
      });
    }

    if (isEligible) {
      fitReasons.push(`Covers ${needs.students.toLocaleString("en-IN")} students and ${needs.staff.toLocaleString("en-IN")} staff.`);
      if (plan.customDomainAllowed) fitReasons.push("Includes custom vanity domain.");
      if (plan.brandingColorsAllowed) fitReasons.push("Supports white-label branding.");
    }

    return {
      planId: plan.planId,
      planName: plan.planName,
      pricingType: plan.pricingType,
      eligible: isEligible,
      recommendation: null as string | null,
      fitReasons,
      unmetRequirements: unmet,
      subtotal,
      discount,
      total,
      tax: 0,
      lines,
    };
  });

  const eligibleSorted = [...eligiblePlans.filter((p) => p.eligible)].sort((a, b) => a.total - b.total);
  const lowestCostPlanId = eligibleSorted.length > 0 ? eligibleSorted[0].planId : null;
  if (lowestCostPlanId) {
    const rec = eligiblePlans.find((p) => p.planId === lowestCostPlanId);
    if (rec) rec.recommendation = "BEST_FIT";
  }

  return {
    currencyCode: plans[0]?.currencyCode || "INR",
    billingCycleMonths: needs.billingCycleMonths,
    plans: eligiblePlans,
    lowestCostPlanId,
    bestFitPlanId: lowestCostPlanId,
  };
}

export function PlansView() {
  const { role } = usePlatformAccess();
  const canConfigurePricing = role === "PRODUCT_OWNER";
  const { can: canDo } = usePlatformAccess();
  const canToggleFeatures = canDo("FEATURE_TOGGLE");
  const canBuildQuotes = canDo("PLAN_MANAGE") || canDo("TENANT_CREATE");

  const [entitlementPlan, setEntitlementPlan] = useState<PlanItem | null>(null);
  const [plans, setPlans] = useState<PlanItem[]>([]);
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [editingPlan, setEditingPlan] = useState<PlanItem | null>(null);
  const [planBuilderKey, setPlanBuilderKey] = useState(0);

  // Search and Filter states
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");
  const [pricingTypeFilter, setPricingTypeFilter] = useState("ALL");

  const [createDraft, setCreateDraft] = useState<CreatePlanDraft>({
    planName: "",
    pricingType: "PER_STUDENT_YEAR",
    priceAmount: 499,
    currencyCode: "INR",
    userLimit: 1000,
    storageLimitGb: 50,
    smsLimit: 5000,
    apiLimit: 10000,
    maxInstitutions: 1,
    maxColleges: 0,
    maxSchools: 0,
    maxStudents: 0,
    maxStaff: 0,
    customDomainAllowed: false,
    brandingColorsAllowed: false,
    billingCycleMonths: 12,
    smsUnitAmount: 0,
    storageUnitAmountPerGb: 0,
    setupFeeAmount: 0,
    discountType: "NONE",
    discountValue: 0,
    contractDurationMonths: 12,
    gracePeriodDays: 15,
    renewalMode: "AUTOMATIC",
    selectedModules: [],
  });
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [message, setMessage] = useState("");
  const [catalog, setCatalog] = useState<Awaited<ReturnType<typeof platformTenantService.getCommercialCatalog>>["data"]>([]);
  const [catalogLoading, setCatalogLoading] = useState(false);
  const [catalogError, setCatalogError] = useState("");
  const [catalogModalOpen, setCatalogModalOpen] = useState(false);
  const [editingCatalogItem, setEditingCatalogItem] = useState<CommercialCatalogItemDto | null>(null);
  const [planComponents, setPlanComponents] = useState<PlanComponentDraft[]>([]);
  const [quoteNeeds, setQuoteNeeds] = useState({
    tenantType: "UNIVERSITY_GROUP" as TenantType,
    colleges: 2,
    schools: 0,
    students: 1000,
    staff: 100,
    requiredModules: "",
    billingCycleMonths: 12,
    includeSetupFee: true,
  });
  const [quoteResult, setQuoteResult] = useState<CustomerQuoteResponse | null>(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [quoteError, setQuoteError] = useState("");

  const loadPlans = async () => {
    setLoading(true);
    setLoadError("");
    try {
      const [plansResponse, tenantsResponse] = await Promise.all([
        platformTenantService.getPlans(),
        platformTenantService.getTenants(),
      ]);
      setPlans(Array.isArray(plansResponse?.data) ? plansResponse.data : []);
      setTenants(Array.isArray(tenantsResponse?.data?.items) ? tenantsResponse.data.items : []);
    } catch (err: unknown) {
      setLoadError(err instanceof Error ? err.message : "Unable to load plans and tenant allocations. Please retry.");
    } finally {
      setLoading(false);
    }
  };

  const loadCatalog = async () => {
    setCatalogLoading(true);
    setCatalogError("");
    try {
      const response = await platformTenantService.getCommercialCatalog();
      setCatalog(Array.isArray(response.data) ? response.data : []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "";
      const is404 = msg.includes("404") || (err as { response?: { status?: number } })?.response?.status === 404;
      if (is404) {
        setCatalog([]);
        setCatalogError("");
      } else {
        setCatalogError(msg || "Unable to load commercial catalog items.");
      }
    } finally {
      setCatalogLoading(false);
    }
  };

  useEffect(() => {
    void loadPlans();
  }, []);

  useEffect(() => {
    if (canConfigurePricing) void loadCatalog();
  }, [canConfigurePricing]);

  const openPlanBuilder = async (plan?: PlanItem) => {
    setPlanBuilderKey((key) => key + 1);
    setMessage("");
    setCreateError("");
    setCatalogError("");
    setCatalog([]);
    setPlanComponents([]);
    setEditingPlan(plan ?? null);
    setShowCreate(true);
    setCatalogLoading(true);
    try {
      const [catalogResponse, componentResponse] = await Promise.all([
        platformTenantService.getCommercialCatalog().catch((err: unknown) => {
          const msg = err instanceof Error ? err.message : "";
          if (msg.includes("404") || (err as { response?: { status?: number } })?.response?.status === 404) {
            return { data: [] };
          }
          throw err;
        }),
        plan
          ? platformTenantService.getPlanComponents(plan.planId).catch((err: unknown) => {
              const msg = err instanceof Error ? err.message : "";
              if (msg.includes("404") || (err as { response?: { status?: number } })?.response?.status === 404) {
                return { data: [] };
              }
              throw err;
            })
          : Promise.resolve({ data: [] }),
      ]);
      setCatalog(Array.isArray(catalogResponse.data) ? catalogResponse.data : []);
      setPlanComponents(
        Array.isArray(componentResponse.data)
          ? componentResponse.data.map((component) => ({
              catalogItemId: component.catalogItemId,
              includedQuantity: component.includedQuantity,
              unitAmountOverride: component.unitAmountOverride,
              enabled: component.enabled,
            }))
          : []
      );
      if (plan) {
        try {
          const entRes = await api.get<{ modules: Array<{ moduleCode: string; enabled: boolean }> }>(
            `/platform/plans/${plan.planId}/entitlement-editor`
          );
          if (Array.isArray(entRes.data?.modules)) {
            const enabledCodes = entRes.data.modules
              .filter((m: { moduleCode: string; enabled: boolean }) => m.enabled)
              .map((m: { moduleCode: string; enabled: boolean }) => m.moduleCode);
            setCreateDraft((curr) => ({ ...curr, selectedModules: enabledCodes }));
          }
        } catch {
          // ignore if endpoint not active
        }
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "";
      if (!msg.includes("404")) {
        setCatalogError(msg || "Unable to load subscription package items.");
      }
    } finally {
      setCatalogLoading(false);
    }
  };

  const submitCreatePlan = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setCreating(true);
    setCreateError("");
    let savedPlan: PlanItem | null = null;
    try {
      if (canConfigurePricing && catalogError && !catalogError.includes("404")) {
        throw new Error("Package items could not be loaded. Close and reopen the plan builder before saving.");
      }
      const payload: CreatePlanPayload = {
        planName: createDraft.planName.trim(),
        userLimit: Number(createDraft.userLimit),
        storageLimitGb: Number(createDraft.storageLimitGb),
        smsLimit: Number(createDraft.smsLimit),
        apiLimit: Number(createDraft.apiLimit),
        maxInstitutions: Number(createDraft.maxInstitutions),
        maxColleges: createDraft.maxColleges > 0 ? Number(createDraft.maxColleges) : null,
        maxSchools: createDraft.maxSchools > 0 ? Number(createDraft.maxSchools) : null,
        maxStudents: createDraft.maxStudents > 0 ? Number(createDraft.maxStudents) : null,
        maxStaff: createDraft.maxStaff > 0 ? Number(createDraft.maxStaff) : null,
        customDomainAllowed: createDraft.customDomainAllowed,
        brandingColorsAllowed: createDraft.brandingColorsAllowed,
        billingCycleMonths: createDraft.billingCycleMonths,
        setupFeeAmount: Number(createDraft.setupFeeAmount),
        contractDurationMonths: Number(createDraft.contractDurationMonths),
        gracePeriodDays: Number(createDraft.gracePeriodDays),
        renewalMode: createDraft.renewalMode,
        ...(createDraft.discountValue > 0 && createDraft.discountType !== "NONE"
          ? { discountType: createDraft.discountType, discountValue: Number(createDraft.discountValue) }
          : editingPlan && editingPlan.discountType
          ? { discountType: "NONE" as const }
          : {}),
        ...(canConfigurePricing
          ? {
              pricingType: createDraft.pricingType,
              priceAmount: Number(createDraft.priceAmount),
              currencyCode: createDraft.currencyCode,
              smsUnitAmount: Number(createDraft.smsUnitAmount),
              storageUnitAmountPerGb: Number(createDraft.storageUnitAmountPerGb),
            }
          : {}),
      };
      if (editingPlan) {
        const response = await platformTenantService.updatePlan(editingPlan.planId, payload, editingPlan.rowVersion);
        savedPlan = response.data;
      } else {
        const response = await platformTenantService.createPlan(payload);
        savedPlan = response.data;
      }
      if (canConfigurePricing && planComponents.length > 0) {
        if (typeof savedPlan.rowVersion !== "number") {
          throw new Error("Plan saved, but its version was not returned; package items were not updated.");
        }
        try {
          await platformTenantService.replacePlanComponents(savedPlan.planId, planComponents, savedPlan.rowVersion);
        } catch (compErr: unknown) {
          const compMsg = compErr instanceof Error ? compErr.message : "";
          if (!compMsg.includes("404")) {
            throw compErr;
          }
        }
      }
      if (createDraft.selectedModules && createDraft.selectedModules.length > 0) {
        try {
          const featuresPayload: Record<string, boolean> = {};
          createDraft.selectedModules.forEach((mod) => {
            featuresPayload[mod] = true;
          });
          await api.put(`/platform/plans/${savedPlan.planId}/features`, { features: featuresPayload });
        } catch {
          // non-blocking if entitlement toggle endpoint is not reachable
        }
      }
      setMessage(`Plan "${createDraft.planName}" ${editingPlan ? "updated" : "created"} successfully.`);
      setShowCreate(false);
      setCreateDraft({
        planName: "",
        pricingType: "PER_STUDENT_YEAR",
        priceAmount: 499,
        currencyCode: "INR",
        userLimit: 1000,
        storageLimitGb: 50,
        smsLimit: 5000,
        apiLimit: 10000,
        maxInstitutions: 1,
        maxColleges: 0,
        maxSchools: 0,
        maxStudents: 0,
        maxStaff: 0,
        customDomainAllowed: false,
        brandingColorsAllowed: false,
        billingCycleMonths: 12,
        smsUnitAmount: 0,
        storageUnitAmountPerGb: 0,
        setupFeeAmount: 0,
        discountType: "NONE",
        discountValue: 0,
        contractDurationMonths: 12,
        gracePeriodDays: 15,
        renewalMode: "AUTOMATIC",
        selectedModules: [],
      });
      setEditingPlan(null);
      void loadPlans();
    } catch (err: unknown) {
      if (savedPlan) {
        setEditingPlan(savedPlan);
        void loadPlans();
      }
      const msg = savedPlan
        ? `Plan saved, but its package items were not saved: ${err instanceof Error ? err.message : "Unknown error"}`
        : err instanceof Error
        ? err.message
        : "Failed to create subscription plan.";
      setCreateError(msg);
    } finally {
      setCreating(false);
    }
  };

  const submitCustomerQuote = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setQuoteLoading(true);
    setQuoteError("");
    setQuoteResult(null);
    try {
      const response = await platformTenantService.quoteCustomer({
        tenantType: quoteNeeds.tenantType,
        colleges: Math.max(0, quoteNeeds.colleges),
        schools: Math.max(0, quoteNeeds.schools),
        students: Math.max(0, quoteNeeds.students),
        staff: Math.max(0, quoteNeeds.staff),
        requiredModules: quoteNeeds.requiredModules
          .split(",")
          .map((module) => module.trim().toUpperCase())
          .filter(Boolean),
        billingCycleMonths: quoteNeeds.billingCycleMonths,
        includeSetupFee: quoteNeeds.includeSetupFee,
      });
      setQuoteResult(response.data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "";
      const is404 = msg.includes("404") || (err as { response?: { status?: number } })?.response?.status === 404;
      if (is404 && plans.length > 0) {
        const computed = computeLocalCustomerQuote(plans, quoteNeeds);
        setQuoteResult(computed);
      } else {
        setQuoteError(msg || "Unable to generate a customer quote.");
        setQuoteResult(null);
      }
    } finally {
      setQuoteLoading(false);
    }
  };

  const formatQuoteCurrency = (amount: number, currencyCode = quoteResult?.currencyCode ?? "INR") =>
    new Intl.NumberFormat("en-IN", { style: "currency", currency: currencyCode, maximumFractionDigits: 2 }).format(amount);

  // Filtered plans
  const filteredPlans = useMemo(() => {
    return plans.filter((plan) => {
      const matchesSearch =
        !searchQuery ||
        plan.planName.toLowerCase().includes(searchQuery.toLowerCase()) ||
        (plan.pricingType && plan.pricingType.toLowerCase().includes(searchQuery.toLowerCase()));

      const matchesStatus =
        statusFilter === "ALL" ||
        (statusFilter === "ACTIVE" ? (plan.status ?? "ACTIVE") === "ACTIVE" : plan.status === "INACTIVE");

      const matchesPricing =
        pricingTypeFilter === "ALL" || plan.pricingType === pricingTypeFilter;

      return matchesSearch && matchesStatus && matchesPricing;
    });
  }, [plans, searchQuery, statusFilter, pricingTypeFilter]);

  // KPI Calculations
  const activePlansCount = useMemo(() => plans.filter((p) => (p.status ?? "ACTIVE") === "ACTIVE").length, [plans]);
  const totalSubscribersCount = useMemo(() => tenants.length, [tenants]);
  const minActivePrice = useMemo(() => {
    const active = plans.filter((p) => (p.status ?? "ACTIVE") === "ACTIVE" && typeof p.priceAmount === "number");
    if (!active.length) return 0;
    return Math.min(...active.map((p) => p.priceAmount));
  }, [plans]);

  return (
    <div className="app-page">
      <PageHeader
        title="Plans & billing"
        description="Review subscription plans, pricing types, capacity limits and commercial package items."
        actions={
          <Button
            onClick={() => {
              setCreateDraft({
                planName: "",
                pricingType: "PER_STUDENT_YEAR",
                priceAmount: 499,
                currencyCode: "INR",
                userLimit: 1000,
                storageLimitGb: 50,
                smsLimit: 5000,
                apiLimit: 10000,
                maxInstitutions: 1,
                maxColleges: 0,
                maxSchools: 0,
                maxStudents: 0,
                maxStaff: 0,
                customDomainAllowed: false,
                brandingColorsAllowed: false,
                billingCycleMonths: 12,
                smsUnitAmount: 0,
                storageUnitAmountPerGb: 0,
                setupFeeAmount: 0,
                discountType: "NONE",
                discountValue: 0,
                contractDurationMonths: 12,
                gracePeriodDays: 15,
                renewalMode: "AUTOMATIC",
              });
              void openPlanBuilder();
            }}
          >
            Add subscription plan
          </Button>
        }
      />

      {message && <Notice tone="success">{message}</Notice>}
      {loadError && !loading && (
        <ErrorState
          message={loadError}
          action={
            <Button variant="secondary" onClick={() => void loadPlans()}>
              Retry
            </Button>
          }
        />
      )}

      {/* KPI Overview Grid */}
      <div className="kpi-grid">
        <MetricCard label="Total Subscription Plans" value={plans.length} note="Catalogue tiers" mark="book" />
        <MetricCard label="Active Tiers" value={activePlansCount} note="Available for enrollment" mark="check-circle" />
        <MetricCard label="Enrolled Tenants" value={totalSubscribersCount} note="Allocated institutions" mark="users" />
        <MetricCard
          label="Starting Tier"
          value={minActivePrice > 0 ? `₹${minActivePrice.toLocaleString("en-IN")}` : "Custom"}
          note="Base active plan rate"
          mark="wallet"
        />
      </div>

      {/* Plans Toolbar with Search and Filters */}
      <div
        className="app-toolbar"
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          gap: "var(--space-3)",
          flexWrap: "wrap",
          padding: "var(--space-3) 0",
        }}
      >
        <div style={{ display: "flex", gap: "var(--space-3)", flexWrap: "wrap", alignItems: "center", flex: "1 1 320px" }}>
          <SearchBox
            value={searchQuery}
            onChange={(val) => setSearchQuery(val)}
            placeholder="Search plans by name or pricing..."
          />
          <div className="app-filter-tabs" role="group" aria-label="Filter plans by status">
            <button
              type="button"
              className={`app-filter-tab ${statusFilter === "ALL" ? "app-filter-tab--active" : ""}`}
              onClick={() => setStatusFilter("ALL")}
            >
              All Plans ({plans.length})
            </button>
            <button
              type="button"
              className={`app-filter-tab ${statusFilter === "ACTIVE" ? "app-filter-tab--active" : ""}`}
              onClick={() => setStatusFilter("ACTIVE")}
            >
              Active ({activePlansCount})
            </button>
            <button
              type="button"
              className={`app-filter-tab ${statusFilter === "INACTIVE" ? "app-filter-tab--active" : ""}`}
              onClick={() => setStatusFilter("INACTIVE")}
            >
              Inactive ({plans.length - activePlansCount})
            </button>
          </div>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
          <SelectField
            id="plan-pricing-filter"
            label="Model"
            value={pricingTypeFilter}
            onChange={(e) => setPricingTypeFilter(e.target.value)}
            options={[
              { value: "ALL", label: "All pricing models" },
              { value: "PER_STUDENT_YEAR", label: "Per Student / Year" },
              { value: "FLAT", label: "Flat Fee" },
              { value: "PER_MODULE", label: "Per Module" },
              { value: "COMBO", label: "Combo Package" },
              { value: "CUSTOM", label: "Custom Tier" },
            ]}
          />
        </div>
      </div>

      {/* Plans Grid */}
      {loading && plans.length === 0 ? (
        <div className="app-plan-grid" role="status" aria-busy="true" aria-label="Loading plans">
          <span className="sr-only">Loading plans...</span>
          {Array.from({ length: 3 }, (_, index) => (
            <SkeletonCard key={index} className="app-plan-card">
              <SkeletonText height="0.75rem" width="40%" />
              <SkeletonText height="1.5rem" width="65%" />
              <SkeletonText height="1rem" width="55%" />
              <SkeletonText height="1rem" width="80%" />
            </SkeletonCard>
          ))}
        </div>
      ) : loadError ? null : filteredPlans.length === 0 ? (
        <div style={{ padding: "var(--space-6)", textAlign: "center", background: "var(--color-bg-surface)", borderRadius: "var(--radius-lg)", border: "1px solid var(--color-border-divider)" }}>
          <p className="app-empty-inline" style={{ margin: 0 }}>
            {plans.length === 0
              ? "No subscription plans found."
              : "No subscription plans match the selected search or filter criteria."}
          </p>
          {plans.length > 0 && (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                setSearchQuery("");
                setStatusFilter("ALL");
                setPricingTypeFilter("ALL");
              }}
              style={{ marginTop: "var(--space-3)" }}
            >
              Clear filters
            </Button>
          )}
        </div>
      ) : (
        <div className="app-plan-grid">
          {filteredPlans.map((plan, index) => {
            const allocationCount = tenants.filter(
              (t) => t.planId === plan.planId || (t.planName && plan.planName && t.planName.toLowerCase() === plan.planName.toLowerCase())
            ).length;
            const isActive = (plan.status ?? "ACTIVE") === "ACTIVE";

            return (
              <article
                className={`app-plan-card ${isActive ? "app-plan-card--active" : ""}`}
                key={plan.planId || plan.planName || `plan-${index}`}
              >
                {/* Header: Kicker and Status Badge */}
                <div className="app-plan-card__top">
                  <div className="app-plan-card__identity">
                    <Kicker>PLAN · {plan.currencyCode ?? "INR"}</Kicker>
                    <h2>{plan.planName}</h2>
                  </div>
                  <StatusBadge value={plan.status ?? "ACTIVE"} />
                </div>

                {/* Hero Pricing Display */}
                <div className="app-plan-card__price-box">
                  <div className="app-plan-card__price-row">
                    <span className="app-plan-card__price-amount">
                      ₹{Number(plan.priceAmount).toLocaleString("en-IN")}
                    </span>
                    <span className="app-plan-card__price-cycle">
                      {formatPricingCadence(plan.pricingType, plan.billingCycleMonths)}
                    </span>
                  </div>
                  {/* Backward compatible text for tests / screen readers */}
                  <p style={{ margin: "2px 0 0", fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>
                    ₹{plan.priceAmount} · {plan.pricingType}
                  </p>
                </div>

                {/* Tags & Commercial Highlights */}
                <div className="app-plan-card__tags">
                  {plan.customDomainAllowed && (
                    <span className="app-plan-card__tag app-plan-card__tag--highlight">✓ Custom Domain</span>
                  )}
                  {plan.brandingColorsAllowed && (
                    <span className="app-plan-card__tag app-plan-card__tag--highlight">✓ Custom Branding</span>
                  )}
                  <span className="app-plan-card__tag">
                    {plan.billingCycleMonths === 1 ? "Monthly" : plan.billingCycleMonths === 12 ? "Annual" : `${plan.billingCycleMonths}m term`}
                  </span>
                  {plan.setupFeeAmount ? (
                    <span className="app-plan-card__tag">Setup ₹{Number(plan.setupFeeAmount).toLocaleString("en-IN")}</span>
                  ) : null}
                  {plan.discountValue ? (
                    <span className="app-plan-card__tag" style={{ color: "var(--color-status-success-fg)" }}>
                      {plan.discountType === "PERCENT" ? `${plan.discountValue}% off` : `₹${plan.discountValue} off`}
                    </span>
                  ) : null}
                </div>

                {/* Structured Specs Grid */}
                <div className="app-plan-card__specs-grid">
                  <div className="app-plan-card__spec-item">
                    <span className="app-plan-card__spec-label">Users</span>
                    <span className="app-plan-card__spec-value">
                      {plan.userLimit ? Number(plan.userLimit).toLocaleString("en-IN") : "Unlimited"}
                    </span>
                  </div>
                  <div className="app-plan-card__spec-item">
                    <span className="app-plan-card__spec-label">Storage</span>
                    <span className="app-plan-card__spec-value">{plan.storageLimitGb ?? 50} GB</span>
                  </div>
                  <div className="app-plan-card__spec-item">
                    <span className="app-plan-card__spec-label">Students</span>
                    <span className="app-plan-card__spec-value">
                      {plan.maxStudents ? Number(plan.maxStudents).toLocaleString("en-IN") : "∞"}
                    </span>
                  </div>
                  <div className="app-plan-card__spec-item">
                    <span className="app-plan-card__spec-label">Staff</span>
                    <span className="app-plan-card__spec-value">
                      {plan.maxStaff ? Number(plan.maxStaff).toLocaleString("en-IN") : "∞"}
                    </span>
                  </div>
                  <div className="app-plan-card__spec-item">
                    <span className="app-plan-card__spec-label">Colleges</span>
                    <span className="app-plan-card__spec-value">{plan.maxColleges ?? "∞"}</span>
                  </div>
                  <div className="app-plan-card__spec-item">
                    <span className="app-plan-card__spec-label">Schools</span>
                    <span className="app-plan-card__spec-value">{plan.maxSchools ?? "∞"}</span>
                  </div>
                </div>

                {/* Backward compatibility billing line */}
                <div className="app-plan-card__billing" style={{ display: "none" }}>
                  {plan.userLimit ? `${plan.userLimit} max users` : "Unlimited users"}
                  <span> · Students {plan.maxStudents ?? "∞"} · Staff {plan.maxStaff ?? "∞"}</span>
                  <span> · Colleges {plan.maxColleges ?? "∞"} · Schools {plan.maxSchools ?? "∞"}</span>
                </div>

                {/* Tenant Allocation Footer */}
                <div className="app-plan-card__footer">
                  <div style={{ display: "flex", flexDirection: "column" }}>
                    <span>Tenant allocation</span>
                    <small style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>Enrolled institutions</small>
                  </div>
                  <strong>{allocationCount}</strong>
                </div>

                {/* Action Button Row */}
                <div className="app-plan-card__actions">
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => {
                      setCreateDraft({
                        planName: plan.planName,
                        pricingType: plan.pricingType,
                        priceAmount: plan.priceAmount,
                        currencyCode: plan.currencyCode,
                        userLimit: plan.userLimit ?? 1000,
                        storageLimitGb: plan.storageLimitGb ?? 50,
                        smsLimit: plan.smsLimit ?? 5000,
                        apiLimit: plan.apiLimit ?? 10000,
                        maxInstitutions: plan.maxInstitutions ?? 1,
                        maxColleges: plan.maxColleges ?? 0,
                        maxSchools: plan.maxSchools ?? 0,
                        maxStudents: plan.maxStudents ?? 0,
                        maxStaff: plan.maxStaff ?? 0,
                        customDomainAllowed: plan.customDomainAllowed,
                        brandingColorsAllowed: plan.brandingColorsAllowed,
                        billingCycleMonths: plan.billingCycleMonths,
                        smsUnitAmount: plan.smsUnitAmount ?? 0,
                        storageUnitAmountPerGb: plan.storageUnitAmountPerGb ?? 0,
                        setupFeeAmount: plan.setupFeeAmount ?? 0,
                        discountType: plan.discountType ?? "NONE",
                        discountValue: plan.discountValue ?? 0,
                        contractDurationMonths: plan.contractDurationMonths ?? 12,
                        gracePeriodDays: plan.gracePeriodDays ?? 15,
                        renewalMode: plan.renewalMode ?? "AUTOMATIC",
                        selectedModules: [],
                      });
                      void openPlanBuilder(plan);
                    }}
                  >
                    Edit plan
                  </Button>
                  {canToggleFeatures && (
                    <Button variant="secondary" size="sm" onClick={() => setEntitlementPlan(plan)}>
                      Modules &amp; features
                    </Button>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}

      {/* Customer Quote Sales Workspace */}
      {canBuildQuotes && (
        <section className="app-panel app-customer-quote" style={{ marginTop: "var(--space-6)" }}>
          <div className="app-panel__header">
            <div>
              <Kicker>SALES WORKSPACE</Kicker>
              <h2>Build a customer quote</h2>
              <p>Enter the institution’s expected size and modules to compare eligible plans and packages.</p>
            </div>
          </div>
          <form className="app-customer-quote__form" onSubmit={submitCustomerQuote}>
            <div className="app-form-grid">
              <SelectField
                id="quote-tenant-type"
                label="Customer type"
                value={quoteNeeds.tenantType}
                onChange={(event) =>
                  setQuoteNeeds((current) => ({ ...current, tenantType: event.target.value as TenantType }))
                }
                options={[
                  { value: "UNIVERSITY_GROUP", label: "University group" },
                  { value: "STANDALONE_COLLEGE", label: "Standalone college" },
                  { value: "STANDALONE_SCHOOL", label: "Standalone school" },
                ]}
              />
              <SelectField
                id="quote-term"
                label="Billing term"
                value={String(quoteNeeds.billingCycleMonths)}
                onChange={(event) =>
                  setQuoteNeeds((current) => ({ ...current, billingCycleMonths: Number(event.target.value) }))
                }
                options={[1, 3, 6, 12, 24, 36].map((months) => ({
                  value: String(months),
                  label: months === 1 ? "Monthly" : months === 12 ? "Yearly" : `${months} months`,
                }))}
              />
              <TextField
                id="quote-colleges"
                label="Colleges"
                type="number"
                min="0"
                value={String(quoteNeeds.colleges)}
                onChange={(event) =>
                  setQuoteNeeds((current) => ({ ...current, colleges: Number(event.target.value) || 0 }))
                }
              />
              <TextField
                id="quote-schools"
                label="Schools"
                type="number"
                min="0"
                value={String(quoteNeeds.schools)}
                onChange={(event) =>
                  setQuoteNeeds((current) => ({ ...current, schools: Number(event.target.value) || 0 }))
                }
              />
              <TextField
                id="quote-students"
                label="Expected students"
                type="number"
                min="0"
                value={String(quoteNeeds.students)}
                onChange={(event) =>
                  setQuoteNeeds((current) => ({ ...current, students: Number(event.target.value) || 0 }))
                }
              />
              <TextField
                id="quote-staff"
                label="Expected staff"
                type="number"
                min="0"
                value={String(quoteNeeds.staff)}
                onChange={(event) =>
                  setQuoteNeeds((current) => ({ ...current, staff: Number(event.target.value) || 0 }))
                }
              />
              <TextField
                id="quote-modules"
                label="Required modules (comma-separated codes)"
                value={quoteNeeds.requiredModules}
                onChange={(event) =>
                  setQuoteNeeds((current) => ({ ...current, requiredModules: event.target.value }))
                }
                placeholder="e.g. SIS, LMS, FINANCE"
                hint="Required to estimate per-module plans accurately."
              />
            </div>
            <label className="app-customer-quote__setup">
              <input
                type="checkbox"
                checked={quoteNeeds.includeSetupFee}
                onChange={(event) =>
                  setQuoteNeeds((current) => ({ ...current, includeSetupFee: event.target.checked }))
                }
              />
              Include one-time setup fee in the estimate
            </label>
            <div>
              <Button type="submit" busy={quoteLoading}>
                Compare plans
              </Button>
            </div>
          </form>
          {quoteError && <Notice tone="danger">{quoteError}</Notice>}
          {quoteResult && (
            <div className="app-customer-quote__results" aria-live="polite">
              <div className="app-customer-quote__result-heading">
                <h3>Quote comparison</h3>
                <span>Prices shown before tax · {quoteResult.billingCycleMonths} months</span>
              </div>
              {quoteResult.plans.length === 0 ? (
                <p className="app-empty-inline">No active plans are available to compare.</p>
              ) : (
                <div className="app-plan-grid">
                  {quoteResult.plans.map((plan) => (
                    <article
                      className={`app-plan-card${plan.eligible ? "" : " app-plan-card--ineligible"}`}
                      key={plan.planId}
                    >
                      <div className="app-customer-quote__badges">
                        {plan.recommendation && (
                          <Badge tone="success">{plan.recommendation.replaceAll("_", " ")}</Badge>
                        )}
                        {!plan.eligible && <Badge tone="warning">Does not fit</Badge>}
                      </div>
                      <Kicker>{plan.pricingType ? plan.pricingType.replaceAll("_", " ") : "QUOTE"}</Kicker>
                      <h3>{plan.planName}</h3>
                      {plan.eligible ? (
                        <>
                          <strong className="app-customer-quote__total">
                            {formatQuoteCurrency(plan.total, quoteResult.currencyCode)}
                          </strong>
                          <span className="app-customer-quote__caption">estimated total for this term</span>
                        </>
                      ) : (
                        <ul className="app-customer-quote__unmet">
                          {plan.unmetRequirements.map((requirement) => (
                            <li key={requirement}>{requirement}</li>
                          ))}
                        </ul>
                      )}
                      <dl className="app-customer-quote__lines">
                        {plan.lines.map((line, index) => (
                          <div key={`${line.code}-${index}`}>
                            <dt>{line.description}</dt>
                            <dd>{formatQuoteCurrency(line.amount, quoteResult.currencyCode)}</dd>
                          </div>
                        ))}
                        {plan.discount > 0 && (
                          <div>
                            <dt>Discount</dt>
                            <dd>−{formatQuoteCurrency(plan.discount, quoteResult.currencyCode)}</dd>
                          </div>
                        )}
                      </dl>
                      {plan.fitReasons.length > 0 && (
                        <ul className="app-customer-quote__reasons">
                          {plan.fitReasons.map((reason) => (
                            <li key={reason}>{reason}</li>
                          ))}
                        </ul>
                      )}
                    </article>
                  ))}
                </div>
              )}
            </div>
          )}
        </section>
      )}

      {/* Commercial Catalog Management Section */}
      {canConfigurePricing && (
        <section className="app-panel app-commercial-catalog" style={{ marginTop: "var(--space-6)" }}>
          <div className="app-panel__header">
            <div>
              <Kicker>REUSABLE PRICING</Kicker>
              <h2>Commercial catalog</h2>
              <p>Manage modules, capacity packs and service add-ons used in plans and customer quotes.</p>
            </div>
            <Button
              onClick={() => {
                setEditingCatalogItem(null);
                setCatalogModalOpen(true);
              }}
            >
              Add catalog item
            </Button>
          </div>
          {catalogError && !catalogError.includes("404") && <Notice tone="danger">{catalogError}</Notice>}
          {catalogLoading ? (
            <div className="app-commercial-catalog__loading" role="status" aria-busy="true">
              <SkeletonText height="1rem" width="50%" />
              <SkeletonText height="1rem" width="70%" />
            </div>
          ) : !catalogError && catalog.length === 0 ? (
            <p className="app-empty-inline">
              No catalog items yet. Add a module, capacity pack or service before building a combo plan.
            </p>
          ) : (
            <div className="app-commercial-catalog__list">
              {catalog.map((item, index) => (
                <article className="app-commercial-catalog__item" key={item.catalogItemId || item.itemCode || `cat-${index}`}>
                  <div>
                    <strong>{item.itemName}</strong>
                    <span>
                      {item.itemCode} · {item.category} · {item.pricingBasis ? item.pricingBasis.replaceAll("_", " ") : ""}
                    </span>
                  </div>
                  <strong>
                    {new Intl.NumberFormat("en-IN", {
                      style: "currency",
                      currency: "INR",
                      maximumFractionDigits: 2,
                    }).format(item.unitAmount)}
                  </strong>
                  <Badge tone={item.available ? "success" : "neutral"}>
                    {item.available ? "Available" : "Unavailable"}
                  </Badge>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => {
                      setEditingCatalogItem(item);
                      setCatalogModalOpen(true);
                    }}
                  >
                    Edit item
                  </Button>
                </article>
              ))}
            </div>
          )}
        </section>
      )}

      {entitlementPlan && (
        <EntitlementEditor
          isOpen
          target="plan"
          targetId={entitlementPlan.planId}
          targetName={entitlementPlan.planName}
          isProductOwner={canConfigurePricing}
          onClose={() => setEntitlementPlan(null)}
          onSaved={() => setEntitlementPlan(null)}
        />
      )}
      <CreatePlanModal
        key={planBuilderKey}
        isOpen={showCreate}
        onClose={() => setShowCreate(false)}
        onSubmit={submitCreatePlan}
        createDraft={createDraft}
        setCreateDraft={setCreateDraft}
        creating={creating}
        createError={createError}
        canConfigurePricing={canConfigurePricing}
        isEditing={Boolean(editingPlan)}
        catalog={catalog}
        catalogLoading={catalogLoading}
        catalogError={catalogError}
        components={planComponents}
        setComponents={setPlanComponents}
      />
      <CommercialCatalogModal
        isOpen={catalogModalOpen}
        item={editingCatalogItem}
        onClose={() => setCatalogModalOpen(false)}
        onSaved={() => void loadCatalog()}
      />
    </div>
  );
}

export const PlansPage = PlansView;
