import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import StudentFeeInvoicesPage from "../pages/StudentFeeInvoicesPage";

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  useAppSelector: vi.fn(),
}));

vi.mock("../../../services/axios", () => ({ default: { get: mocks.get } }));
vi.mock("../../../store/hooks", () => ({ useAppSelector: mocks.useAppSelector }));

describe("StudentFeeInvoicesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.useAppSelector.mockReturnValue("tenant-123");
    mocks.get.mockImplementation((url: string) => {
      if (url.includes("/me/student-fee-accounts")) {
        return Promise.resolve({ data: [{ accountId: "account-123", studentName: "Test Student", institutionName: "Test College" }] });
      }
      return Promise.resolve({ data: [{
        invoiceId: "invoice-1", installmentNumber: 1, dueDate: "2026-12-01",
        baseAmount: 1200, concessionAmount: 100, scholarshipAmount: 0,
        lateFeeAmount: 0, paidAmount: 0, netDue: 1100, outstandingAmount: 1100, status: "ISSUED",
      }] });
    });
  });

  it("loads and displays the server-calculated invoice balances", async () => {
    const user = userEvent.setup();
    render(<StudentFeeInvoicesPage />);

    await user.type(screen.getByLabelText("Student fee account ID"), "account-123");
    await user.click(screen.getByRole("button", { name: "Load invoices" }));

    expect(await screen.findByText("#1")).toBeInTheDocument();
    expect(screen.getByText(/₹1,100/)).toBeInTheDocument();
    expect(mocks.get).toHaveBeenCalledWith("/tenants/tenant-123/student-fee-accounts/account-123/invoices");
  });

  it("shows a recoverable error without presenting a false empty state", async () => {
    const user = userEvent.setup();
    mocks.get.mockImplementation((url: string) => {
      if (url.includes("/me/student-fee-accounts")) return Promise.resolve({ data: [] });
      return Promise.reject(new Error("forbidden"));
    });
    render(<StudentFeeInvoicesPage />);

    await user.type(screen.getByLabelText("Student fee account ID"), "account-outside-scope");
    await user.click(screen.getByRole("button", { name: "Load invoices" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Unable to retrieve invoices");
    expect(screen.queryByText("Enter a fee account ID to view its invoices.")).not.toBeInTheDocument();
  });
});
