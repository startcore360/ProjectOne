import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { platformTenantService } from "../../api/platformTenantService";
import { TenantDataProtectionModal } from "./TenantDataProtectionModal";
import type { Tenant } from "../common/types";

vi.mock("../../api/platformTenantService", () => ({
  platformTenantService: {
    listExports: vi.fn(),
    exportTenant: vi.fn(),
    getRetentionPolicies: vi.fn(),
    requestRetentionChange: vi.fn(),
    requestTenantDeletion: vi.fn(),
  },
}));

const service = vi.mocked(platformTenantService);
const tenant = { id: "tenant-1", name: "Northfield School" } as Tenant;

beforeEach(() => {
  vi.resetAllMocks();
  service.listExports.mockResolvedValue({ data: [] } as never);
  service.getRetentionPolicies.mockResolvedValue({ data: [] } as never);
  service.exportTenant.mockResolvedValue({
    data: { exportRequestId: "exp-1", tenantId: "tenant-1", requestedByKind: "PRODUCT_OWNER", status: "REQUESTED", createdAt: "2026-11-01T00:00:00Z" },
  } as never);
  service.requestTenantDeletion.mockResolvedValue({
    data: { deletionRequestId: "del-1", tenantId: "tenant-1", origin: "PRODUCT_OWNER", status: "REQUESTED" },
  } as never);
  service.requestRetentionChange.mockResolvedValue({ data: {} } as never);
});

describe("TenantDataProtectionModal", () => {

  it("shows the backend export request identifier and status", async () => {
    service.listExports.mockResolvedValue({ data: [] } as never);
    render(<TenantDataProtectionModal isOpen onClose={vi.fn()} tenant={tenant} />);
    fireEvent.click(screen.getByRole("button", { name: "Trigger New Export" }));

    await waitFor(() => {
      expect(service.exportTenant).toHaveBeenCalledWith("tenant-1");
    });
    expect(await screen.findByText(/Export request created \(ID: exp-1\)/)).toBeInTheDocument();
  });

  it("requests deletion through the approval chain without claiming the tenant was deleted", async () => {
    render(<TenantDataProtectionModal isOpen onClose={vi.fn()} tenant={tenant} />);
    fireEvent.click(screen.getByRole("button", { name: /Tenant Deletion/i }));
    fireEvent.change(screen.getByPlaceholderText("Northfield School"), { target: { value: "Northfield School" } });
    fireEvent.click(screen.getByRole("button", { name: "Request deletion" }));

    await waitFor(() => {
      expect(service.requestTenantDeletion).toHaveBeenCalledWith("tenant-1", "PRODUCT_OWNER");
    });
    expect(await screen.findByText(/Requested.*Product Owner confirmation.*Data Protection Officer decision/)).toBeInTheDocument();
  });

  it("offers only the ref.data_class codes for retention requests", async () => {
    render(<TenantDataProtectionModal isOpen onClose={vi.fn()} tenant={tenant} />);
    fireEvent.click(screen.getByRole("button", { name: "Retention Policy" }));
    fireEvent.click(screen.getByRole("button", { name: "Request Policy Change" }));

    const select = await screen.findByLabelText("Data Category");
    expect(select.querySelectorAll("option")).toHaveLength(14);
    expect(screen.queryByRole("option", { name: "STUDENT_RECORDS" })).not.toBeInTheDocument();
  });
});
