import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import authReducer, { setUser } from "../../../../store/slices/authSlice";
import { DataProtectionWorkspacePage } from "./DataProtectionWorkspaceView";
import { platformDataProtectionService, platformDelegationService } from "../../api/platformDataProtectionService";

vi.mock("../../api/platformDataProtectionService", () => ({
  platformDataProtectionService: {
    listExports: vi.fn(),
    listDeletions: vi.fn(),
    listRetentionChanges: vi.fn(),
    decideExport: vi.fn(),
    decideDeletion: vi.fn(),
    decideRetentionChange: vi.fn(),
    getRbacMappingReport: vi.fn(),
    downloadRbacMappingReport: vi.fn(),
  },
  platformDelegationService: { candidates: vi.fn(), create: vi.fn() },
}));

const service = vi.mocked(platformDataProtectionService);

function renderForRole(platformRole: "DATA_PROTECTION_OFFICER" | "PLATFORM_ADMIN") {
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
  return render(<Provider store={store}><DataProtectionWorkspacePage /></Provider>);
}

const exportRow = {
  exportRequestId: "exp-1",
  tenantId: "tenant-1",
  tenantName: "Northfield School",
  requestedByKind: "PRODUCT_OWNER",
  status: "REQUESTED",
  createdAt: "2026-11-01T00:00:00Z",
  rowVersion: 2,
};

beforeEach(() => {
  vi.clearAllMocks();
  service.listExports.mockResolvedValue({ data: [] } as never);
  service.listDeletions.mockResolvedValue({ data: [] } as never);
  service.listRetentionChanges.mockResolvedValue({ data: [] } as never);
  vi.mocked(platformDelegationService.candidates).mockResolvedValue({data:[{userId:"dpo-2",fullName:"Second DPO",email:"second@example.test"}]} as never);
  vi.mocked(platformDelegationService.create).mockResolvedValue({data:{}} as never);
  service.getRbacMappingReport.mockResolvedValue({ data: { mismatches: [], noBaseTemplate: [], skippedTenants: [] } } as never);
  service.downloadRbacMappingReport.mockResolvedValue(undefined);
  service.decideExport.mockResolvedValue({ data: {} } as never);
  service.decideDeletion.mockResolvedValue({ data: {} } as never);
  service.decideRetentionChange.mockResolvedValue({ data: {} } as never);
});

