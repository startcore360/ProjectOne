import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import authReducer from "../../../../store/slices/authSlice";
import { SupportManagerDashboard } from "./SupportManagerDashboard";
import { platformOpsService } from "../../api/platformOpsService";

const access = vi.hoisted(() => ({ can: vi.fn<(action: string) => boolean>() }));
vi.mock("../../platformAccess", () => ({ usePlatformAccess: () => access }));
vi.mock("../../api/platformOpsService", () => ({
  platformOpsService: { listSupportTickets: vi.fn(), getActiveSupportSessionCount: vi.fn() },
}));

const ops = vi.mocked(platformOpsService);
function renderDashboard() {
  const store = configureStore({ reducer: { auth: authReducer } });
  return render(<Provider store={store}><SupportManagerDashboard /></Provider>);
}
function resolveSources() {
  ops.listSupportTickets.mockResolvedValue([]);
  ops.getActiveSupportSessionCount.mockResolvedValue({ count: 0 } as never);
}

describe("SupportManagerDashboard tile states", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    access.can.mockReturnValue(true);
    resolveSources();
  });

  it("shows loading state independently for ticket and session sources", () => {
    ops.listSupportTickets.mockReturnValue(new Promise(() => {}) as never);
    ops.getActiveSupportSessionCount.mockReturnValue(new Promise(() => {}) as never);
    renderDashboard();
    expect(screen.getByText("Loading open tickets by priority...")).toBeInTheDocument();
    expect(screen.getByText("Loading escalated tickets...")).toBeInTheDocument();
    expect(screen.getByText("Loading active support sessions...")).toBeInTheDocument();
  });

  it("summarizes open tickets by priority, counts escalations and reads real session count", async () => {
    ops.listSupportTickets.mockResolvedValue([
      { status: "NEW", priority: "HIGH" },
      { status: "ASSIGNED", priority: "HIGH" },
      { status: "ESCALATED", priority: "CRITICAL", escalatedAt: "2026-11-01T00:00:00Z" },
      { status: "RESOLVED", priority: "LOW" },
    ] as never);
    ops.getActiveSupportSessionCount.mockResolvedValue({ count: 4 } as never);
    renderDashboard();
    expect(await screen.findByText("CRITICAL: 1 · HIGH: 2")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(ops.listSupportTickets).toHaveBeenCalledTimes(1);
    expect(ops.getActiveSupportSessionCount).toHaveBeenCalledTimes(1);
  });

  it("shows real zero values for empty sources", async () => {
    renderDashboard();
    await waitFor(() => expect(screen.getAllByText("0", { exact: true })).toHaveLength(3));
  });

  it("shows source errors independently", async () => {
    ops.listSupportTickets.mockRejectedValue(new Error("ticket source unavailable"));
    ops.getActiveSupportSessionCount.mockRejectedValue(new Error("session source unavailable"));
    renderDashboard();
    expect(await screen.findAllByText("ticket source unavailable")).toHaveLength(2);
    expect(screen.getByText("session source unavailable")).toBeInTheDocument();
  });

  it("applies ticket and session permissions independently", async () => {
    access.can.mockImplementation(action => action === "SUPPORT_ACCESS_USE");
    renderDashboard();
    await waitFor(() => expect(screen.getAllByText("Not available for this role")).toHaveLength(2));
    expect(screen.getByText("0")).toBeInTheDocument();
    expect(ops.listSupportTickets).not.toHaveBeenCalled();
    expect(ops.getActiveSupportSessionCount).toHaveBeenCalledTimes(1);
  });
});
