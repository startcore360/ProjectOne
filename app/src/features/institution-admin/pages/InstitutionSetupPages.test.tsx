import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import type { ReactElement } from "react";
import api from "../../../services/axios";
import authReducer from "../../../store/slices/authSlice";
import {
  CalendarEventsPage,
  ClassTeacherAssignmentPage,
  HodAssignmentPage,
  MentorAssignmentPage,
  StudentLoginSwitchPage,
  WeeklyOffRulesPage,
} from "./InstitutionSetupPages";

vi.mock("../../../services/axios", () => ({
  default: { put: vi.fn(), get: vi.fn(), post: vi.fn() },
}));

const TENANT_ID = "11111111-1111-1111-1111-111111111111";
const DEPT_ID = "22222222-2222-2222-2222-222222222222";
const STAFF_ASSIGNMENT_ID = "33333333-3333-3333-3333-333333333333";

function renderWithTenant(element: ReactElement, tenantId: string | null = TENANT_ID) {
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: {
        user: tenantId
          ? {
              id: "u-1",
              displayName: "Test Admin",
              tenantId,
              userType: "STAFF" as const,
              platformRole: null,
              mustChangePassword: false,
            }
          : null,
        isAuthenticated: !!tenantId,
        loading: false,
      },
    },
  });
  return render(<Provider store={store}>{element}</Provider>);
}

describe("Institution setup action screens", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows the permission-denied state when there is no tenant identity", () => {
    renderWithTenant(<HodAssignmentPage />, null);
    expect(screen.getByText(/requires a signed-in institution identity/i)).toBeInTheDocument();
  });

  it("shows a validation error before calling the API with invalid IDs", async () => {
    renderWithTenant(<HodAssignmentPage />);
    fireEvent.click(screen.getByRole("button", { name: /assign hod/i }));
    expect(await screen.findByText(/must both be valid IDs/i)).toBeInTheDocument();
    expect(api.put).not.toHaveBeenCalled();
  });

  it("submits HOD_ASSIGN with the exact backend field names and shows success", async () => {
    (api.put as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: { departmentId: DEPT_ID, hodStaffAssignmentId: STAFF_ASSIGNMENT_ID },
    });
    renderWithTenant(<HodAssignmentPage />);
    fireEvent.change(screen.getByLabelText(/Department ID/i), { target: { value: DEPT_ID } });
    fireEvent.change(screen.getByLabelText(/Staff assignment ID \(new HOD\)/i), {
      target: { value: STAFF_ASSIGNMENT_ID },
    });
    fireEvent.click(screen.getByRole("button", { name: /assign hod/i }));

    await waitFor(() =>
      expect(api.put).toHaveBeenCalledWith(`/tenants/${TENANT_ID}/departments/${DEPT_ID}/hod`, {
        staffAssignmentId: STAFF_ASSIGNMENT_ID,
      }),
    );
    expect(await screen.findByText(/HOD assignment recorded/i)).toBeInTheDocument();
  });

  it("surfaces a 403 from MENTOR_ASSIGN as a permission-denied banner, not a generic error", async () => {
    (api.put as ReturnType<typeof vi.fn>).mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 403, data: { error: "Not authorized for MENTOR_ASSIGN." } },
    });
    renderWithTenant(<MentorAssignmentPage />);
    fireEvent.change(screen.getByLabelText(/Section ID/i), { target: { value: DEPT_ID } });
    fireEvent.change(screen.getByLabelText(/Staff assignment ID \(new mentor\)/i), {
      target: { value: STAFF_ASSIGNMENT_ID },
    });
    fireEvent.click(screen.getByRole("button", { name: /assign mentor/i }));

    expect(await screen.findByText("Permission denied")).toBeInTheDocument();
    expect(await screen.findByText(/Not authorized for MENTOR_ASSIGN/i)).toBeInTheDocument();
  });

  it("labels CLASS_TEACHER_ASSIGN against sections, not classes (OD-24)", () => {
    renderWithTenant(<ClassTeacherAssignmentPage />);
    expect(screen.getByText(/This assigns a section, not a class/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Section ID/i)).toBeInTheDocument();
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
});
