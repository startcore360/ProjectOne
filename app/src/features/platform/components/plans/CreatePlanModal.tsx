import type { FormEvent } from "react";
import { Button, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";

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
  customDomainAllowed: boolean;
  brandingColorsAllowed: boolean;
  billingCycleMonths: number;
  smsUnitAmount: number;
  storageUnitAmountPerGb: number;
}

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
}: CreatePlanModalProps) {
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
      <form onSubmit={onSubmit}>
        <TextField
          id="plan-name"
          label="Plan Name"
          required
          value={createDraft.planName}
          onChange={(e) => setCreateDraft({ ...createDraft, planName: e.target.value })}
          placeholder="e.g. Standard School Plan"
        />
        {canConfigurePricing && (
          <fieldset style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
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
            <div className="app-form-grid">
              <TextField
                id="plan-sms-unit-amount"
                label="SMS unit amount (₹)"
                type="number"
                value={String(createDraft.smsUnitAmount)}
                onChange={(e) => setCreateDraft({ ...createDraft, smsUnitAmount: Number(e.target.value) })}
              />
              <TextField
                id="plan-storage-unit-amount"
                label="Storage unit amount per GB (₹)"
                type="number"
                value={String(createDraft.storageUnitAmountPerGb)}
                onChange={(e) => setCreateDraft({ ...createDraft, storageUnitAmountPerGb: Number(e.target.value) })}
              />
            </div>
          </fieldset>
        )}
        {!canConfigurePricing && <Notice tone="info">Pricing, currency and pricing type can only be changed by the Product Owner.</Notice>}
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
          <TextField
            id="plan-institution-limit"
            label="Maximum institutions"
            type="number"
            value={String(createDraft.maxInstitutions)}
            onChange={(e) => setCreateDraft({ ...createDraft, maxInstitutions: Number(e.target.value) })}
          />
        </div>
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
        <div className="app-form-grid">
          <label className="app-form-field">
            <input type="checkbox" checked={createDraft.customDomainAllowed}
              onChange={(e) => setCreateDraft({ ...createDraft, customDomainAllowed: e.target.checked })} />
            Allow custom domain
          </label>
          <label className="app-form-field">
            <input type="checkbox" checked={createDraft.brandingColorsAllowed}
              onChange={(e) => setCreateDraft({ ...createDraft, brandingColorsAllowed: e.target.checked })} />
            Allow branding colour overrides
          </label>
        </div>
        {createError && <Notice tone="danger">{createError}</Notice>}
        <footer>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" busy={creating}>
            {isEditing ? "Save plan" : "Create plan"}
          </Button>
        </footer>
      </form>
    </Modal>
  );
}
