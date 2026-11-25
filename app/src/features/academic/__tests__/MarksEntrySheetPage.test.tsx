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

import MarksEntrySheetPage from "../pages/MarksEntrySheetPage";

describe("MarksEntrySheetPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("renders marks capture and two moderation tiers", () => {
    render(<MarksEntrySheetPage />);
    expect(screen.getByRole("heading", { name: "Marks entry & moderation" })).toBeInTheDocument();
    expect(screen.getByLabelText(/Exam schedule ID/)).toBeInTheDocument();
    expect(screen.getByLabelText(/External examiner user ID/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Course teacher: approve/ })).not.toBeInTheDocument();
  });

  it("captures marks and exposes the independent moderation workflow", async () => {
    vi.mocked(api.post).mockResolvedValueOnce({
      data: {
        exam_mark_id: "mark-1", student_profile_id: "student-1",
        marks_obtained: 78, is_absent: false, status: "ENTERED",
      },
    } as never);

    render(<MarksEntrySheetPage />);
    fireEvent.change(screen.getByLabelText(/Exam schedule ID/), { target: { value: "schedule-1" } });
    fireEvent.change(screen.getByLabelText(/Student profile ID/), { target: { value: "student-1" } });
    fireEvent.change(screen.getByLabelText(/Marks obtained/), { target: { value: "78" } });
    fireEvent.change(screen.getByLabelText(/External examiner user ID/), { target: { value: "examiner-1" } });
    fireEvent.click(screen.getByRole("button", { name: "Save marks" }));

    await waitFor(() => expect(screen.getByText("Current status:")).toBeInTheDocument());
    expect(await screen.findByText("Mark record: mark-1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Course teacher: approve first tier" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Submit external moderation" })).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith("/api/tenants/tenant-1/academic/exams/marks", {
      examScheduleId: "schedule-1", studentProfileId: "student-1",
      marksObtained: 78, isAbsent: false, externalExaminerUserId: "examiner-1",
    });
  });

  it("does not submit numeric marks for an absent student", async () => {
    vi.mocked(api.post).mockResolvedValueOnce({
      data: { exam_mark_id: "mark-2", status: "ENTERED", is_absent: true, marks_obtained: null },
    } as never);
    render(<MarksEntrySheetPage />);
    fireEvent.change(screen.getByLabelText(/Exam schedule ID/), { target: { value: "schedule-1" } });
    fireEvent.change(screen.getByLabelText(/Student profile ID/), { target: { value: "student-1" } });
    fireEvent.change(screen.getByLabelText(/External examiner user ID/), { target: { value: "examiner-1" } });
    fireEvent.click(screen.getByLabelText(/Student absent/));
    fireEvent.click(screen.getByRole("button", { name: "Save marks" }));
    await waitFor(() => expect(api.post).toHaveBeenCalled());
    expect(api.post).toHaveBeenCalledWith("/api/tenants/tenant-1/academic/exams/marks", {
      examScheduleId: "schedule-1", studentProfileId: "student-1",
      marksObtained: null, isAbsent: true, externalExaminerUserId: "examiner-1",
    });
  });
});
