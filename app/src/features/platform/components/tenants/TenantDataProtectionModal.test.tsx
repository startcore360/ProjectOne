import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import authReducer, { setUser } from "../../../../store/slices/authSlice";
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
    listDeletionRequests: vi.fn(),
    confirmTenantDeletion: vi.fn(),
  },
}));

const service = vi.mocked(platformTenantService);
const tenant = { id: "tenant-1", name: "Northfield School" } as Tenant;

function renderModal(platformRole: "PRODUCT_OWNER" | "DATA_PROTECTION_OFFICER" = "PRODUCT_OWNER") {
  const store = configureStore({ reducer: { auth: authReducer } });
  store.dispatch(setUser({
    id: "platform-user-1",
    displayName: "Platform User",
    tenantId: null,
    userType: null,
    platformRole,
    roleCode: null,
    roleName: null,
    photoUrl: null,
    mustChangePassword: false,
  }));
  return render(<Provider store={store}><TenantDataProtectionModal isOpen onClose={vi.fn()} tenant={tenant} /></Provider>);
}

beforeEach(() => {
  vi.resetAllMocks();
  service.listExports.mockResolvedValue({ data: [] } as never);
  service.listDeletionRequests.mockResolvedValue({ data: [] } as never);
  service.getRetentionPolicies.mockResolvedValue({ data: [] } as never);
  service.exportTenant.mockResolvedValue({
    data: { exportRequestId: "exp-1", tenantId: "tenant-1", requestedByKind: "PRODUCT_OWNER", status: "REQUESTED", createdAt: "2026-11-01T00:00:00Z" },
  } as never);
  service.requestTenantDeletion.mockResolvedValue({
    data: { deletionRequestId: "del-1", tenantId: "tenant-1", origin: "PRODUCT_OWNER", status: "REQUESTED" },
  } as never);
  service.confirmTenantDeletion.mockResolvedValue({ data: { deletionRequestId: "del-1", tenantId: "tenant-1", status: "PO_CONFIRMED" } } as never);
  service.requestRetentionChange.mockResolvedValue({ data: { status: "REQUESTED" } } as never);
});

