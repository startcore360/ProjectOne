import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import type { ReactElement } from "react";
import api from "../../../services/axios";
import authReducer from "../../../store/slices/authSlice";
import tenantPermissionsReducer, { setTenantPermissions } from "../../../store/slices/tenantPermissionsSlice";
import {
  AcademicGovernancePage,
  AcademicStructurePage,
  InstitutionHierarchyPage,
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
  SUPPORT_SESSION_TOKEN_KEY: "test.support.token",
  SUPPORT_SESSION_EXPIRY_KEY: "test.support.expiry",
  SUPPORT_SESSION_TICKET_KEY: "test.support.ticket",
  SUPPORT_SESSION_PLATFORM_ORIGIN_KEY: "test.support.origin",
  default: { put: vi.fn(), get: vi.fn(), post: vi.fn(), delete: vi.fn() },
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
  return render(<MemoryRouter><Provider store={store}>{element}</Provider></MemoryRouter>);
}

describe("Institution setup action screens", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.get as ReturnType<typeof vi.fn>).mockReset();
    (api.post as ReturnType<typeof vi.fn>).mockReset();
    (api.put as ReturnType<typeof vi.fn>).mockReset();
    (api.delete as ReturnType<typeof vi.fn>).mockReset();
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
    }, { headers: { "If-Match": "\"1\"" } }));
    expect(await screen.findByText(/HOD assignment recorded/i)).toBeInTheDocument();
  });

  it("surfaces a 403 from MENTOR_ASSIGN after selecting a college section and mentor", async () => {
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith("/sections")) return Promise.resolve({ data: [{
        sectionId: SECTION_ID, sectionName: "CSE-A", institutionId: INST_ID, academicSessionId: "88888888-8888-8888-8888-888888888888",
        semesterId: "99999999-9999-9999-9999-999999999999", classId: null, status: "ACTIVE", rowVersion: 1,
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

  it("loads a school and class by name before saving student-login state", async () => {
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith("/institutions")) return Promise.resolve({ data: { items: [{ institutionId: INST_ID, institutionName: "Manoj School", institutionCode: "MNS", institutionType: "SCHOOL" }] } });
      if (url.endsWith(`/institutions/${INST_ID}/classes`)) return Promise.resolve({ data: [{ classId: DEPT_ID, className: "Grade 8", status: "ACTIVE" }] });
      if (url.endsWith(`/classes/${DEPT_ID}`)) return Promise.resolve({ data: { classId: DEPT_ID, studentLoginEnabled: false, rowVersion: 4 } });
      return Promise.resolve({ data: [] });
    });
    (api.put as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: { classId: DEPT_ID, studentLoginEnabled: true, rowVersion: 5 },
    });
    renderWithTenant(<StudentLoginSwitchPage />);
    fireEvent.change(await screen.findByLabelText(/Institution/i), { target: { value: INST_ID } });
    fireEvent.change(await screen.findByLabelText(/Class or grade/i), { target: { value: DEPT_ID } });
    expect(await screen.findByText(/Current setting loaded for the selected class/i)).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText(/Enable view-only student login/i));
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() =>
      expect(api.put).toHaveBeenCalledWith(`/tenants/${TENANT_ID}/classes/${DEPT_ID}/student-login`, {
        enabled: true,
      }, { headers: { "If-Match": "\"4\"" } }),
    );
  });

  it("shows the empty state for calendar events before any institution ID is loaded", () => {
    renderWithTenant(<CalendarEventsPage />);
    expect(screen.getByText(/No events loaded yet/i)).toBeInTheDocument();
  });

  it("places weekly-off validation messages beside the relevant fields", () => {
    renderWithTenant(<WeeklyOffRulesPage />);
    fireEvent.click(screen.getByRole("button", { name: /add rule/i }));

    expect(screen.getByText("Choose a registered institution.")).toBeInTheDocument();
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

  it("renders the tenant-rooted hierarchy with colleges, schools and nested departments", async () => {
    const institutionId = "55555555-5555-5555-5555-555555555555";
    const schoolId = "66666666-6666-6666-6666-666666666666";
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        tenantId: TENANT_ID,
        tenantName: "Northbridge University Group",
        tenantType: "UNIVERSITY_GROUP",
        institutions: [
          {
            institution: {
              tenantId: TENANT_ID, institutionId, institutionType: "COLLEGE",
              institutionCode: "NBC-01", institutionName: "Northbridge College",
              status: "ACTIVE", externalUniversityMode: false, underUniversity: true,
              createdAt: "", updatedAt: "", rowVersion: 3,
            },
            departments: [{
              departmentId: DEPT_ID, institutionId, departmentCode: "CSE",
              departmentName: "Computer Science", hodStaffAssignmentId: null, status: "ACTIVE",
            }],
          },
          {
            institution: {
              tenantId: TENANT_ID, institutionId: schoolId, institutionType: "SCHOOL",
              institutionCode: "NPS-01", institutionName: "Northbridge Public School",
              status: "ACTIVE", externalUniversityMode: false, underUniversity: false,
              createdAt: "", updatedAt: "", rowVersion: 1,
            },
            departments: [],
          },
        ],
      },
    });

    renderWithTenant(<InstitutionHierarchyPage />);
    expect((await screen.findAllByText("Northbridge University Group")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Northbridge College").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Northbridge Public School").length).toBeGreaterThan(0);
    expect(screen.getByText("Computer Science")).toBeInTheDocument();
    expect(screen.getByText("HOD not assigned")).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith(`/api/tenants/${TENANT_ID}/institutions/hierarchy`);
  });

  it("edits an institution profile with the current row-version If-Match header", async () => {
    const institutionId = "55555555-5555-5555-5555-555555555555";
    const institution = {
      tenantId: TENANT_ID, institutionId, institutionType: "COLLEGE" as const,
      institutionCode: "NBC-01", institutionName: "Northbridge College",
      status: "ACTIVE" as const, externalUniversityMode: false, underUniversity: true,
      addressLine: "Old address", city: "Bengaluru", stateName: "Karnataka",
      postalCode: "560001", principalInviteEmail: "principal@example.edu",
      createdAt: "", updatedAt: "", rowVersion: 3,
    };
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        tenantId: TENANT_ID, tenantName: "Northbridge University Group", tenantType: "UNIVERSITY_GROUP",
        institutions: [{ institution, departments: [] }],
      },
    });
    (api.put as ReturnType<typeof vi.fn>).mockResolvedValue({ data: { ...institution, institutionName: "Northbridge College of Science", rowVersion: 4 } });

    renderWithTenant(<InstitutionHierarchyPage />);
    await screen.findByText("Northbridge College");
    fireEvent.click(screen.getByRole("button", { name: /edit profile/i }));
    fireEvent.change(screen.getByLabelText(/Institution name/i), { target: { value: "Northbridge College of Science" } });
    fireEvent.click(screen.getByRole("button", { name: /save institution/i }));

    await waitFor(() => expect(api.put).toHaveBeenCalledWith(
      `/api/tenants/${TENANT_ID}/institutions/${institutionId}`,
      {
        institutionName: "Northbridge College of Science",
        addressLine: "Old address",
        city: "Bengaluru",
        stateName: "Karnataka",
        postalCode: "560001",
        principalInviteEmail: "principal@example.edu",
        externalUniversityMode: false,
      },
      { headers: { "If-Match": "\"3\"" } },
    ));
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
      expect(screen.getAllByText("Computer Science and Engineering").length).toBeGreaterThan(0);
      expect(screen.getAllByText("CSE").length).toBeGreaterThan(0);
      expect(screen.getAllByText("B.Tech Computer Science").length).toBeGreaterThan(0);
      expect(screen.getByText("BTECH_CSE")).toBeInTheDocument();
    });
  });

  it("assigns a department HOD from the integrated department-management modal", async () => {
    const institutionId = "55555555-5555-5555-5555-555555555555";
    const staffAssignmentId = "77777777-7777-7777-7777-777777777777";
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith("/departments")) return Promise.resolve({ data: [{
        departmentId: DEPT_ID, institutionId, departmentCode: "CSE", departmentName: "Computer Science",
        status: "ACTIVE", facultyCount: 5, studentCount: 42, rowVersion: 2,
      }] });
      if (url.endsWith("/programs")) return Promise.resolve({ data: [] });
      if (url.endsWith("/staff")) return Promise.resolve({ data: { items: [{
        staffProfileId: "88888888-8888-8888-8888-888888888888", staffAssignmentId,
        staffCode: "FAC-01", fullName: "Dr. Ada Lovelace", designation: "Professor",
        departmentId: DEPT_ID, departmentName: "Computer Science",
      }], page: { limit: 100, nextCursor: null, hasMore: false, totalCount: 1 } } });
      return Promise.resolve({ data: [] });
    });
    (api.put as ReturnType<typeof vi.fn>).mockResolvedValue({ data: { departmentId: DEPT_ID, hodStaffAssignmentId: staffAssignmentId } });

    renderWithTenant(<AcademicStructurePage />, TENANT_ID, "COLLEGE_ADMIN");
    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: institutionId } });
    fireEvent.click(screen.getByRole("button", { name: /load structure/i }));
    await screen.findByText("Computer Science");
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("42")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /assign HOD/i }));
    const facultyPicker = await screen.findByLabelText(/Faculty member/i);
    fireEvent.change(facultyPicker, { target: { value: staffAssignmentId } });
    fireEvent.click(screen.getByRole("button", { name: /save HOD assignment/i }));

    await waitFor(() => expect(api.put).toHaveBeenCalledWith(
      `/tenants/${TENANT_ID}/departments/${DEPT_ID}/hod`,
      { staffAssignmentId },
      { headers: { "If-Match": "\"2\"" } },
    ));
  });

  it("creates and retrieves immutable curriculum versions for a program", async () => {
    const institutionId = "55555555-5555-5555-5555-555555555555";
    const programId = "66666666-6666-6666-6666-666666666666";
    const version = {
      programCurriculumVersionId: "77777777-7777-7777-7777-777777777777",
      institutionId, programId, versionNumber: 1,
      curriculumJson: { courses: [{ code: "CS101", credits: 4 }] },
      effectiveFrom: "2026-07-01", effectiveTo: null, createdAt: "2026-06-01T00:00:00Z", createdBy: "u-1",
    };
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith("/departments")) return Promise.resolve({ data: [] });
      if (url.endsWith("/programs")) return Promise.resolve({ data: [{
        programId, institutionId, departmentId: DEPT_ID, programCode: "BTECH",
        programName: "B.Tech", durationYears: 4, semestersPerYear: 2, status: "ACTIVE", rowVersion: 1,
      }] });
      if (url.endsWith("/curriculum-versions")) return Promise.resolve({ data: [version] });
      return Promise.resolve({ data: [] });
    });
    (api.post as ReturnType<typeof vi.fn>).mockResolvedValue({ data: version });

    renderWithTenant(<AcademicStructurePage />, TENANT_ID, "COLLEGE_ADMIN");
    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: institutionId } });
    fireEvent.click(screen.getByRole("button", { name: /load structure/i }));
    await screen.findByRole("link", { name: "B.Tech" });

    fireEvent.change(screen.getAllByLabelText("Program")[0], { target: { value: programId } });
    fireEvent.click(screen.getByRole("button", { name: /load curriculum history/i }));
    await screen.findByText("Version 1");

    fireEvent.change(screen.getByLabelText(/Curriculum JSON/i), {
      target: { value: '{"courses":[{"code":"CS101","credits":4}]}' },
    });
    fireEvent.change(screen.getByLabelText(/Effective from/i), { target: { value: "2026-07-01" } });
    fireEvent.click(screen.getByRole("button", { name: /create immutable version/i }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      `/api/tenants/${TENANT_ID}/institutions/${institutionId}/programs/${programId}/curriculum-versions`,
      { curriculumJson: { courses: [{ code: "CS101", credits: 4 }] }, effectiveFrom: "2026-07-01", effectiveTo: null },
    ));
  });

  it("creates a dated semester under a selected college program year", async () => {
    const institutionId = "55555555-5555-5555-5555-555555555555";
    const programId = "66666666-6666-6666-6666-666666666666";
    const programYearId = "77777777-7777-7777-7777-777777777777";
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith("/departments")) return Promise.resolve({ data: [] });
      if (url.endsWith("/programs")) return Promise.resolve({ data: [{
        programId, institutionId, departmentId: DEPT_ID, programCode: "BTECH",
        programName: "B.Tech", durationYears: 4, semestersPerYear: 2, status: "ACTIVE",
      }] });
      if (url.endsWith("/years")) return Promise.resolve({ data: [{
        programYearId, institutionId, programId, yearNumber: 1, label: "Year 1",
      }] });
      if (url.endsWith("/semesters")) return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
    (api.post as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { semesterId: "99999999-8888-7777-6666-555555555555", institutionId, programYearId,
        semesterNumber: 1, label: "Semester 1", startDate: "2026-07-01", endDate: "2026-11-30" },
    });

    renderWithTenant(<AcademicStructurePage />);
    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: institutionId } });
    fireEvent.click(screen.getByRole("button", { name: /load structure/i }));
    await screen.findByRole("link", { name: "B.Tech" });

    fireEvent.change(screen.getAllByLabelText("Program")[1], { target: { value: programId } });
    fireEvent.click(screen.getByRole("button", { name: /load program years/i }));
    await screen.findByLabelText("Program year");

    fireEvent.change(screen.getByLabelText(/Start date/i), { target: { value: "2026-07-01" } });
    fireEvent.change(screen.getByLabelText(/End date/i), { target: { value: "2026-11-30" } });
    fireEvent.click(screen.getByRole("button", { name: /create dated semester/i }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      `/api/tenants/${TENANT_ID}/institutions/${institutionId}/program-years/${programYearId}/semesters`,
      { semesterNumber: 1, label: "Semester 1", startDate: "2026-07-01", endDate: "2026-11-30" },
    ));
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
    expect(screen.getByRole("link", { name: "Section A" })).toBeInTheDocument();
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

    fireEvent.change(screen.getByLabelText(/Section name/i, { selector: "#school-section-name" }), { target: { value: "B" } });
    fireEvent.change(screen.getByLabelText(/Section capacity/i), { target: { value: "45" } });
    fireEvent.click(screen.getByRole("button", { name: /create section/i }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      `/api/tenants/${TENANT_ID}/institutions/${institutionId}/sections`,
      { academicSessionId: sessionId, classId, sectionName: "B", capacity: 45 },
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

    expect(screen.getByText("Choose a registered institution.")).toBeInTheDocument();
    expect(screen.getByText("Choose an academic session for the selected institution.")).toBeInTheDocument();
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
    expect(screen.getByText("Term Locked: Records are immutable")).toBeInTheDocument();

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

  it("requires an eligible colleague and effective dates before creating a delegation", () => {
    renderWithTenant(<DelegationsApprovalsPage />);
    fireEvent.click(screen.getByRole("button", { name: /Create delegation/i }));

    expect(screen.getByText("Choose an eligible colleague from the list.")).toBeInTheDocument();
    expect(screen.getByText("Choose when the delegation begins.")).toBeInTheDocument();
    expect(screen.getByText("Choose when the delegation ends.")).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it("creates time-bounded delegation (Story #20: APPROVAL_DELEGATE / Rule 11)", async () => {
    const delegateId = "44444444-4444-4444-4444-444444444444";
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith("/delegations/eligible-users")) return Promise.resolve({ data: [{ userAccountId: delegateId, displayName: "Asha Rao", email: "asha@example.edu" }] });
      if (url.endsWith("/approvals/inbox")) return Promise.resolve({ data: { items: [] } });
      return Promise.resolve({ data: [] });
    });
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

    const delegateInput = await screen.findByLabelText(/Choose eligible colleague/i);
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

  it("requires selecting pending requests from the inbox for bulk decisions", () => {
    renderWithTenant(<DelegationsApprovalsPage />);
    fireEvent.click(screen.getByRole("button", { name: /Apply bulk decision/i }));

    expect(screen.getByText(/Select at least one request from your pending approval inbox/i)).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it("applies bulk approval decisions (Story #21: APPROVAL_BULK / Rule AP-25)", async () => {
    const req1 = "55555555-5555-5555-5555-555555555555";
    const req2 = "66666666-6666-6666-6666-666666666666";

    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith("/approvals/inbox")) return Promise.resolve({ data: { items: [
        { approvalRequestId: req1, stepNo: 1, approverRoleCode: "PRINCIPAL", decision: "PENDING" },
        { approvalRequestId: req2, stepNo: 2, approverRoleCode: "DEAN", decision: "PENDING" },
      ] } });
      if (url.endsWith("/delegations/eligible-users")) return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
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

    const firstRequest = await screen.findByLabelText(/Request awaiting Principal · Step 1/i);
    const secondRequest = await screen.findByLabelText(/Request awaiting Dean · Step 2/i);
    const commentInput = screen.getByLabelText(/Batch Comment/i);

    fireEvent.click(firstRequest);
    fireEvent.click(secondRequest);
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
    fireEvent.change(await screen.findByLabelText(/Institution \\*/i), { target: { value: "inst-1" } });
    fireEvent.change(await screen.findByLabelText(/Course offering \\*/i), { target: { value: "off-1" } });
    fireEvent.change(await screen.findByLabelText(/Teacher \\*/i), { target: { value: "staff-1" } });
    fireEvent.click(screen.getByRole("button", { name: "Allocate Subject" }));

    await waitFor(() => {
      expect(screen.getByText(/Subject allocated to teacher/i)).toBeInTheDocument();
    });

    // Test year-end promotions tab
    fireEvent.click(screen.getByRole("tab", { name: /Year-end promotions/i }));
    fireEvent.click(screen.getByRole("button", { name: /\+ New Promotion Run/i }));
    fireEvent.change(await screen.findByLabelText(/Institution \\*/i), { target: { value: "inst-1" } });
    fireEvent.change(await screen.findByLabelText(/Promote students from \\*/i), { target: { value: "sess-1" } });
    fireEvent.change(await screen.findByLabelText(/Promote students to \\*/i), { target: { value: "sess-2" } });
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
  it("edits a department using the current row version", async () => {
    const department = {
      departmentId: DEPT_ID, institutionId: INST_ID, departmentCode: "CSE",
      departmentName: "Computer Science", status: "ACTIVE", facultyCount: 8, studentCount: 120, rowVersion: 2,
    };
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith(`/institutions/${INST_ID}/departments`)) return Promise.resolve({ data: [department] });
      if (url.endsWith(`/institutions/${INST_ID}/programs`)) return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
    (api.put as ReturnType<typeof vi.fn>).mockResolvedValue({ data: { ...department, departmentName: "Computer Science and Engineering", rowVersion: 3 } });
    renderWithTenant(<AcademicStructurePage />);
    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: INST_ID } });
    fireEvent.click(screen.getByRole("button", { name: /load structure/i }));
    await screen.findByText("Computer Science");
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    fireEvent.change(screen.getByLabelText(/Department name/i), { target: { value: "Computer Science and Engineering" } });
    fireEvent.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => expect(api.put).toHaveBeenCalledWith(
      `/api/tenants/${TENANT_ID}/institutions/${INST_ID}/departments/${DEPT_ID}`,
      { departmentCode: "CSE", departmentName: "Computer Science and Engineering" },
      { headers: { "If-Match": "\"2\"" } },
    ));
  });

  it("edits a program using If-Match and its department relationship", async () => {
    const department = { departmentId: DEPT_ID, institutionId: INST_ID, departmentCode: "CSE", departmentName: "Computer Science", status: "ACTIVE", rowVersion: 1 };
    const program = {
      programId: SECTION_ID, institutionId: INST_ID, departmentId: DEPT_ID, programCode: "BTECH",
      programName: "B.Tech", durationYears: 4, semestersPerYear: 2, status: "ACTIVE", rowVersion: 3,
    };
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith(`/institutions/${INST_ID}/departments`)) return Promise.resolve({ data: [department] });
      if (url.endsWith(`/institutions/${INST_ID}/programs`)) return Promise.resolve({ data: [program] });
      return Promise.resolve({ data: [] });
    });
    (api.put as ReturnType<typeof vi.fn>).mockResolvedValue({ data: { ...program, programName: "B.Tech Computer Science", rowVersion: 4 } });
    renderWithTenant(<AcademicStructurePage />);
    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: INST_ID } });
    fireEvent.click(screen.getByRole("button", { name: /load structure/i }));
    await screen.findByRole("link", { name: "B.Tech" });
    fireEvent.click(screen.getAllByRole("button", { name: "Edit" })[1]);
    fireEvent.change(screen.getByLabelText(/Program name/i, { selector: "#edit-program-name" }), { target: { value: "B.Tech Computer Science" } });
    fireEvent.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => expect(api.put).toHaveBeenCalledWith(
      `/api/tenants/${TENANT_ID}/institutions/${INST_ID}/programs/${SECTION_ID}`,
      { departmentId: DEPT_ID, programCode: "BTECH", programName: "B.Tech Computer Science", durationYears: 4, semestersPerYear: 2 },
      { headers: { "If-Match": "\"3\"" } },
    ));
  });

  it("edits a school class with optimistic concurrency", async () => {
    const schoolClass = { classId: DEPT_ID, institutionId: INST_ID, className: "Grade 8", displayOrder: 8, studentLoginEnabled: false, status: "ACTIVE", rowVersion: 4 };
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith(`/institutions/${INST_ID}/classes`)) return Promise.resolve({ data: [schoolClass] });
      if (url.endsWith(`/institutions/${INST_ID}/sections`)) return Promise.resolve({ data: [] });
      if (url.endsWith(`/institutions/${INST_ID}/academic-sessions`)) return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
    (api.put as ReturnType<typeof vi.fn>).mockResolvedValue({ data: { ...schoolClass, className: "Grade 9", rowVersion: 5 } });
    renderWithTenant(<AcademicStructurePage />, TENANT_ID, "SCHOOL_ADMIN");
    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: INST_ID } });
    fireEvent.click(screen.getByRole("button", { name: /load structure/i }));
    await screen.findByText("Grade 8");
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    fireEvent.change(screen.getByLabelText(/Class or grade name/i, { selector: "#edit-class-name" }), { target: { value: "Grade 9" } });
    fireEvent.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => expect(api.put).toHaveBeenCalledWith(
      `/api/tenants/${TENANT_ID}/institutions/${INST_ID}/classes/${DEPT_ID}`,
      { className: "Grade 9", displayOrder: 8 },
      { headers: { "If-Match": "\"4\"" } },
    ));
  });

  it("edits a school section including capacity with optimistic concurrency", async () => {
    const schoolClass = { classId: DEPT_ID, institutionId: INST_ID, className: "Grade 8", displayOrder: 8, studentLoginEnabled: false, status: "ACTIVE", rowVersion: 1 };
    const sessionId = "77777777-7777-7777-7777-777777777777";
    const section = {
      sectionId: SECTION_ID, institutionId: INST_ID, academicSessionId: sessionId, semesterId: null,
      classId: DEPT_ID, sectionName: "A", status: "ACTIVE", capacity: 60, rowVersion: 6,
    };
    (api.get as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.endsWith(`/institutions/${INST_ID}/classes`)) return Promise.resolve({ data: [schoolClass] });
      if (url.endsWith(`/institutions/${INST_ID}/sections`)) return Promise.resolve({ data: [section] });
      if (url.includes(`/academic-sessions`)) return Promise.resolve({ data: [{
        academicSessionId: sessionId, sessionLabel: "2026-27", startDate: "2026-06-01", endDate: "2027-05-31", status: "ACTIVE",
      }] });
      return Promise.resolve({ data: [] });
    });
    (api.put as ReturnType<typeof vi.fn>).mockResolvedValue({ data: { ...section, sectionName: "B", capacity: 72, rowVersion: 7 } });
    renderWithTenant(<AcademicStructurePage />, TENANT_ID, "SCHOOL_ADMIN");
    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: INST_ID } });
    fireEvent.click(screen.getByRole("button", { name: /load structure/i }));
    await screen.findByText("Section A");
    fireEvent.click(screen.getAllByRole("button", { name: "Edit" })[1]);
    fireEvent.change(screen.getByLabelText(/Section name/i, { selector: "#edit-section-name" }), { target: { value: "B" } });
    fireEvent.change(screen.getByLabelText(/Section capacity/i, { selector: "#edit-section-capacity" }), { target: { value: "72" } });
    const saveButton = screen.getByRole("button", { name: /save changes/i });
    const editForm = saveButton.closest("form");
    expect(saveButton).toBeEnabled();
    expect(editForm).not.toBeNull();
    expect((editForm as HTMLFormElement).checkValidity()).toBe(true);
    fireEvent.click(saveButton);

    await waitFor(() => expect(api.put).toHaveBeenCalledWith(
      `/api/tenants/${TENANT_ID}/institutions/${INST_ID}/sections/${SECTION_ID}`,
      { sectionName: "B", classId: DEPT_ID, academicSessionId: sessionId, capacity: 72, semesterId: null },
      { headers: { "If-Match": "\"6\"" } },
    ));
  });

});
