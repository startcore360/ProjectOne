import type { FormEvent } from "react";
import { Button, Kicker, TextField, Notice } from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import { Icon } from "../../../../components/ui/Icon";
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
    <div
      className="platform-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="tenant-settings-title">
        <header>
          <div>
            <Kicker>TENANT SETTINGS</Kicker>
            <h2 id="tenant-settings-title">Tenant Settings: {tenant.name}</h2>
            <p>Configure operational limits, custom domains and anonymous analytics preferences.</p>
          </div>
          <button className="platform-icon-button" onClick={onClose} aria-label="Close">
            <Icon name="x" size={16} />
          </button>
        </header>
        {loadingSettings ? (
          <div className="platform-empty-inline">
            <Spinner label="Loading tenant settings" />
          </div>
        ) : (
          <form onSubmit={onSubmit}>
            {settingsError && <Notice tone="danger">{settingsError}</Notice>}
            <div style={{ marginBottom: "1rem" }}>
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
            <div className="platform-form-grid">
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
            <div style={{ margin: "1rem 0" }}>
              <label style={{ display: "flex", alignItems: "center", gap: "8px", cursor: "pointer", fontSize: "0.875rem" }}>
                <input
                  type="checkbox"
                  checked={settingsDraft.analyticsOptOut}
                  onChange={(e) => setSettingsDraft({ ...settingsDraft, analyticsOptOut: e.target.checked })}
                />
                <span>
                  <strong>Opt out of anonymous platform analytics</strong>
                </span>
              </label>
            </div>
            <footer style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
              <Button type="button" variant="secondary" onClick={onClose} disabled={savingSettings}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" busy={savingSettings} disabled={blocked}>
                Save Settings
              </Button>
            </footer>
          </form>
        )}
      </section>
    </div>
  );
}