describe("DataProtectionWorkspacePage", () => {
  it("loads the queues, displays tenant names, and opens a review modal for the DPO", async () => {
    service.listExports.mockResolvedValue({ data: [exportRow] } as never);

    renderForRole("DATA_PROTECTION_OFFICER");

    expect(await screen.findByText("Northfield School")).toBeInTheDocument();
    expect(screen.queryByText("tenant-1")).not.toBeInTheDocument();
    expect(service.listDeletions).toHaveBeenCalledTimes(1);
    expect(service.listRetentionChanges).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "Review" }));
    expect(screen.getByRole("dialog", { name: "Review data protection request" })).toBeInTheDocument();
  });

  it("does not show Review to a non-DPO role", async () => {
    service.listExports.mockResolvedValue({ data: [exportRow] } as never);

    renderForRole("PLATFORM_ADMIN");

    expect(await screen.findByText("Northfield School")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Review" })).not.toBeInTheDocument();
  });

  it("shows Delegate section and saves DPO delegation", async () => {
    renderForRole("DATA_PROTECTION_OFFICER");
    expect(await screen.findByRole("heading",{name:"Delegate DPO approvals"})).toBeInTheDocument();
    fireEvent.change(await screen.findByLabelText("Data Protection Officer"),{target:{value:"dpo-2"}});
    fireEvent.change(screen.getByLabelText("Delegation from"),{target:{value:"2026-12-01"}});
    fireEvent.change(screen.getByLabelText("Delegation to"),{target:{value:"2026-12-15"}});
    fireEvent.click(screen.getByRole("button",{name:"Save delegation"}));
    expect(await screen.findByRole("status")).toHaveTextContent("DPO delegation saved");
    expect(platformDelegationService.create).toHaveBeenCalledWith({delegateUserId:"dpo-2",from:"2026-12-01",to:"2026-12-15"});
  });
  it("shows the design-system error state when a queue request fails", async () => {
    service.listExports.mockRejectedValueOnce(new Error("network failure"));

    renderForRole("DATA_PROTECTION_OFFICER");

    expect(await screen.findByRole("alert")).toHaveTextContent("Data protection queues unavailable");
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("blocks rejection without a remark and enables it when a remark is entered", async () => {
    service.listExports.mockResolvedValue({ data: [exportRow] } as never);

    renderForRole("DATA_PROTECTION_OFFICER");
    fireEvent.click(await screen.findByRole("button", { name: "Review" }));

    fireEvent.change(screen.getByLabelText("Decision"), { target: { value: "REJECTED" } });
    const rejectButton = screen.getByRole("button", { name: "Reject request" });
    expect(rejectButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText("Decision remark (Required)"), {
      target: { value: "Insufficient supporting information." },
    });
    expect(rejectButton).toBeEnabled();
  });

  it("closes the review modal on Escape", async () => {
    service.listExports.mockResolvedValue({ data: [exportRow] } as never);

    renderForRole("DATA_PROTECTION_OFFICER");
    fireEvent.click(await screen.findByRole("button", { name: "Review" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("E5-S2: shows the retention wait and no Review button while a deletion is EXPORT_DONE", async () => {
    service.listDeletions.mockResolvedValue({ data: [{
      deletionRequestId: "del-1", tenantId: "tenant-1", tenantName: "Northfield School",
      origin: "PRODUCT_OWNER", status: "EXPORT_DONE", retentionEndsAt: "2027-01-01",
      createdAt: "2026-11-01T00:00:00Z",
    }] } as never);

    renderForRole("DATA_PROTECTION_OFFICER");
    fireEvent.click(await screen.findByRole("tab", { name: /Deletion requests/ }));

    expect(await screen.findByText("Waiting: retention ends 2027-01-01")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Review" })).not.toBeInTheDocument();
  });

  it("E5-S2: shows the Product Owner's waiver reason to the DPO when reviewing", async () => {
    service.listDeletions.mockResolvedValue({ data: [{
      deletionRequestId: "del-2", tenantId: "tenant-1", tenantName: "Northfield School",
      origin: "PRODUCT_OWNER", status: "PENDING_DPO", retentionEndsAt: "2027-01-01",
      retentionWaivedAt: "2026-11-02T00:00:00Z", retentionWaiverReason: "Approved closure plan",
      createdAt: "2026-11-01T00:00:00Z",
    }] } as never);

    renderForRole("DATA_PROTECTION_OFFICER");
    fireEvent.click(await screen.findByRole("tab", { name: /Deletion requests/ }));
    fireEvent.click(await screen.findByRole("button", { name: "Review" }));

    expect(screen.getByText("Approved closure plan")).toBeInTheDocument();
  });
});

describe("E5-S7 RBAC mapping check tab", () => {
  it("loads an empty report and provides CSV export to the DPO", async () => {
    renderForRole("DATA_PROTECTION_OFFICER");
    fireEvent.click(await screen.findByRole("tab", { name: "RBAC mapping check" }));
    expect(await screen.findByText("No permission mismatches")).toBeInTheDocument();
    expect(service.getRbacMappingReport).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "Export CSV" }));
    expect(service.downloadRbacMappingReport).toHaveBeenCalledTimes(1);
  });

  it("shows base-template drift and unbased custom roles in separate groups", async () => {
    service.getRbacMappingReport.mockResolvedValueOnce({ data: {
      mismatches: [{ scope: "TENANT", tenantId: "t-1", tenantName: "Northfield", roleCode: "CUSTOM_A",
        roleName: "Custom A", actionCode: "GRADE_EDIT", result: "BASE_TEMPLATE_DRIFT", detail: "Extra action." }],
      noBaseTemplate: [{ scope: "TENANT", tenantId: "t-1", tenantName: "Northfield", roleCode: "CUSTOM_B",
        roleName: "Custom B", actionCode: null, result: "NO_BASE_TEMPLATE", detail: "No base recorded." }],
      skippedTenants: [{ tenantId: "t-2", tenantName: "New tenant", state: "CREATED" }],
    } } as never);
    renderForRole("DATA_PROTECTION_OFFICER");
    fireEvent.click(await screen.findByRole("tab", { name: "RBAC mapping check" }));
    expect(await screen.findByText("BASE_TEMPLATE_DRIFT")).toBeInTheDocument();
    expect(screen.getByText("NO_BASE_TEMPLATE")).toBeInTheDocument();
    expect(screen.getByText("Skipped tenants")).toBeInTheDocument();
  });

  it("fails closed for a platform role without RBAC_MAPPING_CHECK", async () => {
    renderForRole("PLATFORM_ADMIN");
    fireEvent.click(await screen.findByRole("tab", { name: "RBAC mapping check" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("do not have permission");
    expect(service.getRbacMappingReport).not.toHaveBeenCalled();
  });

  it("shows a retryable error when the report cannot be loaded", async () => {
    service.getRbacMappingReport.mockRejectedValueOnce(new Error("network failure"));
    renderForRole("DATA_PROTECTION_OFFICER");
    fireEvent.click(await screen.findByRole("tab", { name: "RBAC mapping check" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("RBAC mapping report unavailable");
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });
});
