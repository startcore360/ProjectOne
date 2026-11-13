import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import type { ReactElement } from "react";
import api from "../../../services/axios";
import authReducer from "../../../store/slices/authSlice";
import tenantPermissionsReducer, { setTenantPermissions } from "../../../store/slices/tenantPermissionsSlice";
import {
  AcademicGovernancePage,
  AcademicStructurePage,
  CalendarEventsPage,
  ClassTeacherAssignmentPage,
  DelegationsApprovalsPage,
  HodAssignmentPage,
  MentorAssignmentPage,
  StudentLoginSwitchPage,
  TermLockPage,
  WeeklyOffRulesPage,
} from "./InstitutionSetupPages";

vi.mock("../../../services/axios", () => ({
  default: { put: vi.fn(), get: vi.fn(), post: vi.fn() },
}));

const TENANT_ID = "11111111-1111-1111-1111-111111111111";
const DEPT_ID = "22222222-2222-2222-2222-222222222222";
const STAFF_ASSIGNMENT_ID = "33333333-3333-3333-3333-333333333333";
const INST_ID = "55555555-5555-5555-5555-555555555555";
const SECTION_ID = "66666666-6666-6666-6666-666666666666";

function renderWithTenant(
  element: ReactElement,
  tenantId: string | null = TENANT_ID,
  roleCode: string | null = null,
) {
  const store = configureStore({
    reducer: { auth: authReducer, tenantPermissions: tenantPermissionsReducer },
    preloadedState: {
      auth: {
        user: tenantId
          ? {
              id: "u-1",
              displayName: "Test Admin",
              tenantId,
              userType: "STAFF" as const,
              platformRole: null,
              roleCode,
              roleName: roleCode === "SCHOOL_ADMIN" ? "School Admin" : null,
              photoUrl: null,
              mustChangePassword: false,
            }
          : null,
        isAuthenticated: !!tenantId,
        loading: false,
      },
    },
  });
  store.dispatch(setTenantPermissions({
    tenantId: tenantId ?? TENANT_ID,
    userAccountId: "u-1",
    activeRoleCode: roleCode ?? "UNIVERSITY_ADMIN",
    activeRoleName: roleCode === "SCHOOL_ADMIN" ? "School Admin" : "University Admin",
    scopeType: roleCode === "HEAD_OF_DEPARTMENT" ? "DEPARTMENT" : "INSTITUTION",
    institutionId: INST_ID,
    departmentId: roleCode === "HEAD_OF_DEPARTMENT" ? DEPT_ID : null,
    allowedActions: [
      "STRUCTURE_MANAGE", "HOD_ASSIGN", "MENTOR_ASSIGN", "CLASS_TEACHER_ASSIGN",
      "STUDENT_LOGIN_SWITCH", "CALENDAR_LOCAL_SET", "WEEKLY_OFF_SET", "TERM_LOCK",
      "TERM_UNLOCK", "APPROVAL_DELEGATE", "APPROVAL_BULK", "PROGRAM_RULES_SET",
      "GRADING_SCHEME_SET", "SUBJECT_ALLOCATE", "PROMOTION_RUN", "PROMOTION_APPROVE",
    ],
  }));
  return render(<Provider store={store}>{element}</Provider>);
}

