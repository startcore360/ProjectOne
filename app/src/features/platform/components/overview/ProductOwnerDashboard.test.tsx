import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import authReducer from "../../../../store/slices/authSlice";
import { ProductOwnerDashboard } from "./ProductOwnerDashboard";
import { platformTenantService } from "../../api/platformTenantService";
import { platformOpsService } from "../../api/platformOpsService";
import { platformUserService } from "../../api/platformUserService";

const access = vi.hoisted(() => ({
  can: vi.fn<(action: string) => boolean>(),
  canAny: vi.fn<(actions: string[]) => boolean>(),
}));

vi.mock("../../platformAccess", async (importOriginal) => {
  const original = await importOriginal<typeof import("../../platformAccess")>();
  return { ...original, usePlatformAccess: () => access };
});
vi.mock("../../api/platformTenantService", () => ({
  platformTenantService: { getTenants: vi.fn(), getProvisioning: vi.fn() },
}));
vi.mock("../../api/platformUserService", () => ({
  platformUserService: { getPlatformUsers: vi.fn() },
}));
vi.mock("../../api/platformOpsService", () => ({
  platformOpsService: {
    getReport: vi.fn(),
    getUsageCounts: vi.fn(),
    getPlatformBackupStatus: vi.fn(),
    listSupportTickets: vi.fn(),
    getPendingDataProtectionCount: vi.fn(),
    listOverdueInvoices: vi.fn(),
    listRecentAuditLogs: vi.fn(),
  },
}));

const tenants = vi.mocked(platformTenantService);
const ops = vi.mocked(platformOpsService);
const users = vi.mocked(platformUserService);
const DAY = 86_400_000;

function renderDashboard() {
  const store = configureStore({ reducer: { auth: authReducer } });
  return render(<Provider store={store}><MemoryRouter><ProductOwnerDashboard /></MemoryRouter></Provider>);
}

function tenant(id: string, state: string, extra: Record<string, unknown> = {}) {
  return { tenantId: id, tenantName: `Tenant ${id}`, state, subdomain: id, planName: "Standard", ...extra };
}

function resolveAllEmpty() {
  tenants.getTenants.mockResolvedValue({ data: { items: [], page: 0, size: 200, totalElements: 0, totalPages: 0 } } as never);
  tenants.getProvisioning.mockResolvedValue({ data: { overallStatus: "DONE" } } as never);
  users.getPlatformUsers.mockResolvedValue({ data: [] } as never);
  ops.getReport.mockResolvedValue({ currency: "INR", metrics: { totalCollectionsThisMonth: 0, monthlyRevenue: [] } } as never);
  ops.getUsageCounts.mockResolvedValue({ metrics: [{ metricCode: "ACTIVE_USERS", value: null }] } as never);
  ops.getPlatformBackupStatus.mockResolvedValue({ wiredToRealBackupSystem: false, tenants: [] } as never);
  ops.listSupportTickets.mockResolvedValue([] as never);
  ops.getPendingDataProtectionCount.mockResolvedValue({ count: 0 } as never);
  ops.listOverdueInvoices.mockResolvedValue([] as never);
  ops.listRecentAuditLogs.mockResolvedValue([] as never);
}

