import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import api from "../../../services/axios";

vi.mock("../../../store/hooks", () => ({
  useAppSelector: (selector: (state: unknown) => unknown) =>
    selector({ auth: { user: { tenantId: "tenant-1" } } }),
}));
vi.mock("../../../services/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

import ApplicantPortalPage from "../pages/ApplicantPortalPage";

describe("ApplicantPortalPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.get).mockImplementation(async (url) => ({
      data: String(url).endsWith("/applications/mine")
        ? [{
            id: "application-1",
            application_number: "ADM-2026-001",
            status: "SUBMITTED",
            verification_status: "PENDING",
            fee_status: "CONFIRMED",
            admission_fee_status: "PENDING",
          }]
        : [],
    }) as never);
  });

  it("restores the applicant's latest application and seat status on return", async () => {
    render(<ApplicantPortalPage />);
    expect(await screen.findByText(/ADM-2026-001/)).toBeInTheDocument();
    expect(screen.getByText(/Verification: PENDING · Application fee: CONFIRMED/)).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith("/tenants/tenant-1/admissions/applications/mine");
  });

  it("fires blur-triggered format validation when an invalid mobile number is entered and blurred", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] } as never);
    render(<ApplicantPortalPage />);

    const phoneInput = screen.getByLabelText("Mobile number (Required)");
    fireEvent.change(phoneInput, { target: { value: "not-a-phone" } });
    fireEvent.blur(phoneInput);

    await waitFor(() => {
      expect(phoneInput).toHaveAttribute("aria-invalid", "true");
      expect(screen.getByRole("alert")).toHaveTextContent("Include the country code, for example +919876543210.");
    });
  });

  it("fires blur-triggered validation for required name and invalid email", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] } as never);
    render(<ApplicantPortalPage />);

    const nameInput = screen.getByLabelText("Full name (Required)");
    fireEvent.focus(nameInput);
    fireEvent.blur(nameInput);

    await waitFor(() => {
      expect(nameInput).toHaveAttribute("aria-invalid", "true");
      expect(document.getElementById("applicant-name-error")).toHaveTextContent("Full name is required.");
    });

    const emailInput = screen.getByLabelText("Email (Required)");
    fireEvent.change(emailInput, { target: { value: "not-an-email" } });
    fireEvent.blur(emailInput);

    await waitFor(() => {
      expect(emailInput).toHaveAttribute("aria-invalid", "true");
      expect(document.getElementById("applicant-email-error")).toHaveTextContent("Enter a valid email address.");
    });
  });
});
