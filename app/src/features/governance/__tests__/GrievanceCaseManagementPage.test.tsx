import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import GrievanceCaseManagementPage from "../pages/GrievanceCaseManagementPage";

const mocks = vi.hoisted(() => ({
  can: vi.fn(() => true),
  submit: vi.fn(),
  list: vi.fn(),
  detail: vi.fn(),
  evidence: vi.fn(),
  actions: vi.fn(),
}));
vi.mock("../../profile/hooks/useTenantAccess", () => ({ useTenantAccess: () => ({ can: mocks.can }) }));
vi.mock("../../../store/hooks", () => ({ useAppSelector: () => "tenant-t9" }));
vi.mock("../api/governanceApi", () => ({
  governanceApi: {
    submitGrievance: mocks.submit,
    grievances: mocks.list,
    grievance: mocks.detail,
    grievanceEvidence: mocks.evidence,
    grievanceActions: mocks.actions,
    assignGrievance: vi.fn(),
    scheduleHearing: vi.fn(),
    recordMinutes: vi.fn(),
    issuePenalty: vi.fn(),
    resolveGrievance: vi.fn(),
    uploadGrievanceEvidence: vi.fn(),
  },
}));

describe("GrievanceCaseManagementPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.can.mockReturnValue(true);
    mocks.actions.mockResolvedValue([]);
    mocks.submit.mockResolvedValue({ caseNumber: "GRV-2026-T9TEST", status: "SUBMITTED", submittedAt: "2026-11-26T10:00:00Z" });
  });

  it("submits a named or anonymous grievance and shows only a receipt", async () => {
    render(<GrievanceCaseManagementPage mode="submit" />);
    fireEvent.change(screen.getByLabelText("Institution ID"), { target: { value: "institution-1" } });
    fireEvent.change(screen.getByLabelText("Details"), { target: { value: "I need to report an incident." } });
    fireEvent.click(screen.getByRole("button", { name: "Submit confidential grievance" }));

    await waitFor(() => expect(mocks.submit).toHaveBeenCalledWith("tenant-t9", {
      institutionId: "institution-1", category: "GENERAL",
      details: "I need to report an incident.", accusedUserId: null, anonymous: false,
    }));
    expect(await screen.findByText("GRV-2026-T9TEST")).toBeInTheDocument();
    expect(screen.getByText(/does not grant access to case details/i)).toBeInTheDocument();
  });

  it("renders only cases returned by the PO-T05-protected API", async () => {
    mocks.list.mockResolvedValue([{
      grievanceCaseId: "case-1", caseNumber: "GRV-2026-001", institutionId: "institution-1",
      category: "HARASSMENT", details: "Confidential case details", accusedUserId: "accused-1",
      anonymousSubmission: false, status: "SUBMITTED", assignedToUserId: null,
      nextHearingAt: null, resolutionNotes: null, submittedAt: "2026-11-26T10:00:00Z",
    }]);
    mocks.detail.mockResolvedValue({
      grievanceCaseId: "case-1", caseNumber: "GRV-2026-001", institutionId: "institution-1",
      category: "HARASSMENT", details: "Confidential case details", accusedUserId: "accused-1",
      anonymousSubmission: false, status: "SUBMITTED", assignedToUserId: null,
      nextHearingAt: null, resolutionNotes: null, submittedAt: "2026-11-26T10:00:00Z",
    });
    mocks.evidence.mockResolvedValue([]);
    render(<GrievanceCaseManagementPage mode="review" />);
    fireEvent.change(screen.getByLabelText("Institution ID"), { target: { value: "institution-1" } });
    fireEvent.click(screen.getByRole("button", { name: "Load cases" }));
    expect(await screen.findByText("GRV-2026-001")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /GRV-2026-001/ }));
    await waitFor(() => expect(mocks.detail).toHaveBeenCalledWith("tenant-t9", "case-1"));
    expect(await screen.findByText("Confidential case details")).toBeInTheDocument();
  });
});
