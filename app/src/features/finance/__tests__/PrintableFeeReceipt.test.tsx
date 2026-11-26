import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import PrintableFeeReceipt, { type FeeReceiptDetails } from "../components/PrintableFeeReceipt";

describe("PrintableFeeReceipt", () => {
  it("renders server-issued receipt details and invokes print", async () => {
    const user = userEvent.setup();
    const print = vi.spyOn(window, "print").mockImplementation(() => undefined);
    const receipt: FeeReceiptDetails = {
      receiptLabel: "EDU/2026/0001",
      paymentId: "payment-123",
      studentName: "Test Student",
      institutionName: "Test College",
      paidAt: "2026-11-26T10:00:00Z",
      paymentMethod: "RAZORPAY",
      amount: 1200,
      currency: "INR",
      allocations: [{ installmentNumber: 1, dueDate: "2026-12-01", amount: 1200 }],
    };

    render(<PrintableFeeReceipt receipt={receipt} />);
    expect(screen.getByText("EDU/2026/0001")).toBeInTheDocument();
    expect(screen.getByText("Test Student")).toBeInTheDocument();
    expect(screen.getByText("Test College")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Print / Save as PDF" }));
    expect(print).toHaveBeenCalledTimes(1);
  });
});
