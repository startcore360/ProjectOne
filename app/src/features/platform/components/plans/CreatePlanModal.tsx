import type { FormEvent } from "react";
import { Button, Kicker, TextField, Notice } from "../../../../design-system/components";
import { Icon } from "../../../../components/ui/Icon";

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
  if (!isOpen) return null;

  return (
    <div
      className="platform-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="plan-create-title">
        <header>
          <div>
            <Kicker>NEW SUBSCRIPTION PLAN · Rule F-4</Kicker>
            <h2 id="plan-create-title">Add subscription plan</h2>
            <p>Platform Admins can configure limits and modules; Product Owner configures pricing [Rule F-4].</p>
          </div>
          <button className="platform-icon-button" onClick={onClose} aria-label="Close">
            <Icon name="x" size={16} />
          </button>
        </header>
        <form onSubmit={onSubmit}>
          <TextField
            id="plan-name"
            label="Plan Name"
            required
            value={createDraft.planName}
            onChange={(e) => setCreateDraft({ ...createDraft, planName: e.target.value })}
            placeholder="e.g. Standard School Plan"
          />
          <div className="platform-form-grid">
            <label className="platform-form-field">
              Pricing Type
              <select
                value={createDraft.pricingType}
                onChange={(e) => setCreateDraft({ ...createDraft, pricingType: e.target.value })}
              >
                <option value="PER_STUDENT_YEAR">Per Student Per Year</option>
                <option value="FLAT">Flat Fee</option>
                <option value="PER_MODULE">Per Module</option>
              </select>
            </label>
            <TextField
              id="plan-price"
              label="Price Amount (₹)"
              type="number"
              value={String(createDraft.priceAmount)}
              onChange={(e) => setCreateDraft({ ...createDraft, priceAmount: Number(e.target.value) })}
            />
          </div>
          <div className="platform-form-grid">
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
      </section>
    </div>
  );
}
