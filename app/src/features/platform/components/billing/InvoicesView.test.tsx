/** @vitest-environment jsdom */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InvoicesView } from "./InvoicesView";
import { platformOpsService } from "../../api/platformOpsService";
import { platformTenantService } from "../../api/platformTenantService";
import { getLocalDateString, getLocalMonthStart } from "../../../../utils/dateUtils";

vi.mock("../../platformAccess", () => ({
  usePlatformAccess: () => ({
    can: () => true,
    role: "PRODUCT_OWNER",
  }),
}));

vi.mock("../../api/platformOpsService", () => ({
  platformOpsService: {
    listInvoices: vi.fn(),
    createInvoice: vi.fn(),
    approveInvoice: vi.fn(),
    sendInvoice: vi.fn(),
    recordPayment: vi.fn(),
    flagOverdue: vi.fn(),
    applyDiscount: vi.fn(),
    listInvoiceLines: vi.fn(),
    addInvoiceLine: vi.fn(),
    removeInvoiceLine: vi.fn(),
    createBillingCycle: vi.fn(),
  },
}));

vi.mock("../../api/platformTenantService", () => ({
  platformTenantService: {
    getTenants: vi.fn(),
  },
}));

describe("InvoicesView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockTenants = [
    { tenantId: "tenant-1", tenantName: "Springfield Academy", subdomain: "springfield" },
    { tenantId: "tenant-2", tenantName: "Shelbyville High", subdomain: "shelbyville" },
  ];

  it("initializes modal dates with local date helpers and shows error inside modal on create failure", async () => {
    const user = userEvent.setup();
    vi.mocked(platformOpsService.listInvoices).mockResolvedValueOnce([]);
    vi.mocked(platformTenantService.getTenants).mockResolvedValueOnce({
      data: { items: mockTenants, total: 2 },
    } as never);
    vi.mocked(platformOpsService.createInvoice).mockRejectedValueOnce(
      new Error("Overlapping billing cycle for tenant")
    );

    render(<InvoicesView />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Create Invoice" })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Create Invoice" }));

    // Verify modal is open and has accessible SelectField for tenant
    expect(screen.getByRole("heading", { name: "Create Tenant Invoice" })).toBeInTheDocument();
    expect(screen.getByLabelText(/Tenant Institution/i)).toBeInTheDocument();

    const startInput = screen.getByLabelText(/Period Start/i) as HTMLInputElement;
    const endInput = screen.getByLabelText(/Period End/i) as HTMLInputElement;

    expect(startInput.value).toBe(getLocalMonthStart());
    expect(endInput.value).toBe(getLocalDateString());

    await user.click(screen.getByRole("button", { name: "Save Draft Invoice" }));

    // Verify in-modal error
    await waitFor(() => {
      expect(screen.getByText("Overlapping billing cycle for tenant")).toBeInTheDocument();
    });

    // Modal stays open and preserves input
    expect(screen.getByRole("heading", { name: "Create Tenant Invoice" })).toBeInTheDocument();
    expect(startInput.value).toBe(getLocalMonthStart());
  });
});
