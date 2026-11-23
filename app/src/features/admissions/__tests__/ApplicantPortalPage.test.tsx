import { render, screen } from "@testing-library/react";
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
});
