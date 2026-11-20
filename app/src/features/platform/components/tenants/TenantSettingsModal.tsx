import type { FormEvent } from "react";
import {
  Button,
  CheckboxField,
  Modal,
  Notice,
  SkeletonCard,
  SkeletonText,
  TextField,
} from "../../../../design-system/components";
import type { Tenant } from "../common/types";

export interface TenantSettingsDraft {
  timeZone: string;
  dataRegion: string;
  analyticsOptOut: boolean;
  customDomain: string;
  userLimit: number;
  storageLimitGb: number;
}

// E1-S3: the plan's limits and what is already saved, so the window can explain the plan rule.
export interface TenantPlanLimits {
  userLimit: number | null;
  storageLimitGb: number | null;
  customDomainAllowed: boolean;
  currentUserLimit: number | null;
  currentStorageLimitGb: number | null;
  currentCustomDomain: string;
}

export interface TenantSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
  tenant: Tenant | null;
  settingsDraft: TenantSettingsDraft;
  setSettingsDraft: React.Dispatch<React.SetStateAction<TenantSettingsDraft>>;
  loadingSettings: boolean;
  savingSettings: boolean;
  settingsError: string;
  plan?: TenantPlanLimits | null;
  isProductOwner?: boolean;
}

const BEYOND_PLAN = "Beyond the plan - Product Owner only.";

/** A new raise above the plan (the same rule the server applies). */
function beyondPlan(value: number, planLimit: number | null, current: number | null): boolean {
  return planLimit != null && value > planLimit && (current == null || value > current || current <= planLimit);
}

export function TenantSettingsModal({
  isOpen,
  onClose,
  onSubmit,
  tenant,
  settingsDraft,
  setSettingsDraft,
  loadingSettings,
  savingSettings,
  settingsError,
  plan = null,
  isProductOwner = false,
}: TenantSettingsModalProps) {
  if (!isOpen || !tenant) return null;

  const usersBeyond = !!plan && beyondPlan(settingsDraft.userLimit, plan.userLimit, plan.currentUserLimit);
  const storageBeyond = !!plan && beyondPlan(settingsDraft.storageLimitGb, plan.storageLimitGb, plan.currentStorageLimitGb);
  const domainChanged = !!plan && settingsDraft.customDomain.trim().toLowerCase() !== plan.currentCustomDomain;
  const domainBlocked = !!plan && !plan.customDomainAllowed && domainChanged && settingsDraft.customDomain.trim() !== "";
  const blocked = (!isProductOwner && (usersBeyond || storageBeyond)) || domainBlocked;
  const limitHint = (limit: number | null | undefined) => (limit == null ? undefined : `Plan limit: ${limit}`);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="TENANT SETTINGS"
      title={`Tenant Settings: ${tenant.name}`}
      titleId="tenant-settings-title"
      description="Configure operational limits, custom domains and anonymous analytics preferences."
      size="md"
      footer={
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={savingSettings}>
            Cancel
          </Button>
          <Button
            form="tenant-settings-form"
            type="submit"
            variant="primary"
            busy={savingSettings}
            disabled={blocked}
          >
            Save Settings
          </Button>
        </div>
      }
    >
      {loadingSettings ? (
        <div role="status" aria-busy="true" aria-label="Loading tenant settings">
          <span className="sr-only">Loading tenant settings...</span>
          <SkeletonCard>
            <SkeletonText height="2.5rem" />
            <div className="app-form-grid">
              <SkeletonText height="2.5rem" />
              <SkeletonText height="2.5rem" />
            </div>
            <SkeletonText height="1rem" width="65%" />
          </SkeletonCard>
        </div>
      ) : (
        <form id="tenant-settings-form" onSubmit={onSubmit}>
          {settingsError && (
            <div style={{ marginBottom: "var(--space-3)" }}>
              <Notice tone="danger">{settingsError}</Notice>
            </div>
          )}
          <div style={{ marginBottom: "var(--space-3)" }}>
            <TextField
              id="settings-custom-domain"
              label="Custom Domain"
              value={settingsDraft.customDomain}
              onChange={(e) => setSettingsDraft({ ...settingsDraft, customDomain: e.target.value })}
              placeholder="e.g. portal.northbridge.edu"
              hint={plan && !plan.customDomainAllowed ? "This tenant's plan does not include a custom domain." : undefined}
              error={domainBlocked ? "Change the plan first: a custom domain is not part of it." : undefined}
            />
          </div>
          <div className="app-form-grid">
            <TextField
              id="settings-user-limit"
              label="User Limit"
              type="number"
              value={String(settingsDraft.userLimit)}
              onChange={(e) => setSettingsDraft({ ...settingsDraft, userLimit: Number(e.target.value) })}
              hint={usersBeyond && isProductOwner ? `${limitHint(plan?.userLimit)} - you are going beyond the plan.` : limitHint(plan?.userLimit)}
              error={usersBeyond && !isProductOwner ? BEYOND_PLAN : undefined}
            />
            <TextField
              id="settings-storage-limit"
              label="Storage Limit (GB)"
              type="number"
              value={String(settingsDraft.storageLimitGb)}
              onChange={(e) => setSettingsDraft({ ...settingsDraft, storageLimitGb: Number(e.target.value) })}
              hint={storageBeyond && isProductOwner ? `${limitHint(plan?.storageLimitGb)} - you are going beyond the plan.` : limitHint(plan?.storageLimitGb)}
              error={storageBeyond && !isProductOwner ? BEYOND_PLAN : undefined}
            />
          </div>
          <div style={{ margin: "var(--space-3) 0" }}>
            <CheckboxField
              id="settings-analytics-opt-out"
              label={<strong>Opt out of anonymous platform analytics</strong>}
              checked={settingsDraft.analyticsOptOut}
              onChange={(e) => setSettingsDraft({ ...settingsDraft, analyticsOptOut: e.target.checked })}
            />
          </div>
        </form>
      )}
    </Modal>
  );
}