describe("ProductOwnerDashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    access.can.mockReturnValue(true);
    // The Product Owner holds everything except the DPO decision actions.
    access.canAny.mockImplementation((actions) => !actions.includes("TENANT_EXPORT_APPROVE"));
    resolveAllEmpty();
  });

  it("says all clear and shows honest empty states when nothing needs attention", async () => {
    renderDashboard();

    expect(screen.getByRole("heading", { level: 1, name: /^Good (morning|afternoon|evening)/ })).toBeInTheDocument();
    expect(await screen.findByText("All clear — nothing needs your action")).toBeInTheDocument();
    expect(screen.getByText("No tenants yet")).toBeInTheDocument();
    expect(screen.getByText("No tenants in trial or setup.")).toBeInTheDocument();
    expect(await screen.findByText("No platform events recorded yet.")).toBeInTheDocument();
    expect(screen.getByText(/Not measured yet: 5xx errors · failed background jobs · active users · tenant backups/)).toBeInTheDocument();
  });

  it("lists only items that need action, most urgent first, with links where this role can act", async () => {
    const soon = new Date(Date.now() + 3 * DAY).toISOString();
    tenants.getTenants.mockResolvedValue({ data: { items: [
      tenant("a", "ACTIVE"), tenant("b", "CREATED"), tenant("c", "TRIAL", { trialEndsAt: soon }),
    ] } } as never);
    tenants.getProvisioning.mockResolvedValue({ data: { overallStatus: "FAILED" } } as never);
    ops.getPendingDataProtectionCount.mockResolvedValue({ count: 17 } as never);

    renderDashboard();

    expect(await screen.findByText("2 things need your action")).toBeInTheDocument();
    const list = screen.getByRole("list", { name: "Needs your attention" });
    const items = within(list).getAllByRole("listitem").map((item) => item.textContent ?? "");
    expect(items[0]).toContain("1 tenant setup failed");
    expect(items.some((text) => text.includes("1 trial ends in the next 7 days"))).toBe(true);
    expect(items.some((text) => text.includes("data-protection"))).toBe(false);
    // The DPO decides these, so they wait on someone else and carry no action for the Product Owner.
    const waiting = within(screen.getByRole("list", { name: "Waiting on others" })).getByRole("listitem");
    expect(waiting).toHaveTextContent("17 data-protection requests are waiting for the Data Protection Officer");
    expect(waiting).toHaveTextContent("Only the Data Protection Officer can approve or reject these.");
    expect(within(waiting).queryByRole("link")).toBeNull();
    expect(screen.getByRole("link", { name: "Retry setup" })).toHaveAttribute("href", "/platform/tenants");
    expect(screen.queryByRole("link", { name: /Review/ })).toBeNull();
    // Provisioning is only checked for the tenant whose setup never finished.
    expect(tenants.getProvisioning).toHaveBeenCalledTimes(1);
    expect(tenants.getProvisioning).toHaveBeenCalledWith("b");
  });

  it("keeps the rest of the page working when one block fails, and retries just that block", async () => {
    ops.getReport.mockRejectedValueOnce(new Error("report down"));
    tenants.getTenants.mockResolvedValue({ data: { items: [tenant("a", "ACTIVE"), tenant("b", "TRIAL")] } } as never);

    renderDashboard();

    const errors = await screen.findAllByText("Could not load revenue.");
    expect(errors).toHaveLength(1);
    expect(screen.getByRole("img", { name: "Active 1, Trial 1" })).toBeInTheDocument();

    fireEvent.click(within(errors[0].parentElement as HTMLElement).getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(ops.getReport).toHaveBeenCalledTimes(2));
    expect(await screen.findByText("No collections last month")).toBeInTheDocument();
  });

  it("does not call endpoints the role may not use and says so per block", async () => {
    access.can.mockReturnValue(false);
    access.canAny.mockReturnValue(false);

    renderDashboard();

    expect((await screen.findAllByText("Not available for your role")).length).toBeGreaterThanOrEqual(5);
    expect(tenants.getTenants).not.toHaveBeenCalled();
    expect(ops.getReport).not.toHaveBeenCalled();
    expect(ops.listRecentAuditLogs).not.toHaveBeenCalled();
  });

  it("shows readable activity with names and filters to denied attempts", async () => {
    users.getPlatformUsers.mockResolvedValue({ data: [
      { platformUserId: "u1", fullName: "Priya Nair", platformRole: "PRODUCT_OWNER", status: "ACTIVE" },
    ] } as never);
    ops.listRecentAuditLogs.mockResolvedValue([
      { auditOutboxId: "e1", occurredAt: new Date().toISOString(), actorKind: "PLATFORM_USER", actorUserId: "u1", operation: "UPDATE", eventType: "TENANT_SUSPENDED", outcome: "SUCCESS" },
    ] as never);

    renderDashboard();

    expect(await screen.findByText("Tenant suspended")).toBeInTheDocument();
    expect(await screen.findByText((_, element) => element?.tagName === "SMALL" && (element.textContent ?? "").startsWith("Priya Nair · "))).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Audit log →/ })).toHaveAttribute("href", "/platform/audit");

    fireEvent.click(screen.getByRole("button", { name: "Denied only" }));
    await waitFor(() => expect(ops.listRecentAuditLogs).toHaveBeenCalledWith(6, "DENIED"));
  });

  it("shows collections with month-on-month change and an accessible table", async () => {
    ops.getReport.mockResolvedValue({ currency: "INR", metrics: {
      totalCollectionsThisMonth: 640400,
      monthlyRevenue: [{ month: "2026-10", revenue: 500000 }, { month: "2026-11", revenue: 640400 }],
    } } as never);

    renderDashboard();

    const card = await screen.findByRole("region", { name: "Revenue this month" });
    expect((await within(card).findAllByText("INR 6,40,400"))[0]).toHaveClass("kpi__value");
    expect(screen.getByText("▲ 28% vs last month")).toBeInTheDocument();
    const table = screen.getByRole("table", { name: "Collections per month" });
    expect(within(table).getByText("INR 5,00,000")).toBeInTheDocument();
  });
});
