import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Tenant } from "../common/types";
import { TenantSettingsModal, type TenantPlanLimits, type TenantSettingsDraft } from "./TenantSettingsModal";

const tenant: Tenant = { id: "ten-1", name: "Doon", type: "School", plan: "Standard", status: "Active", gates: 0, updated: "" };
const plan: TenantPlanLimits = {
  userLimit: 1000, storageLimitGb: 50, customDomainAllowed: false,
  currentUserLimit: 1000, currentStorageLimitGb: 50, currentCustomDomain: "",
};
const draft = (over: Partial<TenantSettingsDraft> = {}): TenantSettingsDraft => ({
  timeZone: "Asia/Kolkata", dataRegion: "IN", analyticsOptOut: false, customDomain: "", userLimit: 1000, storageLimitGb: 50, ...over,
});

const renderModal = (d: TenantSettingsDraft, isProductOwner: boolean) =>
  render(
    <TenantSettingsModal isOpen onClose={vi.fn()} onSubmit={vi.fn()} tenant={tenant} settingsDraft={d}
      setSettingsDraft={vi.fn()} loadingSettings={false} savingSettings={false} settingsError=""
      plan={plan} isProductOwner={isProductOwner} />,
  );

describe("TenantSettingsModal (E1-S3)", () => {
  afterEach(cleanup);

  it("shows the plan limit beside each field", () => {
    renderModal(draft(), false);
    expect(screen.getByText("Plan limit: 1000")).toBeTruthy();
    expect(screen.getByText("Plan limit: 50")).toBeTruthy();
  });

  it("blocks a Platform Admin from going beyond the plan", () => {
    renderModal(draft({ userLimit: 1500 }), false);
    expect(screen.getByText(/Beyond the plan - Product Owner only/)).toBeTruthy();
    expect((screen.getByRole("button", { name: /Save Settings/i }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("lets the Product Owner go beyond the plan, with a reminder", () => {
    renderModal(draft({ userLimit: 1500 }), true);
    expect(screen.queryByText(/Product Owner only/)).toBeNull();
    expect(screen.getByText(/you are going beyond the plan/)).toBeTruthy();
    expect((screen.getByRole("button", { name: /Save Settings/i }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("refuses a custom domain the plan does not include, for everyone", () => {
    renderModal(draft({ customDomain: "portal.doon.edu" }), true);
    expect(screen.getByText(/a custom domain is not part of it/)).toBeTruthy();
    expect((screen.getByRole("button", { name: /Save Settings/i }) as HTMLButtonElement).disabled).toBe(true);
  });
});
