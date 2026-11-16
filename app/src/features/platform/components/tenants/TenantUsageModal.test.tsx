import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { platformTenantService } from "../../api/platformTenantService";
import { TenantUsageModal } from "./TenantUsageModal";
import type { Tenant } from "../common/types";

vi.mock("../../api/platformTenantService", () => ({
  platformTenantService: {
    getTenantUsage: vi.fn(),
  },
}));

const service = vi.mocked(platformTenantService);
const tenant = { id: "tenant-1", name: "Northfield School" } as Tenant;

beforeEach(() => {
  vi.resetAllMocks();
});

describe("TenantUsageModal", () => {
  it("renders the monthly metric rows returned by the platform usage API", async () => {
    service.getTenantUsage.mockResolvedValue({
      data: [{
        tenantUsageMonthlyId: "usage-1",
        tenantId: "tenant-1",
        usageMonth: "2026-10-01",
        metricCode: "API_CALLS",
        quantity: 1250,
        computedAt: "2026-11-01T10:00:00Z",
        isFinal: true,
      }],
    } as never);

    render(<TenantUsageModal isOpen onClose={vi.fn()} tenant={tenant} />);

    await waitFor(() => expect(screen.getByText("API_CALLS")).toBeInTheDocument());
    expect(screen.getByText("2026-10")).toBeInTheDocument();
    expect(screen.getByText("1,250")).toBeInTheDocument();
    expect(screen.getByText("Yes")).toBeInTheDocument();
  });

  it("renders an empty state when no monthly usage rows exist", async () => {
    service.getTenantUsage.mockResolvedValue({ data: [] } as never);
    render(<TenantUsageModal isOpen onClose={vi.fn()} tenant={tenant} />);
    await waitFor(() => expect(service.getTenantUsage).toHaveBeenCalledWith("tenant-1"));
    expect(screen.getByText("Monthly Historical Records")).toBeInTheDocument();
    expect(screen.queryByText("API_CALLS")).not.toBeInTheDocument();
  });
});
