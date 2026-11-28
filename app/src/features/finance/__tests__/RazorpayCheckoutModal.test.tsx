import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RazorpayCheckoutModal, { type CheckoutSession } from "../components/RazorpayCheckoutModal";

describe("RazorpayCheckoutModal", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("opens a single provider checkout and disables repeat opening", async () => {
    const user = userEvent.setup();
    const open = vi.fn();
    const RazorpayMock = vi.fn(function RazorpayMockImplementation() { return { open }; });
    window.Razorpay = RazorpayMock as unknown as typeof window.Razorpay;
    const session: CheckoutSession = {
      paymentId: "payment-123", provider: "RAZORPAY", providerOrderId: "order-123",
      amount: 1200, currency: "INR", keyId: "rzp_test_public",
    };

    render(<RazorpayCheckoutModal session={session} onClose={vi.fn()} onSubmitted={vi.fn()} />);
    const payButton = screen.getByRole("button", { name: "Pay securely" });
    await user.click(payButton);

    expect(open).toHaveBeenCalledTimes(1);
    expect(payButton).toBeDisabled();
  });
});