describe("TenantDataProtectionModal", () => {

  it("shows the backend export request identifier and status", async () => {
    service.listExports.mockResolvedValue({ data: [] } as never);
    renderModal();
    fireEvent.click(screen.getByRole("button", { name: "Trigger New Export" }));

    await waitFor(() => {
      expect(service.exportTenant).toHaveBeenCalledWith("tenant-1");
    });
    expect(await screen.findByText(/Export request created \(ID: exp-1\)/)).toBeInTheDocument();
  });

  it("requests deletion through the approval chain without claiming the tenant was deleted", async () => {
    renderModal();
    fireEvent.click(screen.getByRole("button", { name: /Tenant Deletion/i }));
    fireEvent.change(screen.getByPlaceholderText("Northfield School"), { target: { value: "Northfield School" } });
    fireEvent.click(screen.getByRole("button", { name: "Request deletion" }));

    await waitFor(() => {
      expect(service.requestTenantDeletion).toHaveBeenCalledWith("tenant-1", "PRODUCT_OWNER");
    });
    expect(await screen.findByText(/Requested.*Product Owner confirmation.*Data Protection Officer decision/)).toBeInTheDocument();
  });

  it("offers only the ref.data_class codes for retention requests", async () => {
    renderModal();
    fireEvent.click(screen.getByRole("button", { name: "Retention Policy" }));
    fireEvent.click(screen.getByRole("button", { name: "Request Policy Change" }));

    const select = await screen.findByLabelText("Data Category");
    expect(Array.from((select as HTMLSelectElement).options).map((option) => option.value)).toEqual([
      "STRUCTURE", "PERSON", "ACADEMIC_RECORD", "FINANCIAL", "COMMUNICATION",
      "STUDENT_NOTE", "GOVERNANCE", "APPROVAL", "AUDIT", "BACKUP",
      "OPERATIONAL", "BY_OWNER", "TENANT_LIFETIME", "REFERENCE",
    ]);
  });

  it("allows the Product Owner to confirm an existing deletion request", async () => {
    service.listDeletionRequests.mockResolvedValue({ data: [{
      deletionRequestId: "del-1", tenantId: "tenant-1", tenantName: "Northfield School",
      origin: "PRODUCT_OWNER", status: "REQUESTED", createdAt: "2026-11-01T00:00:00Z",
    }] } as never);
    renderModal("PRODUCT_OWNER");
    fireEvent.click(screen.getByRole("button", { name: /Tenant Deletion/i }));

    fireEvent.click(await screen.findByRole("button", { name: "Confirm request" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm deletion request" }));
    await waitFor(() => expect(service.confirmTenantDeletion).toHaveBeenCalledWith("del-1", {
      waiveRetentionWait: false,
      waiverReason: undefined,
    }));
    expect(await screen.findByText(/once the tenant export is ready and the retention period has ended/)).toBeInTheDocument();
  });

  it("E5-S2: requires a reason before the Product Owner can waive the retention wait", async () => {
    service.listDeletionRequests.mockResolvedValue({ data: [{
      deletionRequestId: "del-1", tenantId: "tenant-1", tenantName: "Northfield School",
      origin: "PRODUCT_OWNER", status: "REQUESTED", createdAt: "2026-11-01T00:00:00Z",
    }] } as never);
    renderModal("PRODUCT_OWNER");
    fireEvent.click(screen.getByRole("button", { name: /Tenant Deletion/i }));
    fireEvent.click(await screen.findByRole("button", { name: "Confirm request" }));

    fireEvent.click(screen.getByRole("checkbox", { name: /Waive the retention wait/ }));
    const confirm = screen.getByRole("button", { name: "Confirm deletion request" });
    expect(confirm).toBeDisabled();

    fireEvent.change(screen.getByLabelText("Waiver reason (Required)"), { target: { value: "  Approved closure plan  " } });
    expect(confirm).toBeEnabled();
    fireEvent.click(confirm);
    await waitFor(() => expect(service.confirmTenantDeletion).toHaveBeenCalledWith("del-1", {
      waiveRetentionWait: true,
      waiverReason: "Approved closure plan",
    }));
  });

  it("E5-S2: offers no waiver on a request that did not come from the Product Owner", async () => {
    service.listDeletionRequests.mockResolvedValue({ data: [{
      deletionRequestId: "del-2", tenantId: "tenant-1", tenantName: "Northfield School",
      origin: "TENANT_ADMIN", status: "REQUESTED", createdAt: "2026-11-01T00:00:00Z",
    }] } as never);
    renderModal("PRODUCT_OWNER");
    fireEvent.click(screen.getByRole("button", { name: /Tenant Deletion/i }));
    fireEvent.click(await screen.findByRole("button", { name: "Confirm request" }));

    expect(screen.queryByRole("checkbox", { name: /Waive the retention wait/ })).not.toBeInTheDocument();
  });

  it("E5-S2: shows the retention wait once the export is done", async () => {
    service.listDeletionRequests.mockResolvedValue({ data: [{
      deletionRequestId: "del-3", tenantId: "tenant-1", tenantName: "Northfield School",
      origin: "PRODUCT_OWNER", status: "EXPORT_DONE", retentionEndsAt: "2027-11-17", createdAt: "2026-11-01T00:00:00Z",
    }] } as never);
    renderModal("PRODUCT_OWNER");
    fireEvent.click(screen.getByRole("button", { name: /Tenant Deletion/i }));

    expect(await screen.findByText("Waiting: retention ends 2027-11-17")).toBeInTheDocument();
  });

  it("does not show Product Owner confirmation controls to a DPO", async () => {
    service.listDeletionRequests.mockResolvedValue({ data: [{
      deletionRequestId: "del-1", tenantId: "tenant-1", tenantName: "Northfield School",
      origin: "PRODUCT_OWNER", status: "REQUESTED", createdAt: "2026-11-01T00:00:00Z",
    }] } as never);
    renderModal("DATA_PROTECTION_OFFICER");
    fireEvent.click(screen.getByRole("button", { name: /Tenant Deletion/i }));

    await waitFor(() => expect(service.listDeletionRequests).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("button", { name: "Confirm request" })).not.toBeInTheDocument();
  });
});
