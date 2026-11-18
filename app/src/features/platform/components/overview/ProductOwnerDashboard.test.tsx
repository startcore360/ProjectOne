import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import authReducer from "../../../../store/slices/authSlice";
import { ProductOwnerDashboard } from "./ProductOwnerDashboard";
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
    getProvisioning: vi.fn(),
  },
}));
vi.mock("../../api/platformOpsService", () => ({
  platformOpsService: {
    getReport: vi.fn(),
    getUsageCounts: vi.fn(),
    getPlatformBackupStatus: vi.fn(),
    listSupportTickets: vi.fn(),
  },
}));

const tenants = vi.mocked(platformTenantService);
const ops = vi.mocked(platformOpsService);

function renderDashboard() {
  const store = configureStore({ reducer: { auth: authReducer } });
  return render(<Provider store={store}><ProductOwnerDashboard /></Provider>);
}

function allowAll() {
  access.can.mockReturnValue(true);
  access.canAny.mockReturnValue(true);
}

function resolveAllEmpty() {
  tenants.getTenants.mockResolvedValue({ data: { items: [], page: 0, size: 200, totalElements: 0, totalPages: 0 } } as never);
  tenants.getProvisioning.mockResolvedValue({ data: { overallStatus: "DONE" } } as never);
  ops.getReport.mockResolvedValue({ currency: "INR", metrics: { totalCollectionsThisMonth: 0 } } as never);
  ops.getUsageCounts.mockResolvedValue({ metrics: [{ metricCode: "ACTIVE_USERS", value: null }] } as never);
  ops.getPlatformBackupStatus.mockResolvedValue({ tenants: [] } as never);
  ops.listSupportTickets.mockResolvedValue([] as never);
}

describe("ProductOwnerDashboard tile states", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    allowAll();
    resolveAllEmpty();
  });

  it("shows independent loading states while metric requests are pending", () => {
    tenants.getTenants.mockReturnValue(new Promise(() => {}) as never);
    ops.getReport.mockReturnValue(new Promise(() => {}) as never);
    ops.getUsageCounts.mockReturnValue(new Promise(() => {}) as never);
    ops.getPlatformBackupStatus.mockReturnValue(new Promise(() => {}) as never);
    ops.listSupportTickets.mockReturnValue(new Promise(() => {}) as never);

    renderDashboard();

    expect(screen.getByText("Loading tenants by state...")).toBeInTheDocument();
    expect(screen.getByText("Loading revenue this month...")).toBeInTheDocument();
    expect(screen.getByText("Loading active users...")).toBeInTheDocument();
    expect(screen.getByText("Loading stale backups...")).toBeInTheDocument();
  });

  it("shows empty-source values honestly and retains explicitly unmeasured tiles", async () => {
    renderDashboard();

    await waitFor(() => expect(screen.getByText("0 tenants")).toBeInTheDocument());
    expect(screen.getByText("INR 0")).toBeInTheDocument();
    expect(screen.getByText("Open escalations")).toBeInTheDocument();
    expect(screen.getByText("Not measured — awaiting data-protection count endpoint")).toBeInTheDocument();
    expect(screen.getAllByText("Not measured").length).toBeGreaterThanOrEqual(2);
  });

  it("shows an error state for a failed metric without replacing other tiles", async () => {
    ops.getReport.mockRejectedValue(new Error("report unavailable"));

    renderDashboard();

    expect(await screen.findByText("report unavailable")).toBeInTheDocument();
    expect(screen.getByText("Pending DP approvals")).toBeInTheDocument();
    expect(screen.getByText("Not measured — awaiting data-protection count endpoint")).toBeInTheDocument();
  });

  it("shows permission-denied state per tile", async () => {
    access.can.mockReturnValue(false);
    access.canAny.mockReturnValue(false);

    renderDashboard();

    await waitFor(() => expect(screen.getAllByText("Not available for this role").length).toBeGreaterThanOrEqual(5));
    expect(screen.getByText("Pending DP approvals")).toBeInTheDocument();
    expect(screen.getByText("Not measured — awaiting data-protection count endpoint")).toBeInTheDocument();
  });

  it("shows Not measured when active-user data is null or backup rows are absent", async () => {
    ops.getUsageCounts.mockResolvedValue({
      metrics: [{ metricCode: "ACTIVE_USERS", value: null, measured: false }],
    } as never);
    ops.getPlatformBackupStatus.mockResolvedValue({ tenants: [] } as never);

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText("Active users")).toBeInTheDocument();
      expect(screen.getByText("Stale backups")).toBeInTheDocument();
      expect(screen.getAllByText("Not measured").length).toBeGreaterThanOrEqual(2);
    });
  });
});
