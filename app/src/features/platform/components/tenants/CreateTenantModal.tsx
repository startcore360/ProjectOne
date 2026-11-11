import type { FormEvent } from "react";
import { Button, Kicker, TextField, Notice } from "../../../../design-system/components";
import { Icon } from "../../../../components/ui/Icon";
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
  if (!isOpen) return null;

  return (
    <div
      className="platform-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="tenant-create-title">
        <header>
          <div>
            <Kicker>NEW TENANT</Kicker>
            <h2 id="tenant-create-title">Create trial tenant</h2>
            <p>New tenants begin in Trial state with a 30-day evaluation period. Activation occurs upon plan payment.</p>
          </div>
          <button className="platform-icon-button" onClick={onClose} aria-label="Close">
            <Icon name="x" size={16} />
          </button>
        </header>
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
          <div className="platform-form-grid">
            <label className="platform-form-field">
              Institution type
              <select
                value={draft.tenantType}
                onChange={(e) => setDraft({ ...draft, tenantType: e.target.value as TenantType })}
              >
                <option value="STANDALONE_SCHOOL">School</option>
                <option value="STANDALONE_COLLEGE">College</option>
                <option value="UNIVERSITY_GROUP">University Group</option>
              </select>
            </label>
            <label className="platform-form-field">
              Subscription plan
              <select value={draft.planId} onChange={(e) => setDraft({ ...draft, planId: e.target.value })}>
                {plans.length > 0 ? (
                  plans.map((p) => (
                    <option key={p.planId} value={p.planId}>
                      {p.planName} (₹{p.priceAmount})
                    </option>
                  ))
                ) : (
                  <>
                    <option value="11111111-1111-1111-1111-111111111101">Standard School Plan (₹499)</option>
                    <option value="11111111-1111-1111-1111-111111111102">Premium College Plan (₹999)</option>
                    <option value="11111111-1111-1111-1111-111111111103">Enterprise University Plan (₹1999)</option>
                  </>
                )}
              </select>
            </label>
          </div>
          <div className="platform-form-grid">
            <label className="platform-form-field">
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
              <small className="platform-field-hint">
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
          <div className="platform-form-grid">
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
          <div className="platform-modal-note">
            <strong>Statutory Compliance Note:</strong> Per UIDAI guidelines and DPDP Act 2023 [R2-34], full 12-digit
            Aadhaar numbers are never stored; only the last 4 digits are recorded for statutory identity verification.
          </div>
          {createError && <Notice tone="danger">{createError}</Notice>}
          <footer>
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" busy={creating}>
              Submit & create tenant
            </Button>
          </footer>
        </form>
      </section>
    </div>
  );
}
