import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { GuardianManagementPanel } from "./GuardianManagementPanel";
import { guardianService } from "../../api/guardianService";

vi.mock("../../api/guardianService", () => ({
  guardianService: {
    listGuardianLinks: vi.fn().mockResolvedValue([]),
    linkGuardian: vi.fn(),
  },
}));

vi.mock("../../../../store/hooks", () => ({
  useAppSelector: (selector: (state: unknown) => unknown) =>
    selector({ auth: { user: { tenantId: "tenant-1" } } }),
}));

describe("GuardianManagementPanel", () => {
  it("fires blur-triggered format validation when an invalid mobile number is entered and blurred in link modal", async () => {
    render(<GuardianManagementPanel tenantId="tenant-1" studentId="student-1" />);

    await waitFor(() => expect(guardianService.listGuardianLinks).toHaveBeenCalled());

    fireEvent.click(screen.getByRole("button", { name: /\+ Link Guardian/i }));

    const modal = screen.getByRole("dialog");
    const mobileInput = within(modal).getByLabelText("Mobile number (Required)");
    fireEvent.change(mobileInput, { target: { value: "invalid-phone" } });
    fireEvent.blur(mobileInput);

    await waitFor(() => {
      expect(mobileInput).toHaveAttribute("aria-invalid", "true");
      expect(within(modal).getByRole("alert")).toHaveTextContent("Include the country code, for example +919876543210.");
    });
  });
});
