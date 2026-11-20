import { useEffect, useState, type FormEvent } from "react";
import { Button, Kicker, Notice, SkeletonCard, SkeletonText } from "../../../../design-system/components";
import { Badge } from "../../../../components/ui";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { platformTenantService, type PlanItem, type TenantItem } from "../../api/platformTenantService";
import { CreatePlanModal, type CreatePlanDraft } from "./CreatePlanModal";
import { usePlatformAccess } from "../../platformAccess";
import { EntitlementEditor } from "../entitlements/EntitlementEditor";

export function PlansView() {
  const { role } = usePlatformAccess();
  const canConfigurePricing = role === "PRODUCT_OWNER";
  // E2-S2: module and feature switches per plan (FEATURE_TOGGLE).
  const { can: canDo } = usePlatformAccess();
  const canToggleFeatures = canDo("FEATURE_TOGGLE");
  const [entitlementPlan, setEntitlementPlan] = useState<PlanItem | null>(null);
  const [plans, setPlans] = useState<PlanItem[]>([]);
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [editingPlan, setEditingPlan] = useState<PlanItem | null>(null);
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
    customDomainAllowed: false,
    brandingColorsAllowed: false,
    billingCycleMonths: 12,
    smsUnitAmount: 0,
    storageUnitAmountPerGb: 0,
  });
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [message, setMessage] = useState("");

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

  useEffect(() => {
    loadPlans();
  }, []);

  const submitCreatePlan = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setCreating(true);
    setCreateError("");
    try {
      const payload = {
        planName: createDraft.planName.trim(),
        userLimit: Number(createDraft.userLimit),
        storageLimitGb: Number(createDraft.storageLimitGb),
        smsLimit: Number(createDraft.smsLimit),
        apiLimit: Number(createDraft.apiLimit),
        maxInstitutions: Number(createDraft.maxInstitutions),
        customDomainAllowed: createDraft.customDomainAllowed,
        brandingColorsAllowed: createDraft.brandingColorsAllowed,
        billingCycleMonths: createDraft.billingCycleMonths,
        ...(canConfigurePricing ? {
          pricingType: createDraft.pricingType,
          priceAmount: Number(createDraft.priceAmount),
          currencyCode: createDraft.currencyCode,
          smsUnitAmount: Number(createDraft.smsUnitAmount),
          storageUnitAmountPerGb: Number(createDraft.storageUnitAmountPerGb),
        } : {}),
      };
      if (editingPlan) {
        await platformTenantService.updatePlan(editingPlan.planId, payload, editingPlan.rowVersion);
      } else {
        await platformTenantService.createPlan(payload);
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
        customDomainAllowed: false,
        brandingColorsAllowed: false,
        billingCycleMonths: 12,
        smsUnitAmount: 0,
        storageUnitAmountPerGb: 0,
      });
      setEditingPlan(null);
      loadPlans();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to create subscription plan.";
      setCreateError(msg);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="app-page">
      <PageHeader
        title="Plans & billing"
        description="Review subscription plans, pricing types and platform limits."
        actions={
          <Button
            onClick={() => {
              setMessage("");
              setCreateError("");
              setEditingPlan(null);
              setCreateDraft({
                planName: "", pricingType: "PER_STUDENT_YEAR", priceAmount: 499, currencyCode: "INR",
                userLimit: 1000, storageLimitGb: 50, smsLimit: 5000, apiLimit: 10000, maxInstitutions: 1,
                customDomainAllowed: false, brandingColorsAllowed: false, billingCycleMonths: 12,
                smsUnitAmount: 0, storageUnitAmountPerGb: 0,
              });
              setShowCreate(true);
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
          action={<Button variant="secondary" onClick={() => void loadPlans()}>Retry</Button>}
        />
      )}

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
      ) : loadError ? null : plans.length === 0 ? (
        <p className="app-empty-inline">No subscription plans found.</p>
      ) : (
        <div className="app-plan-grid">
          {plans.map((plan) => (
            <article className="app-plan-card" key={plan.planId || plan.planName}>
              <Kicker>PLAN · {plan.currencyCode ?? "INR"}</Kicker>
              <h2>{plan.planName}</h2>
              <p>
                ₹{plan.priceAmount} · {plan.pricingType}
              </p>
              <div className="app-plan-card__billing">
                {plan.userLimit ? `${plan.userLimit} max users` : "Unlimited users"}
              </div>
              <div className="app-plan-card__footer">
                <span>Tenant allocation</span>
                <strong>
                  {
                    tenants.filter((t) => t.planId === plan.planId || t.planName.toLowerCase() === plan.planName.toLowerCase()).length
                  }
                </strong>
              </div>
              <Badge tone="neutral">{plan.status ?? "ACTIVE"}</Badge>
              <Button variant="secondary" size="sm" onClick={() => {
                setEditingPlan(plan);
                setCreateError("");
                setCreateDraft({
                  planName: plan.planName, pricingType: plan.pricingType, priceAmount: plan.priceAmount,
                  currencyCode: plan.currencyCode, userLimit: plan.userLimit ?? 1000,
                  storageLimitGb: plan.storageLimitGb ?? 50, smsLimit: plan.smsLimit ?? 5000,
                  apiLimit: plan.apiLimit ?? 10000, maxInstitutions: plan.maxInstitutions ?? 1,
                  customDomainAllowed: plan.customDomainAllowed, brandingColorsAllowed: plan.brandingColorsAllowed,
                  billingCycleMonths: plan.billingCycleMonths, smsUnitAmount: plan.smsUnitAmount ?? 0,
                  storageUnitAmountPerGb: plan.storageUnitAmountPerGb ?? 0,
                });
                setShowCreate(true);
              }}>Edit plan</Button>
              {canToggleFeatures && (
                <Button variant="secondary" size="sm" onClick={() => setEntitlementPlan(plan)}>Modules &amp; features</Button>
              )}
            </article>
          ))}
        </div>
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
        isOpen={showCreate}
        onClose={() => setShowCreate(false)}
        onSubmit={submitCreatePlan}
        createDraft={createDraft}
        setCreateDraft={setCreateDraft}
        creating={creating}
        createError={createError}
        canConfigurePricing={canConfigurePricing}
        isEditing={Boolean(editingPlan)}
      />
    </div>
  );
}

export const PlansPage = PlansView;
