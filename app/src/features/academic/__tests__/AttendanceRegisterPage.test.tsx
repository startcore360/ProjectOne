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

import AttendanceRegisterPage from "../pages/AttendanceRegisterPage";

describe("AttendanceRegisterPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("loads an enrolled class roster and exposes only canonical attendance statuses", async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [
      { enrollment_id: "enrol-1", student_profile_id: "student-1", student_code: "S001", full_name: "A Student" },
    ] } as never);
    render(<AttendanceRegisterPage />);
    fireEvent.change(screen.getByLabelText("Institution ID"), { target: { value: "institution-1" } });
    fireEvent.change(screen.getByLabelText("Section ID"), { target: { value: "section-1" } });
    await waitFor(() => expect(screen.getByText("A Student")).toBeInTheDocument());
    expect(screen.getByText("Present")).toBeInTheDocument();
    expect(screen.getByText("Absent")).toBeInTheDocument();
    expect(screen.getByText("Approved leave")).toBeInTheDocument();
    expect(screen.queryByText(/Late|Excused/)).not.toBeInTheDocument();
  });

  it("submits the daily attendance sheet with canonical statuses", async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [
      { enrollment_id: "enrol-1", student_profile_id: "student-1", student_code: "S001", full_name: "A Student" },
    ] } as never);
    vi.mocked(api.post).mockResolvedValueOnce({ data: { attendance_session_id: "session-1" } } as never);
    render(<AttendanceRegisterPage />);
    fireEvent.change(screen.getByLabelText("Institution ID"), { target: { value: "institution-1" } });
    fireEvent.change(screen.getByLabelText("Academic session ID"), { target: { value: "academic-1" } });
    fireEvent.change(screen.getByLabelText("Section ID"), { target: { value: "section-1" } });
    await waitFor(() => expect(screen.getByText("A Student")).toBeInTheDocument());
    fireEvent.click(screen.getByLabelText("A Student ABSENT"));
    fireEvent.click(screen.getByRole("button", { name: "Save attendance" }));
    await waitFor(() => expect(api.post).toHaveBeenCalled());
    expect(api.post).toHaveBeenCalledWith("/api/tenants/tenant-1/attendance/sessions", expect.objectContaining({
      institutionId: "institution-1", academicSessionId: "academic-1", sectionId: "section-1",
      sessionType: "DAILY", notHeld: false,
      entries: [{ enrollmentId: "enrol-1", studentProfileId: "student-1", status: "ABSENT" }],
    }));
    expect(await screen.findByText(/Attendance session saved as a draft/)).toBeInTheDocument();
  });
});