import { useEffect, useState, type FormEvent } from "react";
import { Button, Kicker, Notice } from "../../../../design-system/components";
import { Badge, Spinner } from "../../../../components/ui";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { platformTenantService, type PlanItem, type TenantItem } from "../../api/platformTenantService";
import { CreatePlanModal, type CreatePlanDraft } from "./CreatePlanModal";

export function PlansView() {
  const [plans, setPlans] = useState<PlanItem[]>([]);
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [showCreate, setShowCreate] = useState(false);
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
      await platformTenantService.createPlan({
        planName: createDraft.planName.trim(),
        pricingType: createDraft.pricingType,
        priceAmount: Number(createDraft.priceAmount),
        currencyCode: createDraft.currencyCode,
        userLimit: Number(createDraft.userLimit),
        storageLimitGb: Number(createDraft.storageLimitGb),
        smsLimit: Number(createDraft.smsLimit),
        apiLimit: Number(createDraft.apiLimit),
        maxInstitutions: Number(createDraft.maxInstitutions),
      });
      setMessage(`Plan "${createDraft.planName}" created successfully.`);
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
      });
      loadPlans();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to create subscription plan.";
      setCreateError(msg);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="Plans & billing"
        description="Review the platform plan catalogue, pricing types, and subscription limits [PLAN_MANAGE, Action #202]."
        actions={
          <Button
            onClick={() => {
              setMessage("");
              setCreateError("");
              setShowCreate(true);
            }}
          >
            Add subscription plan
          </Button>
        }
      />
      {message && <Notice tone="success">{message}</Notice>}
      {loadError && !loading && (
        <Notice tone="danger">
          <div className="platform-error-state" role="alert">
            <p>{loadError}</p>
            <Button variant="secondary" onClick={() => void loadPlans()}>Retry</Button>
          </div>
        </Notice>
      )}

      {loading ? (
        <div className="platform-empty-inline">
          <Spinner label="Loading plans..." />
        </div>
      ) : loadError ? null : plans.length === 0 ? (
        <p className="platform-empty-inline">No subscription plans found.</p>
      ) : (
        <div className="platform-plan-grid">
          {plans.map((plan) => (
            <article className="platform-plan-card" key={plan.planId || plan.planName}>
              <Kicker>PLAN · {plan.currencyCode ?? "INR"}</Kicker>
              <h2>{plan.planName}</h2>
              <p>
                ₹{plan.priceAmount} · {plan.pricingType}
              </p>
              <div className="platform-plan-card__billing">
                {plan.userLimit ? `${plan.userLimit} max users` : "Unlimited users"}
              </div>
              <div className="platform-plan-card__footer">
                <span>Tenant allocation</span>
                <strong>
                  {
                    tenants.filter((t) => t.planId === plan.planId || t.planName.toLowerCase() === plan.planName.toLowerCase()).length
                  }
                </strong>
              </div>
              <Badge tone="neutral">{plan.status ?? "ACTIVE"}</Badge>
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
      />
    </div>
  );
}

export const PlansPage = PlansView;
