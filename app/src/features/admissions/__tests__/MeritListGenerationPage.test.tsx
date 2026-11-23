import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import api from "../../../services/axios";

vi.mock("../../../store/hooks", () => ({
  useAppSelector: (selector: (state: unknown) => unknown) =>
    selector({ auth: { user: { tenantId: "tenant-1" } } }),
}));
vi.mock("../../../services/axios", () => ({
  default: { get: vi.fn().mockResolvedValue({ data: [] }), post: vi.fn() },
}));

import MeritListGenerationPage from "../pages/MeritListGenerationPage";

describe("MeritListGenerationPage", () => {
  it("renders merit generation controls and an empty state", () => {
    render(<MeritListGenerationPage />);
    expect(screen.getByRole("heading", { name: "Merit lists and seat allocation" })).toBeInTheDocument();
    expect(screen.getByLabelText(/Admission cycle ID/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Generate merit list" })).toBeDisabled();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.get).mockResolvedValue({ data: [] } as never);
  });

  it("loads seat offers and converts an accepted, fee-confirmed applicant", async () => {
    vi.mocked(api.get).mockImplementation(async (url) => ({
      data: String(url).includes("seat-allotments")
        ? [{
            id: "seat-1",
            application_id: "app-1",
            application_number: "ADM-001",
            applicant_name: "Asha Rao",
            quota_code: "GENERAL",
            status: "ACCEPTED",
            admission_fee_status: "CONFIRMED",
            student_profile_id: null,
          }]
        : [{ id: "merit-1", status: "APPROVED", round_number: 1, generated_at: "2026-11-23T00:00:00Z" }],
    }) as never);
    vi.mocked(api.post).mockResolvedValue({ data: { studentCode: "STU-2026-001" } } as never);

    render(<MeritListGenerationPage />);
    fireEvent.change(screen.getByLabelText(/Admission cycle ID/), { target: { value: "cycle-1" } });
    fireEvent.click(screen.getByRole("button", { name: "Load lists and seats" }));

    expect(await screen.findByText("ADM-001 · Asha Rao")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Convert to student" }));
    await waitFor(() => expect(screen.getByText("Student record created: STU-2026-001")).toBeInTheDocument());
    expect(api.post).toHaveBeenCalledWith(
      "/tenants/tenant-1/admissions/applications/app-1/conversion",
    );
  });
});
