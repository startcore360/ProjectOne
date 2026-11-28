import type { FormEvent } from "react";
import { Button, Modal, Notice, PhoneField, SelectField, TextField, validateNationalIdLast4 } from "../../../../design-system/components";
import { useFormErrors } from "../../../../hooks";
import type { PlanItem, TenantType, IsolationMode, FirstUserRole } from "../../api/platformTenantService";

export interface CreateTenantDraft {
  tenantName: string;
  tenantType: TenantType;
  planId: string;
  subdomain: string;
  isolationMode: IsolationMode;
  firstUserRole: FirstUserRole;
  firstAdminName: string;
  firstAdminEmail: string;
  firstAdminMobile: string;
  firstAdminNationalIdLast4: string;
  retentionMonths: number | "";
  backupRetentionDays: number | "";
}

// The first user is the owner of the new tenant. Only the Admin owner can be created today; the combined
// Principal + Admin owner is shown so the choice is visible, but cannot be picked yet (the server refuses it).
function firstUserRoleOptions(tenantType: TenantType) {
  if (tenantType === "UNIVERSITY_GROUP") {
    return [{ value: "ADMIN", label: "University Admin" }];
  }
  const admin = tenantType === "STANDALONE_COLLEGE" ? "College Admin" : "School Admin";
  return [
    { value: "ADMIN", label: `${admin}: owner who runs it and hires a Principal later` },
    { value: "PRINCIPAL_ADMIN", label: "Principal + Admin: owner and Principal in one account (coming soon)", disabled: true },
  ];
}

function firstUserRoleHint(tenantType: TenantType) {
  if (tenantType === "UNIVERSITY_GROUP") {
    return "The University Admin runs the whole university and creates the Vice Chancellor, Registrar and other top users.";
  }
  const place = tenantType === "STANDALONE_COLLEGE" ? "college" : "school";
  return `This person is the owner. They run the ${place}, create staff and hire the Principal.`;
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
  const { fieldErrors, setFieldError, clearFieldError } = useFormErrors();
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
          fieldName="tenantName"
          error={fieldErrors.tenantName}
          setFieldError={setFieldError}
          clearFieldError={clearFieldError}
        />
        <TextField
          id="tenant-subdomain"
          label="Assigned subdomain"
          required
          value={draft.subdomain}
          onChange={(e) => setDraft({ ...draft, subdomain: e.target.value.toLowerCase().trim() })}
          placeholder="e.g. lakeside"
          fieldName="subdomain"
          error={fieldErrors.subdomain}
          setFieldError={setFieldError}
          clearFieldError={clearFieldError}
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
          <SelectField
            id="tenant-plan-id"
            label="Subscription plan"
            value={draft.planId}
            onChange={(e) => setDraft({ ...draft, planId: e.target.value })}
            options={
              plans.length > 0
                ? plans.map((p) => ({ value: p.planId, label: `${p.planName} (₹${p.priceAmount})` }))
                : [{ value: "", label: "No subscription plans available" }]
            }
          />
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
          <SelectField
            id="tenant-isolation-mode"
            label="Database isolation"
            value={draft.isolationMode}
            onChange={(e) => setDraft({ ...draft, isolationMode: e.target.value as IsolationMode })}
            options={[{ value: "SHARED", label: "Shared Database with RLS (SHARED)" }]}
            hint="Dedicated-database tenants aren't provisionable yet - every tenant is created on the shared, RLS-isolated database for now."
          />
          <TextField
            id="tenant-admin-name"
            label="Primary administrator full name"
            required
            value={draft.firstAdminName}
            onChange={(e) => setDraft({ ...draft, firstAdminName: e.target.value })}
            placeholder="e.g. Dr. Ramesh Kumar"
            fieldName="firstAdminName"
            error={fieldErrors.firstAdminName}
            setFieldError={setFieldError}
            clearFieldError={clearFieldError}
          />
        </div>
        <SelectField
          id="tenant-first-user-role"
          label="First user's role"
          value={draft.firstUserRole}
          onChange={(e) => setDraft({ ...draft, firstUserRole: e.target.value as FirstUserRole })}
          options={firstUserRoleOptions(draft.tenantType)}
          hint={firstUserRoleHint(draft.tenantType)}
        />
        <TextField
          id="tenant-admin-email"
          label="Primary administrator email"
          type="email"
          required
          value={draft.firstAdminEmail}
          onChange={(e) => setDraft({ ...draft, firstAdminEmail: e.target.value })}
          placeholder="principal@lakeside.edu"
          fieldName="firstAdminEmail"
          error={fieldErrors.firstAdminEmail}
          setFieldError={setFieldError}
          clearFieldError={clearFieldError}
        />
        <div className="app-form-grid">
          <PhoneField
            id="tenant-admin-mobile"
            label="Primary contact mobile (+91 format)"
            value={draft.firstAdminMobile}
            onChange={(e) => setDraft({ ...draft, firstAdminMobile: e.target.value })}
            fieldName="firstAdminMobile"
            error={fieldErrors.firstAdminMobile}
            setFieldError={setFieldError}
            clearFieldError={clearFieldError}
          />
          <TextField
            id="tenant-admin-national-id"
            label="Aadhaar / National ID (Last 4 digits)"
            value={draft.firstAdminNationalIdLast4}
            onChange={(e) => {
              const sanitized = e.target.value.replace(/\D/g, "").slice(0, 4);
              setDraft({ ...draft, firstAdminNationalIdLast4: sanitized });
            }}
            placeholder="e.g. 4582"
            maxLength={4}
            inputMode="numeric"
            pattern="\d{4}"
            hint="Enter exactly 4 numeric digits."
            fieldName="firstAdminNationalIdLast4"
            error={fieldErrors.firstAdminNationalIdLast4}
            validator={validateNationalIdLast4}
            setFieldError={setFieldError}
            clearFieldError={clearFieldError}
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
