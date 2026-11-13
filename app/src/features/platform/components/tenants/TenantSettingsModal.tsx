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
}: TenantSettingsModalProps) {
  if (!isOpen || !tenant) return null;

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
              />
            </div>
            <div className="platform-form-grid">
              <TextField
                id="settings-user-limit"
                label="User Limit"
                type="number"
                value={String(settingsDraft.userLimit)}
                onChange={(e) => setSettingsDraft({ ...settingsDraft, userLimit: Number(e.target.value) })}
              />
              <TextField
                id="settings-storage-limit"
                label="Storage Limit (GB)"
                type="number"
                value={String(settingsDraft.storageLimitGb)}
                onChange={(e) => setSettingsDraft({ ...settingsDraft, storageLimitGb: Number(e.target.value) })}
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
              <Button type="submit" variant="primary" busy={savingSettings}>
                Save Settings
              </Button>
            </footer>
          </form>
        )}
      </section>
    </div>
  );
}
