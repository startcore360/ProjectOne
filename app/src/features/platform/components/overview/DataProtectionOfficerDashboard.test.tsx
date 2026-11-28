import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { MemoryRouter } from "react-router-dom";
import authReducer from "../../../../store/slices/authSlice";
import { DataProtectionOfficerDashboard } from "./DataProtectionOfficerDashboard";
import { platformDataProtectionService } from "../../api/platformDataProtectionService";

vi.mock("../../api/platformDataProtectionService", () => ({
  platformDataProtectionService: {
    listExports: vi.fn(),
    listDeletions: vi.fn(),
    listRetentionChanges: vi.fn(),
  },
}));

const service = vi.mocked(platformDataProtectionService);

function renderDashboard() {
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: {
        user: {
          id: "dpo-1",
          displayName: "Manoj DPO",
          tenantId: null,
          userType: null,
          platformRole: "DATA_PROTECTION_OFFICER" as const,
          roleCode: null,
          roleName: null,
          photoUrl: null,
          mustChangePassword: false,
        },
        isAuthenticated: true,
        loading: false,
      },
    },
  });
  return render(
    <Provider store={store}>
      <MemoryRouter>
        <DataProtectionOfficerDashboard />
      </MemoryRouter>
    </Provider>
  );
}

describe("DataProtectionOfficerDashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders empty state cleanly when no requests are pending", async () => {
    service.listExports.mockResolvedValue({ data: [] } as never);
    service.listDeletions.mockResolvedValue({ data: [] } as never);
    service.listRetentionChanges.mockResolvedValue({ data: [] } as never);

    renderDashboard();

    expect(await screen.findByText(/Manoj DPO/)).toBeInTheDocument();
    expect(screen.getByText("All clear — no privacy requests waiting for action")).toBeInTheDocument();
    expect(screen.getByText("Pending Approvals")).toBeInTheDocument();
  });

  it("renders pending requests with counts, attention banner, and work list", async () => {
    service.listExports.mockResolvedValue({
      data: [{ exportRequestId: "exp-1", tenantName: "Oakridge High", status: "REQUESTED", createdAt: "2026-11-20T10:00:00Z" }],
    } as never);
    service.listDeletions.mockResolvedValue({
      data: [{ deletionRequestId: "del-1", tenantName: "St. Jude School", status: "REQUESTED", createdAt: "2026-11-20T11:00:00Z" }],
    } as never);
    service.listRetentionChanges.mockResolvedValue({
      data: [],
    } as never);

    renderDashboard();

    expect(await screen.findByText(/2 requests need your authorization/)).toBeInTheDocument();
    expect(screen.getByText("Oakridge High")).toBeInTheDocument();
    expect(screen.getByText("St. Jude School")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open approval queue/ })).toBeInTheDocument();
  });
});
