import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import authReducer from "../../../../store/slices/authSlice";
import { PlatformAdminDashboard } from "./PlatformAdminDashboard";
import { platformTenantService } from "../../api/platformTenantService";
import { platformOpsService } from "../../api/platformOpsService";

const access = vi.hoisted(() => ({
  can: vi.fn<(action: string) => boolean>(),
  canAny: vi.fn<(actions: string[]) => boolean>(),
}));

vi.mock("../../platformAccess", () => ({ usePlatformAccess: () => access }));
vi.mock("../../api/platformTenantService", () => ({
  platformTenantService: {
    getTenants: vi.fn(),
  },
}));
vi.mock("../../api/platformOpsService", () => ({
  platformOpsService: {
    getPlatformBackupStatus: vi.fn(),
    listSupportTickets: vi.fn(),
  },
}));

const tenants = vi.mocked(platformTenantService);
const ops = vi.mocked(platformOpsService);

function renderDashboard() {
  const store = configureStore({ reducer: { auth: authReducer } });
  return render(<Provider store={store}><PlatformAdminDashboard /></Provider>);
}

function resolveAllEmpty() {
  tenants.getTenants.mockResolvedValue({
    data: { items: [], page: 0, size: 200, totalElements: 0, totalPages: 0 },
  } as never);
  ops.listSupportTickets.mockResolvedValue([] as never);
  ops.getPlatformBackupStatus.mockResolvedValue({ tenants: [] } as never);
}

describe("PlatformAdminDashboard tile states", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    access.can.mockReturnValue(true);
    access.canAny.mockReturnValue(true);
    resolveAllEmpty();
  });

  it("shows loading states while each source is pending", () => {
    tenants.getTenants.mockReturnValue(new Promise(() => {}) as never);
    ops.listSupportTickets.mockReturnValue(new Promise(() => {}) as never);
    ops.getPlatformBackupStatus.mockReturnValue(new Promise(() => {}) as never);

    renderDashboard();

    expect(screen.getByText("Loading tenants pending activation...")).toBeInTheDocument();
    expect(screen.getByText("Loading failed setups...")).toBeInTheDocument();
    expect(screen.getByText("Loading open tickets...")).toBeInTheDocument();
    expect(screen.getByText("Loading backup alerts...")).toBeInTheDocument();
  });

  it("loads the tenant list once and counts CREATED tenants as failed setups", async () => {
    tenants.getTenants.mockResolvedValue({
      data: { items: [
        { tenantId: "trial-1", state: "TRIAL" },
        { tenantId: "created-1", state: "CREATED" },
        { tenantId: "created-2", state: "CREATED" },
        { tenantId: "active-1", state: "ACTIVE" },
      ], page: 0, size: 200, totalElements: 4, totalPages: 1 },
    } as never);

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText("Tenants pending activation")).toBeInTheDocument();
      expect(screen.getByText("Failed setups")).toBeInTheDocument();
    });
    expect(tenants.getTenants).toHaveBeenCalledTimes(1);
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("shows real zero counts for empty tenant, setup and ticket sources", async () => {
    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText("Tenants pending activation")).toBeInTheDocument();
      expect(screen.getByText("Failed setups")).toBeInTheDocument();
      expect(screen.getByText("Open tickets")).toBeInTheDocument();
    });
    expect(screen.getAllByText("0").length).toBeGreaterThanOrEqual(3);
    expect(screen.getByText("Failed jobs")).toBeInTheDocument();
    expect(screen.getAllByText("Not measured")).toHaveLength(2);
    expect(screen.getByText("Backup alerts")).toBeInTheDocument();
  });

  it("shows an error for a failed source without hiding other tiles", async () => {
    ops.listSupportTickets.mockRejectedValue(new Error("ticket service unavailable"));

    renderDashboard();

    expect(await screen.findByText("ticket service unavailable")).toBeInTheDocument();
    expect(screen.getByText("Failed jobs")).toBeInTheDocument();
    expect(screen.getAllByText("Not measured")).toHaveLength(2);
  });

  it("denies individual tiles when the corresponding permission is absent", async () => {
    access.can.mockImplementation(action => action !== "BACKUP_STATUS_VIEW" && action !== "SUPPORT_TICKET_MANAGE");
    access.canAny.mockReturnValue(true);

    renderDashboard();

    await waitFor(() => expect(screen.getAllByText("Not available for this role").length).toBeGreaterThanOrEqual(2));
    expect(screen.getByText("Failed jobs")).toBeInTheDocument();
    expect(screen.getByText("Not measured")).toBeInTheDocument();
  });

  it("shows Not measured for backup rows without the server-computed stale flag", async () => {
    ops.getPlatformBackupStatus.mockResolvedValue({
      tenants: [{ tenantId: "tenant-1" }],
    } as never);

    renderDashboard();

    await waitFor(() => expect(screen.getByText("Backup alerts")).toBeInTheDocument());
    expect(screen.getAllByText("Not measured").length).toBeGreaterThanOrEqual(2);
  });
});
