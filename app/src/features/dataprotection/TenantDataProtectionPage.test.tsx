import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import authReducer, { setUser } from "../../store/slices/authSlice";
import tenantPermissionsReducer, { setTenantPermissions } from "../../store/slices/tenantPermissionsSlice";
import TenantDataProtectionPage from "./TenantDataProtectionPage";
import { tenantDataProtectionService } from "./tenantDataProtectionService";

vi.mock("./tenantDataProtectionService", () => ({
  tenantDataProtectionService: {
    listExports: vi.fn(),
    requestExport: vi.fn(),
    downloadExport: vi.fn(),
    listDeletions: vi.fn(),
    requestDeletion: vi.fn(),
  },
}));

const service = vi.mocked(tenantDataProtectionService);

function renderPage(actions = ["TENANT_EXPORT_REQUEST", "TENANT_DELETION_REQUEST"]) {
  const store = configureStore({ reducer: { auth: authReducer, tenantPermissions: tenantPermissionsReducer } });
  store.dispatch(setUser({
    id: "tenant-user-1", displayName: "Tenant Admin", tenantId: "tenant-1", userType: "STAFF",
    platformRole: null, roleCode: "SCHOOL_ADMIN", roleName: "School Admin", photoUrl: null, mustChangePassword: false,
  }));
  store.dispatch(setTenantPermissions({
    tenantId: "tenant-1", userAccountId: "tenant-user-1", activeRoleCode: "SCHOOL_ADMIN",
    activeRoleName: "School Admin", allowedActions: actions,
  }));
  return render(<Provider store={store}><TenantDataProtectionPage /></Provider>);
}

beforeEach(() => {
  vi.clearAllMocks();
  service.listExports.mockResolvedValue({ data: [] } as never);
  service.listDeletions.mockResolvedValue({ data: [] } as never);
  service.requestExport.mockResolvedValue({ data: {} } as never);
  service.requestDeletion.mockResolvedValue({ data: {} } as never);
  service.downloadExport.mockResolvedValue({ data: new Blob(["zip"]) } as never);
});

describe("TenantDataProtectionPage", () => {
  it("loads tenant-scoped history and exposes only the actions granted to the active role", async () => {
    service.listExports.mockResolvedValue({ data: [{
      exportRequestId: "export-1", tenantId: "tenant-1", tenantName: "Northfield", status: "READY",
      requestedByKind: "TENANT_ADMIN", createdAt: "2026-11-01T10:00:00Z",
      downloadExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(), rowVersion: 1,
    }] } as never);
    renderPage(["TENANT_EXPORT_REQUEST"]);

    expect(await screen.findByText("export-1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Request data export" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Request tenant deletion" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Download ZIP" })).toBeInTheDocument();
    expect(service.listExports).toHaveBeenCalledWith("tenant-1");
    expect(service.listDeletions).toHaveBeenCalledWith("tenant-1");
  });

  it("submits an export request and refreshes both histories", async () => {
    renderPage(["TENANT_EXPORT_REQUEST"]);
    fireEvent.click(await screen.findByRole("button", { name: "Request data export" }));

    await waitFor(() => expect(service.requestExport).toHaveBeenCalledWith("tenant-1"));
    expect(await screen.findByRole("status")).toHaveTextContent("The tenant export request was submitted");
    expect(service.listExports).toHaveBeenCalledTimes(2);
  });

  it("prevents a second deletion request while an existing request remains open", async () => {
    service.listDeletions.mockResolvedValue({ data: [{
      deletionRequestId: "deletion-1", tenantId: "tenant-1", tenantName: "Northfield",
      origin: "TENANT_ADMIN", status: "EXPORT_DONE", createdAt: "2026-11-01T10:00:00Z",
    }] } as never);
    renderPage(["TENANT_DELETION_REQUEST"]);

    expect(await screen.findByText("deletion-1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Request tenant deletion" })).toBeDisabled();
    expect(screen.getByText(/An open deletion request already exists/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Request data export" })).not.toBeInTheDocument();
  });

  it("does not offer downloads for expired exports", async () => {
    service.listExports.mockResolvedValue({ data: [{
      exportRequestId: "export-expired", tenantId: "tenant-1", tenantName: "Northfield",
      status: "READY", requestedByKind: "TENANT_ADMIN", createdAt: "2026-11-01T10:00:00Z",
      downloadExpiresAt: "2026-11-02T10:00:00Z", rowVersion: 1,
    }] } as never);
    renderPage(["TENANT_EXPORT_REQUEST"]);

    expect(await screen.findByText("export-expired")).toBeInTheDocument();
    expect(screen.getByText("Expired")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Download ZIP" })).not.toBeInTheDocument();
  });
});
