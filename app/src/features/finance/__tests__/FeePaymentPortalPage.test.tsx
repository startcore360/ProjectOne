import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FeePaymentPortalPage from "../pages/FeePaymentPortalPage";

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  useAppSelector: vi.fn(),
}));

vi.mock("../../../services/axios", () => ({ default: { get: mocks.get, post: mocks.post } }));
vi.mock("../../../store/hooks", () => ({ useAppSelector: mocks.useAppSelector }));

describe("FeePaymentPortalPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.useAppSelector.mockReturnValue("tenant-123");
    mocks.get.mockImplementation((url: string) => {
      if (url.includes("/me/student-fee-accounts")) {
        return Promise.resolve({ data: [{ accountId: "account-123", studentName: "Test Student", institutionName: "Test College" }] });
      }
      return Promise.resolve({ data: [
        { invoiceId: "installment-1", installmentNumber: 1, dueDate: "2026-12-01", outstandingAmount: 1200, status: "ISSUED" },
        { invoiceId: "installment-2", installmentNumber: 2, dueDate: "2027-01-01", outstandingAmount: 0, status: "PAID" },
      ] });
    });
    mocks.post.mockResolvedValue({ data: {
      paymentId: "payment-123", provider: "RAZORPAY", providerOrderId: "order-123",
      amount: 1200, currency: "INR", keyId: "rzp_test_public",
    } });
  });

  it("loads account invoices and starts checkout only for selected outstanding installments", async () => {
    const user = userEvent.setup();
    render(<FeePaymentPortalPage />);

    await user.type(screen.getByLabelText("Student fee account ID"), "account-123");
    await user.click(screen.getByRole("button", { name: "Refresh" }));
    expect(await screen.findByText("Installment #1")).toBeInTheDocument();
    expect(screen.getByText("Installment #2")).toBeInTheDocument();

    const checkboxes = screen.getAllByRole("checkbox");
    expect(checkboxes[1]).toBeDisabled();
    await user.click(checkboxes[0]);
    await user.click(screen.getByRole("button", { name: /Continue to payment/ }));

    await waitFor(() => expect(mocks.post).toHaveBeenCalledWith(
      "/tenants/tenant-123/payments/checkout",
      expect.objectContaining({
        studentFeeAccountId: "account-123",
        installmentIds: ["installment-1"],
        provider: "RAZORPAY",
        requestKey: expect.any(String),
      }),
    ));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText(/confirmed only after the provider's signed server notification/)).toBeInTheDocument();
  });

  it("does not allow a paid invoice to be selected", async () => {
    const user = userEvent.setup();
    render(<FeePaymentPortalPage />);
    await user.type(screen.getByLabelText("Student fee account ID"), "account-123");
    await waitFor(() => expect(mocks.get).toHaveBeenCalled());
    const paidRow = await screen.findByText("Installment #2");
    expect(paidRow.closest("label")?.querySelector("input")).toBeDisabled();
  });
});
