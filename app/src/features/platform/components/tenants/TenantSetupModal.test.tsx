import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { platformTenantService, type ProvisioningStatus } from "../../api/platformTenantService";
import { TenantSetupModal } from "./TenantSetupModal";

const CODES = ["P01_REGISTRY", "P09_RETENTION", "P05_ENTITLEMENTS", "P06_IDENTITY_REALM", "P10_REALM_POLICY", "P07_FIRST_ADMIN", "P04_ROLES_SEED"];

const status = (failed: string, doneBefore: boolean): ProvisioningStatus => ({
  tenantId: "ten-1",
  overallStatus: "FAILED",
  retryable: true,
  steps: CODES.map((code, i) => ({
    executionOrder: i + 1,
    planStep: `P0${i + 1}`,
    stepCode: code,
    description: `Step ${code}`,
    status: code === failed ? "FAILED" : doneBefore && CODES.indexOf(code) < CODES.indexOf(failed) ? "DONE" : "PENDING",
    attempts: code === failed ? 1 : 0,
    lastErrorCode: code === failed ? "KEYCLOAK_UNAVAILABLE" : null,
    startedAt: null,
    finishedAt: null,
  })),
});

describe("TenantSetupModal (E1-S2)", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("shows which step failed and why, and retries without asking for retention when it is already saved", async () => {
    vi.spyOn(platformTenantService, "getProvisioning").mockResolvedValue({ data: status("P06_IDENTITY_REALM", true) } as never);
    const retry = vi.spyOn(platformTenantService, "retryProvisioning").mockResolvedValue({
      data: { tenantName: "Doon", subdomain: "doon", firstAdminTemporaryPassword: "Xy7#kLm2Qw9!pRt4" },
    } as never);
    const onRetried = vi.fn();
    render(<TenantSetupModal isOpen tenantId="ten-1" tenantName="Doon" canRetry onClose={vi.fn()} onRetried={onRetried} />);

    expect(await screen.findByText(/sign-in service could not be reached/i)).toBeTruthy();
    expect(screen.getAllByText("Failed").length).toBe(1);
    expect(screen.queryByLabelText(/Data retention/)).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Retry setup" }));
    await waitFor(() => expect(onRetried).toHaveBeenCalled());
    expect(retry).toHaveBeenCalledWith("ten-1", undefined);
  });

  it("asks for retention again only when the retention step itself did not finish", async () => {
    vi.spyOn(platformTenantService, "getProvisioning").mockResolvedValue({ data: status("P09_RETENTION", true) } as never);
    const retry = vi.spyOn(platformTenantService, "retryProvisioning").mockResolvedValue({ data: { tenantName: "Doon" } } as never);
    render(<TenantSetupModal isOpen tenantId="ten-1" tenantName="Doon" canRetry onClose={vi.fn()} onRetried={vi.fn()} />);

    const months = await screen.findByLabelText(/Data retention \(months\)/);
    const button = screen.getByRole("button", { name: "Retry setup" }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    fireEvent.change(months, { target: { value: "84" } });
    fireEvent.change(screen.getByLabelText(/Backup retention \(days\)/), { target: { value: "35" } });
    fireEvent.click(screen.getByRole("button", { name: "Retry setup" }));
    await waitFor(() => expect(retry).toHaveBeenCalledWith("ten-1", { retentionMonths: 84, backupRetentionDays: 35 }));
  });

  it("hides Retry from someone who cannot create tenants", async () => {
    vi.spyOn(platformTenantService, "getProvisioning").mockResolvedValue({ data: status("P06_IDENTITY_REALM", true) } as never);
    render(<TenantSetupModal isOpen tenantId="ten-1" tenantName="Doon" canRetry={false} onClose={vi.fn()} onRetried={vi.fn()} />);
    expect(await screen.findByText(/Only someone who can create tenants/)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Retry setup" })).toBeNull();
  });
});
