import type { FormEvent } from "react";
import { Button, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";
import type { PlanItem, TenantType, IsolationMode } from "../../api/platformTenantService";

export interface CreateTenantDraft {
  tenantName: string;
  tenantType: TenantType;
  planId: string;
  subdomain: string;
  isolationMode: IsolationMode;
  firstAdminName: string;
  firstAdminEmail: string;
  firstAdminMobile: string;
  firstAdminNationalIdLast4: string;
  retentionMonths: number | "";
  backupRetentionDays: number | "";
}

export interface CreateTenantModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
  draft: CreateTenantDraft;
  setDraft: React.Dispatch<React.SetStateAction<CreateTenantDraft>>;
  plans: PlanItem[];
  creating: boolean;
  createError: string;
  onNameChange: (name: string) => void;
}

export function CreateTenantModal({
  isOpen,
  onClose,
  onSubmit,
  draft,
  setDraft,
  plans,
  creating,
  createError,
  onNameChange,
}: CreateTenantModalProps) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="NEW TENANT"
      title="Create trial tenant"
      titleId="tenant-create-title"
      description="New tenants begin in Trial state with a 30-day evaluation period. Activation occurs upon plan payment."
      size="lg"
    >
      <form onSubmit={onSubmit}>
        <TextField
          id="tenant-name"
          label="Institution name"
          required
          value={draft.tenantName}
          onChange={(e) => onNameChange(e.target.value)}
          placeholder="e.g. Lakeside World School"
        />
        <TextField
          id="tenant-subdomain"
          label="Assigned subdomain"
          required
          value={draft.subdomain}
          onChange={(e) => setDraft({ ...draft, subdomain: e.target.value.toLowerCase().trim() })}
          placeholder="e.g. lakeside"
        />
        <div className="app-form-grid">
          <SelectField
            id="tenant-type"
            label="Institution type"
            value={draft.tenantType}
            onChange={(e) => setDraft({ ...draft, tenantType: e.target.value as TenantType })}
            options={[
              { value: "STANDALONE_SCHOOL", label: "School" },
              { value: "STANDALONE_COLLEGE", label: "College" },
              { value: "UNIVERSITY_GROUP", label: "University Group" },
            ]}
          />
          {/* Raw <select> kept because plan options include dynamic price values and optgroup-style would need SelectField extension */}
          <label className="app-form-field">
            Subscription plan
            <select value={draft.planId} onChange={(e) => setDraft({ ...draft, planId: e.target.value })}>
              {plans.length > 0 ? (
                plans.map((p) => (
                  <option key={p.planId} value={p.planId}>
                    {p.planName} (₹{p.priceAmount})
                  </option>
                ))
              ) : (
                <option value="">No subscription plans available</option>
              )}
            </select>
          </label>
        </div>
        {plans.length === 0 && (
          <Notice tone="warning">
            A subscription plan must exist before a tenant can be created. Configure a plan first, then retry.
          </Notice>
        )}
        <div className="app-form-grid">
          <TextField
            id="tenant-retention-months"
            label="Data retention (months)"
            type="number"
            required
            value={String(draft.retentionMonths)}
            onChange={(e) => setDraft({ ...draft, retentionMonths: e.target.value === "" ? "" : Number(e.target.value) })}
          />
          <TextField
            id="tenant-backup-retention-days"
            label="Backup retention (days)"
            type="number"
            required
            value={String(draft.backupRetentionDays)}
            onChange={(e) => setDraft({ ...draft, backupRetentionDays: e.target.value === "" ? "" : Number(e.target.value) })}
          />
        </div>
        <small className="app-field-hint">
          Set at creation: data retention 1–240 months; backup retention 7–3650 days. Later changes require Data Protection Officer approval.
        </small>
        <div className="app-form-grid">
          <label className="app-form-field">
            Database isolation
            <select
              value={draft.isolationMode}
              onChange={(e) => setDraft({ ...draft, isolationMode: e.target.value as IsolationMode })}
            >
              <option value="SHARED">Shared Database with RLS (SHARED)</option>
              <option value="ISOLATED" disabled>
                Dedicated Database (ISOLATED - not yet available)
              </option>
            </select>
            <small className="app-field-hint">
              Dedicated-database tenants aren&rsquo;t provisionable yet - every tenant is created on the shared,
              RLS-isolated database for now.
            </small>
          </label>
          <TextField
            id="tenant-admin-name"
            label="Primary administrator full name"
            required
            value={draft.firstAdminName}
            onChange={(e) => setDraft({ ...draft, firstAdminName: e.target.value })}
            placeholder="e.g. Dr. Ramesh Kumar"
          />
        </div>
        <TextField
          id="tenant-admin-email"
          label="Primary administrator email"
          type="email"
          required
          value={draft.firstAdminEmail}
          onChange={(e) => setDraft({ ...draft, firstAdminEmail: e.target.value })}
          placeholder="principal@lakeside.edu"
        />
        <div className="app-form-grid">
          <TextField
            id="tenant-admin-mobile"
            label="Primary contact mobile (+91 format)"
            value={draft.firstAdminMobile}
            onChange={(e) => setDraft({ ...draft, firstAdminMobile: e.target.value })}
            placeholder="+919876543210"
          />
          <TextField
            id="tenant-admin-national-id"
            label="Aadhaar / National ID (Last 4 digits)"
            value={draft.firstAdminNationalIdLast4}
            onChange={(e) => setDraft({ ...draft, firstAdminNationalIdLast4: e.target.value })}
            placeholder="e.g. 4582"
          />
        </div>
        <div className="app-note">
          <strong>Statutory Compliance Note:</strong> Per UIDAI guidelines and DPDP Act 2023 [R2-34], full 12-digit
          Aadhaar numbers are never stored; only the last 4 digits are recorded for statutory identity verification.
        </div>
        {createError && <Notice tone="danger">{createError}</Notice>}
        <footer>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" busy={creating} disabled={creating || plans.length === 0}>
            Submit & create tenant
          </Button>
        </footer>
      </form>
    </Modal>
  );
}
