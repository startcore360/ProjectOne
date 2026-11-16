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
}

export interface CreatePlanModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
  createDraft: CreatePlanDraft;
  setCreateDraft: React.Dispatch<React.SetStateAction<CreatePlanDraft>>;
  creating: boolean;
  createError: string;
}

export function CreatePlanModal({
  isOpen,
  onClose,
  onSubmit,
  createDraft,
  setCreateDraft,
  creating,
  createError,
}: CreatePlanModalProps) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="NEW SUBSCRIPTION PLAN"
      title="Add subscription plan"
      titleId="plan-create-title"
      description="Platform administrators configure limits and modules; the Product Owner configures pricing."
      size="sm"
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
        {createError && <Notice tone="danger">{createError}</Notice>}
        <footer>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" busy={creating}>
            Create plan
          </Button>
        </footer>
      </form>
    </Modal>
  );
}