describe("Institution setup action screens", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.get as ReturnType<typeof vi.fn>).mockReset();
    (api.post as ReturnType<typeof vi.fn>).mockReset();
    (api.put as ReturnType<typeof vi.fn>).mockReset();
  });

  it("shows the permission-denied state when there is no tenant identity", () => {
    renderWithTenant(<HodAssignmentPage />, null);
    expect(screen.getByText(/requires a signed-in institution identity/i)).toBeInTheDocument();
  });

  it("keeps the HOD action disabled until a department and person are selected", () => {
    renderWithTenant(<HodAssignmentPage />);
    expect(screen.getByRole("button", { name: /assign hod/i })).toBeDisabled();
    expect(screen.getByLabelText(/Department/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/New HOD/i)).toBeInTheDocument();
    expect(api.put).not.toHaveBeenCalled();
  });

  it("submits HOD_ASSIGN with a picked department and staff assignment", async () => {
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith("/departments")) return Promise.resolve({ data: [{ departmentId: DEPT_ID, departmentName: "Computer Science" }] });
      if (url.endsWith("/staff")) return Promise.resolve({ data: { items: [{
        staffProfileId: "77777777-7777-7777-7777-777777777777", staffAssignmentId: STAFF_ASSIGNMENT_ID,
        staffCode: "ST-01", fullName: "Asha Rao", designation: "Professor", departmentId: DEPT_ID, departmentName: "Computer Science",
      }] } });
      return Promise.resolve({ data: [] });
    });
    (api.put as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: { departmentId: DEPT_ID, hodStaffAssignmentId: STAFF_ASSIGNMENT_ID },
    });
    renderWithTenant(<HodAssignmentPage />);
    await screen.findByRole("option", { name: "Computer Science" });
    fireEvent.change(screen.getByLabelText(/Department/i), { target: { value: DEPT_ID } });
    await screen.findByRole("option", { name: /Asha Rao/ });
    fireEvent.change(screen.getByLabelText(/New HOD/i), { target: { value: STAFF_ASSIGNMENT_ID } });
    fireEvent.click(screen.getByRole("button", { name: /assign hod/i }));

    await waitFor(() => expect(api.put).toHaveBeenCalledWith(`/tenants/${TENANT_ID}/departments/${DEPT_ID}/hod`, {
      staffAssignmentId: STAFF_ASSIGNMENT_ID,
    }));
    expect(await screen.findByText(/HOD assignment recorded/i)).toBeInTheDocument();
  });

  it("surfaces a 403 from MENTOR_ASSIGN after selecting a college section and mentor", async () => {
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith("/sections")) return Promise.resolve({ data: [{
        sectionId: SECTION_ID, sectionName: "CSE-A", institutionId: INST_ID, academicSessionId: "88888888-8888-8888-8888-888888888888",
        semesterId: "99999999-9999-9999-9999-999999999999", classId: null, status: "ACTIVE",
      }] });
      if (url.endsWith("/staff")) return Promise.resolve({ data: { items: [{
        staffProfileId: "77777777-7777-7777-7777-777777777777", staffAssignmentId: STAFF_ASSIGNMENT_ID,
        staffCode: "ST-01", fullName: "Asha Rao", designation: "Professor", departmentId: DEPT_ID, departmentName: "Computer Science",
      }] } });
      return Promise.resolve({ data: [] });
    });
    (api.put as ReturnType<typeof vi.fn>).mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 403, data: { error: "Not authorized for MENTOR_ASSIGN." } },
    });
    renderWithTenant(<MentorAssignmentPage />, TENANT_ID, "HEAD_OF_DEPARTMENT");
    await screen.findByRole("option", { name: "CSE-A" });
    fireEvent.change(screen.getByLabelText(/Section/i), { target: { value: SECTION_ID } });
    await screen.findByRole("option", { name: /Asha Rao/ });
    fireEvent.change(screen.getByLabelText(/New mentor/i), { target: { value: STAFF_ASSIGNMENT_ID } });
    fireEvent.click(screen.getByRole("button", { name: /assign mentor/i }));

    expect(await screen.findByText("Permission denied")).toBeInTheDocument();
    expect(await screen.findByText(/do not have permission/i)).toBeInTheDocument();
  });

  it("uses scoped section and person pickers for CLASS_TEACHER_ASSIGN", () => {
    renderWithTenant(<ClassTeacherAssignmentPage />, TENANT_ID, "SCHOOL_ADMIN");
    expect(screen.getByText(/This assigns a section, not a class/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Section/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/New Class Teacher/i)).toBeInTheDocument();
  });

  it("sends the enabled flag for STUDENT_LOGIN_SWITCH", async () => {
    (api.put as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: { classId: DEPT_ID, studentLoginEnabled: true },
    });
    renderWithTenant(<StudentLoginSwitchPage />);
    fireEvent.change(screen.getByLabelText(/Class ID/i), { target: { value: DEPT_ID } });
    fireEvent.click(screen.getByLabelText(/Enable view-only student login/i));
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() =>
      expect(api.put).toHaveBeenCalledWith(`/tenants/${TENANT_ID}/classes/${DEPT_ID}/student-login`, {
        enabled: true,
      }),
    );
  });

  it("shows the empty state for calendar events before any institution ID is loaded", () => {
    renderWithTenant(<CalendarEventsPage />);
    expect(screen.getByText(/No events loaded yet/i)).toBeInTheDocument();
  });

  it("places weekly-off validation messages beside the relevant fields", () => {
    renderWithTenant(<WeeklyOffRulesPage />);
    fireEvent.click(screen.getByRole("button", { name: /add rule/i }));

    expect(screen.getByText("Enter a valid institution ID.")).toBeInTheDocument();
    expect(screen.getByText("Choose an effective-from date.")).toBeInTheDocument();
    expect(screen.queryByText("Institution ID and effective-from date are required.")).not.toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it("lists weekly-off rules returned by the API with the configured day labels", async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: [
        {
          weeklyOffRuleId: "44444444-4444-4444-4444-444444444444",
          institutionId: DEPT_ID,
          dayOfWeek: 7,
          occurrence: "ALL",
          effectiveFrom: "2026-01-01",
          effectiveTo: null,
        },
      ],
    });
    renderWithTenant(<WeeklyOffRulesPage />);
    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: DEPT_ID } });
    fireEvent.click(screen.getByRole("button", { name: /load rules/i }));

    await waitFor(() =>
      expect(api.get).toHaveBeenCalledWith(`/tenants/${TENANT_ID}/weekly-off-rules`, {
        params: { institutionId: DEPT_ID },
      }),
    );
    await waitFor(() => expect(screen.getAllByText("Sunday").length).toBeGreaterThan(0));
    expect(screen.getByText("Open-ended")).toBeInTheDocument();
  });

  it("loads academic structure (departments and programs) for an institution (Story #29: STRUCTURE_MANAGE)", async () => {
    (api.get as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({
        data: [
          {
            departmentId: DEPT_ID,
            institutionId: "55555555-5555-5555-5555-555555555555",
            departmentCode: "CSE",
            departmentName: "Computer Science and Engineering",
            status: "ACTIVE",
          },
        ],
      })
      .mockResolvedValueOnce({
        data: [
          {
            programId: "66666666-6666-6666-6666-666666666666",
            institutionId: "55555555-5555-5555-5555-555555555555",
            departmentId: DEPT_ID,
            programCode: "BTECH_CSE",
            programName: "B.Tech Computer Science",
            durationYears: 4,
            semestersPerYear: 2,
            status: "ACTIVE",
          },
        ],
      });

    renderWithTenant(<AcademicStructurePage />);
    fireEvent.change(screen.getByLabelText(/Institution ID/i), {
      target: { value: "55555555-5555-5555-5555-555555555555" },
    });
    fireEvent.click(screen.getByRole("button", { name: /load structure/i }));

    await waitFor(() => {
      expect(screen.getByText("Computer Science and Engineering")).toBeInTheDocument();
      expect(screen.getByText("CSE")).toBeInTheDocument();
      expect(screen.getByText("B.Tech Computer Science")).toBeInTheDocument();
      expect(screen.getByText("BTECH_CSE")).toBeInTheDocument();
    });
  });

  it("loads school classes and sections without showing college department/programme panels", async () => {
    const institutionId = "55555555-5555-5555-5555-555555555555";
    const classId = "77777777-7777-7777-7777-777777777777";
    const sessionId = "88888888-8888-8888-8888-888888888888";
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith("/classes")) return Promise.resolve({ data: [
        { classId, institutionId, className: "Grade 8", displayOrder: 8, studentLoginEnabled: false, status: "ACTIVE" },
      ] });
      if (url.endsWith("/sections")) return Promise.resolve({ data: [
        { sectionId: "99999999-8888-7777-6666-555555555555", institutionId, academicSessionId: sessionId, semesterId: null, classId, sectionName: "A", status: "ACTIVE" },
      ] });
      return Promise.resolve({ data: [
        { academicSessionId: sessionId, tenantId: TENANT_ID, institutionId, sessionLabel: "2026–27", startDate: "2026-06-01", endDate: "2027-05-31", academicYear: 2026, status: "ACTIVE", rowVersion: 1 },
      ] });
    });

    renderWithTenant(<AcademicStructurePage />, TENANT_ID, "SCHOOL_ADMIN");
    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: institutionId } });
    fireEvent.click(screen.getByRole("button", { name: /load structure/i }));

    expect((await screen.findAllByText("Grade 8")).length).toBeGreaterThan(0);
    expect(screen.getByText("Section A", { selector: "strong" })).toBeInTheDocument();
    expect(screen.getByText("School classes")).toBeInTheDocument();
    expect(screen.queryByText("Academic Departments")).not.toBeInTheDocument();
    expect(screen.queryByText("Degree Programs")).not.toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith(`/api/tenants/${TENANT_ID}/institutions/${institutionId}/classes`);
    expect(api.get).toHaveBeenCalledWith(`/api/tenants/${TENANT_ID}/institutions/${institutionId}/sections`);
  });

  it("creates a school section with its class and academic session IDs", async () => {
    const institutionId = "55555555-5555-5555-5555-555555555555";
    const classId = "77777777-7777-7777-7777-777777777777";
    const sessionId = "88888888-8888-8888-8888-888888888888";
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith("/classes")) return Promise.resolve({ data: [
        { classId, institutionId, className: "Grade 8", displayOrder: 8, studentLoginEnabled: false, status: "ACTIVE" },
      ] });
      if (url.endsWith("/sections")) return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [
        { academicSessionId: sessionId, tenantId: TENANT_ID, institutionId, sessionLabel: "2026–27", startDate: "2026-06-01", endDate: "2027-05-31", academicYear: 2026, status: "ACTIVE", rowVersion: 1 },
      ] });
    });
    (api.post as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: { sectionId: "99999999-8888-7777-6666-555555555555", institutionId, academicSessionId: sessionId, semesterId: null, classId, sectionName: "B", status: "ACTIVE" },
    });

    renderWithTenant(<AcademicStructurePage />, TENANT_ID, "SCHOOL_ADMIN");
    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: institutionId } });
    fireEvent.click(screen.getByRole("button", { name: /load structure/i }));
    await screen.findAllByText("Grade 8");

    fireEvent.change(screen.getByLabelText(/Section name/i), { target: { value: "B" } });
    fireEvent.click(screen.getByRole("button", { name: /create section/i }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      `/api/tenants/${TENANT_ID}/institutions/${institutionId}/sections`,
      { academicSessionId: sessionId, classId, sectionName: "B" },
    ));
  });

  it("opens modal and creates department under college (Story #29: STRUCTURE_MANAGE)", async () => {
    (api.get as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({
        data: [
          {
            departmentId: DEPT_ID,
            institutionId: "55555555-5555-5555-5555-555555555555",
            departmentCode: "ECE",
            departmentName: "Electronics and Communication",
            status: "ACTIVE",
          },
        ],
      })
      .mockResolvedValueOnce({ data: [] });

    (api.post as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: {
        departmentId: DEPT_ID,
        institutionId: "55555555-5555-5555-5555-555555555555",
        departmentCode: "ECE",
        departmentName: "Electronics and Communication",
        status: "ACTIVE",
      },
    });

    renderWithTenant(<AcademicStructurePage />);
    fireEvent.change(screen.getByLabelText(/Institution ID/i), {
      target: { value: "55555555-5555-5555-5555-555555555555" },
    });
    fireEvent.click(screen.getByRole("button", { name: /load structure/i }));

    await waitFor(() => expect(screen.getByText(/No departments found for this institution/i)).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /Add Department/i }));

    const dialog = screen.getByRole("dialog", { name: /Add Department/i });
    expect(dialog).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Department Code/i), { target: { value: "ECE" } });
    fireEvent.change(screen.getByLabelText(/Department Name/i), { target: { value: "Electronics and Communication" } });

    fireEvent.click(screen.getByRole("button", { name: /Create Department/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith(
        `/api/tenants/${TENANT_ID}/institutions/55555555-5555-5555-5555-555555555555/departments`,
        { departmentCode: "ECE", departmentName: "Electronics and Communication" }
      );
    });
  });

  it("loads and displays master academic sessions on CalendarEventsPage", async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: [
        {
          academicSessionId: "11111111-2222-3333-4444-555555555555",
          tenantId: TENANT_ID,
          institutionId: null,
          sessionLabel: "2026-27",
          startDate: "2026-06-01",
          endDate: "2027-05-31",
          academicYear: 2026,
          status: "ACTIVE",
          rowVersion: 1,
        },
      ],
    });

    renderWithTenant(<CalendarEventsPage />);
    fireEvent.click(screen.getByRole("button", { name: /load sessions/i }));

    await waitFor(() => {
      expect(screen.getByText("2026-27")).toBeInTheDocument();
      expect(screen.getByText("2026")).toBeInTheDocument();
    });
  });

  it("loads and displays master calendar events on CalendarEventsPage", async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: [
        {
          academicCalendarEventId: "99999999-8888-7777-6666-555555555555",
          tenantId: TENANT_ID,
          academicSessionId: "11111111-2222-3333-4444-555555555555",
          institutionId: null,
          sourceLevel: "UNIVERSITY",
          eventType: "SEMESTER_DATES",
          title: "Odd Semester 2026",
          startDate: "2026-07-01",
          endDate: "2026-11-30",
          rowVersion: 1,
        },
      ],
    });

    renderWithTenant(<CalendarEventsPage />);
    fireEvent.click(screen.getByRole("button", { name: /load master calendar/i }));

    await waitFor(() => {
      expect(screen.getByText("Odd Semester 2026")).toBeInTheDocument();
      expect(screen.getByText("Semester dates")).toBeInTheDocument();
    });
  });

  it("shows term-lock UUID errors beside their fields before calling the API", () => {
    renderWithTenant(<TermLockPage />);
    fireEvent.click(screen.getByRole("button", { name: /Lock term/i }));

    expect(screen.getByText("Enter a valid institution UUID.")).toBeInTheDocument();
    expect(screen.getByText("Enter a valid academic session UUID.")).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it("locks academic term upon submit (Story #43: TERM_LOCK / Rule R2-08)", async () => {
    const instId = "11111111-2222-3333-4444-555555555555";
    const sessionId = "22222222-3333-4444-5555-666666666666";

    (api.get as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ data: [] });
    (api.post as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: {
        termLockId: "lock-1",
        tenantId: TENANT_ID,
        institutionId: instId,
        academicSessionId: sessionId,
        lockState: "LOCKED",
        rowVersion: 1,
      },
    });

    renderWithTenant(<TermLockPage />);

    const instInput = screen.getByLabelText(/Institution ID/i);
    const sessionInput = screen.getByLabelText(/Academic Session ID/i);

    fireEvent.change(instInput, { target: { value: instId } });
    fireEvent.change(sessionInput, { target: { value: sessionId } });

    const lockBtn = screen.getByRole("button", { name: /Lock term/i });
    fireEvent.click(lockBtn);

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith(
        `/api/tenants/${TENANT_ID}/institutions/${instId}/term-locks`,
        expect.objectContaining({
          academicSessionId: sessionId,
        })
      );
      expect(
        screen.getByText(/Term locked successfully\. Attendance and marks can no longer be changed/i)
      ).toBeInTheDocument();
    });
  });

  it("unlocks term with statutory reason via modal (Story #44: TERM_UNLOCK / Rule AT-09)", async () => {
    const instId = "11111111-2222-3333-4444-555555555555";
    const sessionId = "22222222-3333-4444-5555-666666666666";

    (api.get as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: [
        {
          termLockId: "lock-1",
          tenantId: TENANT_ID,
          institutionId: instId,
          academicSessionId: sessionId,
          lockState: "LOCKED",
          rowVersion: 1,
        },
      ],
    });

    (api.post as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: {
        termLockId: "lock-1",
        tenantId: TENANT_ID,
        institutionId: instId,
        academicSessionId: sessionId,
        lockState: "TEMP_UNLOCKED",
        unlockUntil: "2026-11-10T00:00:00Z",
        rowVersion: 2,
      },
    });

    renderWithTenant(<TermLockPage />);

    const instInput = screen.getByLabelText(/Institution ID/i);
    fireEvent.change(instInput, { target: { value: instId } });

    await waitFor(() => {
      expect(screen.getByText(sessionId)).toBeInTheDocument();
    });

    const unlockBtn = screen.getByRole("button", { name: /Unlock Term/i });
    fireEvent.click(unlockBtn);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /Temporary Term Unlock/i })).toBeInTheDocument();
    });

    const reasonInput = screen.getByLabelText(/Statutory Justification \/ Reason \*/i);
    fireEvent.change(reasonInput, { target: { value: "Re-evaluation grievance approved" } });

    const confirmBtn = screen.getByRole("button", { name: /Confirm Unlock/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith(
        `/api/tenants/${TENANT_ID}/institutions/${instId}/term-locks/lock-1/unlocks`,
        expect.objectContaining({
          reason: "Re-evaluation grievance approved",
          unlockDurationHours: 72,
        })
      );
      expect(screen.getByText(/Term temporarily unlocked/i)).toBeInTheDocument();
    });
  });

  it("shows delegation field errors beside invalid IDs and dates", () => {
    renderWithTenant(<DelegationsApprovalsPage />);
    fireEvent.click(screen.getByRole("button", { name: /Create delegation/i }));

    expect(screen.getByText("Enter a valid user account UUID.")).toBeInTheDocument();
    expect(screen.getByText("Choose when the delegation begins.")).toBeInTheDocument();
    expect(screen.getByText("Choose when the delegation ends.")).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it("creates time-bounded delegation (Story #20: APPROVAL_DELEGATE / Rule 11)", async () => {
    const delegateId = "44444444-4444-4444-4444-444444444444";
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: [] });
    (api.post as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: {
        delegationId: "del-1",
        tenantId: TENANT_ID,
        delegatorUserId: "u-1",
        delegateUserId: delegateId,
        delegationBasis: "SAME_OR_HIGHER",
        startsAt: "2026-11-08T00:00:00Z",
        endsAt: "2026-11-15T00:00:00Z",
        status: "ACTIVE",
        rowVersion: 1,
      },
    });

    renderWithTenant(<DelegationsApprovalsPage />);

    const delegateInput = screen.getByLabelText(/Delegate User Account ID \(UUID\) \*/i);
    const startInput = screen.getByLabelText(/Effective From \*/i);
    const endInput = screen.getByLabelText(/Effective Until \*/i);

    fireEvent.change(delegateInput, { target: { value: delegateId } });
    fireEvent.change(startInput, { target: { value: "2026-11-08T00:00" } });
    fireEvent.change(endInput, { target: { value: "2026-11-15T00:00" } });

    const submitBtn = screen.getByRole("button", { name: /Create delegation/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith(
        `/api/tenants/${TENANT_ID}/delegations`,
        expect.objectContaining({
          delegateUserId: delegateId,
          delegationBasis: "SAME_OR_HIGHER",
        })
      );
      expect(screen.getByText(/Approval authority delegated successfully/i)).toBeInTheDocument();
    });
  });

  it("rejects mixed valid and invalid request IDs beside the bulk ID field", () => {
    renderWithTenant(<DelegationsApprovalsPage />);
    fireEvent.change(screen.getByLabelText(/Approval Request IDs/i), { target: { value: "55555555-5555-5555-5555-555555555555, invalid" } });
    fireEvent.click(screen.getByRole("button", { name: /Apply bulk decision/i }));

    expect(screen.getByText(/Every approval request ID must be a valid UUID/i)).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it("applies bulk approval decisions (Story #21: APPROVAL_BULK / Rule AP-25)", async () => {
    const req1 = "55555555-5555-5555-5555-555555555555";
    const req2 = "66666666-6666-6666-6666-666666666666";

    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: [] });
    (api.post as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: {
        batchId: "batch-1",
        totalProcessed: 2,
        succeeded: 2,
        failed: 0,
        errors: [],
      },
    });

    renderWithTenant(<DelegationsApprovalsPage />);

    const idsInput = screen.getByLabelText(/Approval Request IDs/i);
    const commentInput = screen.getByLabelText(/Batch Comment/i);

    fireEvent.change(idsInput, { target: { value: `${req1}, ${req2}` } });
    fireEvent.change(commentInput, { target: { value: "Batch review approved" } });

    const submitBtn = screen.getByRole("button", { name: /Apply bulk decision/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith(
        `/api/tenants/${TENANT_ID}/approvals/bulk-decisions`,
        expect.objectContaining({
          approvalRequestIds: [req1, req2],
          decision: "APPROVED",
          comment: "Batch review approved",
        })
      );
      expect(screen.getByText(/Bulk decision applied: 2 items processed successfully/i)).toBeInTheDocument();
    });
  });
  it("keeps every governance tab associated with a mounted tab panel", async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: [] });
    renderWithTenant(<AcademicGovernancePage />);

    const tabs = screen.getAllByRole("tab");
    expect(tabs).toHaveLength(4);
    for (const tab of tabs) {
      const panelId = tab.getAttribute("aria-controls");
      expect(panelId).toBeTruthy();
      expect(document.getElementById(panelId!)).toHaveAttribute("role", "tabpanel");
      expect(document.getElementById(panelId!)).toHaveAttribute("aria-labelledby", tab.id);
    }
  });

  it("renders academic governance and submits program rules (Story #30: PROGRAM_RULES_SET)", async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: [
        {
          programRuleSetId: "rule-1",
          institutionId: null,
          programId: null,
          versionNo: 1,
          rules: '{"minCredits": 160}',
          effectiveFrom: "2026-06-01",
          effectiveTo: null,
          status: "ACTIVE",
          rowVersion: 1,
        },
      ],
    });

    (api.post as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: {
        programRuleSetId: "rule-2",
        institutionId: null,
        programId: null,
        versionNo: 2,
        rules: '{"minCredits": 180}',
        effectiveFrom: "2026-07-01",
        effectiveTo: null,
        status: "DRAFT",
        rowVersion: 1,
      },
    });

    renderWithTenant(<AcademicGovernancePage />);

    await waitFor(() => {
      expect(screen.getByText("rule-1")).toBeInTheDocument();
    });

    const openModalBtn = screen.getByRole("button", { name: /\+ Set Program Rules/i });
    fireEvent.click(openModalBtn);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /Set Curriculum Rules/i })).toBeInTheDocument();
    });

    const saveBtn = screen.getByRole("button", { name: /Save Rules/i });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(screen.getByText(/Program rule set created in draft/i)).toBeInTheDocument();
    });
  });

  it("submits grading scheme, subject allocation, and promotion run (Stories #34, #36, #39, #40)", async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: [] });
    (api.post as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({
        data: {
          schemeVersionId: "scheme-1",
          institutionId: null,
          schemeName: "Standard 10-Point CGPA Scale",
          effectiveFrom: "2026-06-01",
          status: "DRAFT",
          rowVersion: 1,
        },
      })
      .mockResolvedValueOnce({
        data: {
          allocationId: "alloc-1",
          institutionId: "inst-1",
          courseOfferingId: "off-1",
          staffAssignmentId: "staff-1",
          status: "ACTIVE",
          rowVersion: 1,
        },
      })
      .mockResolvedValueOnce({
        data: {
          promotionRunId: "prom-1",
          institutionId: "inst-1",
          fromSessionId: "sess-1",
          toSessionId: "sess-2",
          status: "DRAFT",
          rowVersion: 1,
        },
      })
      .mockResolvedValueOnce({
        data: {
          promotionRunId: "prom-1",
          institutionId: "inst-1",
          fromSessionId: "sess-1",
          toSessionId: "sess-2",
          status: "PENDING_APPROVAL",
          rowVersion: 2,
        },
      })
      .mockResolvedValueOnce({
        data: {
          promotionRunId: "prom-1",
          institutionId: "inst-1",
          fromSessionId: "sess-1",
          toSessionId: "sess-2",
          status: "APPROVED",
          rowVersion: 3,
        },
      });

    renderWithTenant(<AcademicGovernancePage />);

    // Test grading schemes tab
    fireEvent.click(screen.getByRole("tab", { name: /Grading schemes/i }));
    fireEvent.click(screen.getByRole("button", { name: /\+ Set Grading Scheme/i }));
    fireEvent.click(screen.getByRole("button", { name: /Save Scheme/i }));

    await waitFor(() => {
      expect(screen.getByText(/Grading scheme "Standard 10-Point CGPA Scale" created in draft/i)).toBeInTheDocument();
    });

    // Test subject allocation tab
    fireEvent.click(screen.getByRole("tab", { name: /Subject allocations/i }));
    fireEvent.click(screen.getByRole("button", { name: "+ Allocate Subject" }));
    fireEvent.change(screen.getByLabelText(/Institution UUID \*/i), { target: { value: "inst-1" } });
    fireEvent.change(screen.getByLabelText(/Course Offering UUID \*/i), { target: { value: "off-1" } });
    fireEvent.change(screen.getByLabelText(/Teacher Staff Assignment UUID \*/i), { target: { value: "staff-1" } });
    fireEvent.click(screen.getByRole("button", { name: "Allocate Subject" }));

    await waitFor(() => {
      expect(screen.getByText(/Subject allocated to teacher/i)).toBeInTheDocument();
    });

    // Test year-end promotions tab
    fireEvent.click(screen.getByRole("tab", { name: /Year-end promotions/i }));
    fireEvent.click(screen.getByRole("button", { name: /\+ New Promotion Run/i }));
    fireEvent.change(screen.getByLabelText(/Institution UUID \*/i), { target: { value: "inst-1" } });
    fireEvent.change(screen.getByLabelText(/From Session UUID \*/i), { target: { value: "sess-1" } });
    fireEvent.change(screen.getByLabelText(/To Session UUID \*/i), { target: { value: "sess-2" } });
    fireEvent.click(screen.getByRole("button", { name: /Create Run/i }));

    await waitFor(() => {
      expect(screen.getByText(/Year-end promotion run created in draft/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Submit for approval/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Submit for approval/i }));

    await waitFor(() => {
      expect(screen.getByText(/Promotion run submitted for Principal approval/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^Approve$/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /^Approve$/i }));

    await waitFor(() => {
      expect(screen.getByText(/Promotion run approved by Principal/i)).toBeInTheDocument();
    });
  });
});
