import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { platformOpsService } from "../../api/platformOpsService";
import { platformTenantService } from "../../api/platformTenantService";
import { ReportsView } from "./ReportsView";

vi.mock("../../api/platformOpsService", () => ({
  platformOpsService: {
    getReport: vi.fn(),
    listInvoices: vi.fn(),
    listSupportTickets: vi.fn(),
  },
}));

vi.mock("../../api/platformTenantService", () => ({
  platformTenantService: {
    getTenants: vi.fn(),
  },
}));

vi.mock("../../platformAccess", () => ({
  usePlatformAccess: () => ({
    role: "PLATFORM_ADMIN",
    can: () => true,
  }),
}));

const mockReport = {
  reportCode: "PLATFORM_OVERVIEW",
  generatedAt: "2026-11-24T10:00:00Z",
  timeframe: "CURRENT_MONTH",
  metrics: {
    totalTenants: 10,
    activeTenants: 8,
    mrr: 50000,
  },
  dimensions: {},
};

describe("ReportsView parallel data loading", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads report and datasets in parallel with Promise.allSettled", async () => {
    vi.mocked(platformOpsService.getReport).mockResolvedValueOnce(mockReport as never);
    vi.mocked(platformTenantService.getTenants).mockResolvedValueOnce({
      data: {
        items: [
          {
            tenantId: "t-1",
            tenantName: "Apex University",
            tenantType: "UNIVERSITY_GROUP",
            state: "ACTIVE",
          },
        ],
      },
    } as never);
    vi.mocked(platformOpsService.listInvoices).mockResolvedValueOnce([]);
    vi.mocked(platformOpsService.listSupportTickets).mockResolvedValueOnce([]);

    render(<ReportsView />);

    await waitFor(() => {
      expect(screen.getByText("Active Tenants")).toBeInTheDocument();
      expect(screen.getByText("Apex University")).toBeInTheDocument();
    });

    expect(platformOpsService.getReport).toHaveBeenCalledWith("PLATFORM_OVERVIEW");
    expect(platformTenantService.getTenants).toHaveBeenCalledWith(0, 100);
    expect(platformOpsService.listInvoices).toHaveBeenCalled();
    expect(platformOpsService.listSupportTickets).toHaveBeenCalled();
  });

  it("renders report and shows visible error indicator when tenant list endpoint fails", async () => {
    vi.mocked(platformOpsService.getReport).mockResolvedValueOnce(mockReport as never);
    vi.mocked(platformTenantService.getTenants).mockRejectedValueOnce(
      new Error("Network timeout loading tenants")
    );
    vi.mocked(platformOpsService.listInvoices).mockResolvedValueOnce([]);
    vi.mocked(platformOpsService.listSupportTickets).mockResolvedValueOnce([]);

    render(<ReportsView />);

    // Report metrics still render correctly
    await waitFor(() => {
      expect(screen.getByText("Active Tenants")).toBeInTheDocument();
    });

    // Visible error indicator is displayed for failed tenant dataset, not silent
    expect(screen.getByText("Network timeout loading tenants")).toBeInTheDocument();
  });
});
