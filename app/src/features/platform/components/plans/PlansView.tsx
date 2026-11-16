import { useEffect, useState, type FormEvent } from "react";
import { Button, Kicker, Notice } from "../../../../design-system/components";
import { Badge, Spinner } from "../../../../components/ui";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { platformTenantService, type PlanItem, type TenantItem } from "../../api/platformTenantService";
import { CreatePlanModal, type CreatePlanDraft } from "./CreatePlanModal";
import { usePlatformAccess } from "../../platformAccess";

export function PlansView() {
  const { role } = usePlatformAccess();
  const canConfigurePricing = role === "PRODUCT_OWNER";
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
        smsUnitAmount: Number(createDraft.smsUnitAmount),
        storageUnitAmountPerGb: Number(createDraft.storageUnitAmountPerGb),
        ...(canConfigurePricing ? {
          pricingType: createDraft.pricingType,
          priceAmount: Number(createDraft.priceAmount),
          currencyCode: createDraft.currencyCode,
        } : {}),
      };
      if (editingPlan) {
        await platformTenantService.updatePlan(editingPlan.planId, payload);
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

      {loading ? (
        <div className="app-empty-inline">
          <Spinner label="Loading plans..." />
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
            </article>
          ))}
        </div>
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
