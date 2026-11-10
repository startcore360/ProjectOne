import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import authReducer, { setUser } from "../../store/slices/authSlice";
import { meService, type UserRoleOption } from "../../features/profile/api/meService";
import { RoleSwitcher } from "./RoleSwitcher";

vi.mock("../../features/profile/api/meService", () => ({
  meService: {
    getMe: vi.fn(),
    switchActiveRole: vi.fn(),
  },
}));

function storeWithUser(roleCode: string | null = "PROFESSOR", roleName: string | null = "Professor") {
  const store = configureStore({ reducer: { auth: authReducer } });
  store.dispatch(
    setUser({
      id: "u1",
      displayName: "Dr. Vikram Sarabhai",
      tenantId: "tenant-1",
      userType: "STAFF",
      platformRole: null,
      roleCode,
      roleName,
      photoUrl: null,
      mustChangePassword: false,
    })
  );
  return store;
}

const mockRoles: UserRoleOption[] = [
  {
    userRoleAssignmentId: "as-1",
    roleDefinitionId: "rd-1",
    roleCode: "PROFESSOR",
    roleName: "Professor",
    scopeType: "INSTITUTION",
    institutionId: "inst-1",
    institutionName: "Northbridge College of Science",
    currentActive: true,
  },
  {
    userRoleAssignmentId: "as-2",
    roleDefinitionId: "rd-2",
    roleCode: "HEAD_OF_DEPARTMENT",
    roleName: "Head of Department",
    scopeType: "DEPARTMENT",
    institutionId: "inst-1",
    departmentId: "dept-1",
    departmentName: "Physics",
    currentActive: false,
  },
];

describe("RoleSwitcher component (Story #6: ROLE_SWITCH)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders a single role badge when the user has only 1 role", async () => {
    vi.mocked(meService.getMe).mockResolvedValueOnce({
      data: {
        userAccountId: "u1",
        tenantId: "tenant-1",
        email: "vikram@northbridge.edu.in",
        displayName: "Dr. Vikram Sarabhai",
        userType: "STAFF",
        currentRoleCode: "PROFESSOR",
        currentRoleName: "Professor",
        currentAssignmentId: "as-1",
        roles: [mockRoles[0]],
      },
    } as never);

    const store = storeWithUser();
    render(
      <Provider store={store}>
        <MemoryRouter>
          <RoleSwitcher />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText("Professor")).toBeInTheDocument();
    });
  });

  it("renders a dropdown switcher when user holds multiple roles and switches active role upon selection", async () => {
    vi.mocked(meService.getMe).mockResolvedValueOnce({
      data: {
        userAccountId: "u1",
        tenantId: "tenant-1",
        email: "vikram@northbridge.edu.in",
        displayName: "Dr. Vikram Sarabhai",
        userType: "STAFF",
        currentRoleCode: "PROFESSOR",
        currentRoleName: "Professor",
        currentAssignmentId: "as-1",
        roles: mockRoles,
      },
    } as never);

    vi.mocked(meService.switchActiveRole).mockResolvedValueOnce({
      data: {
        assignmentId: "as-2",
        roleCode: "HEAD_OF_DEPARTMENT",
        roleName: "Head of Department",
        scopeType: "DEPARTMENT",
        institutionId: "inst-1",
        departmentId: "dept-1",
        message: "Active role switched successfully.",
      },
    } as never);

    const store = storeWithUser();
    // Start on some other page, to prove a successful switch always lands on the dashboard.
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={["/university/setup/term-lock"]}>
          <RoleSwitcher />
          <Routes>
            <Route path="/dashboard" element={<p>Dashboard page</p>} />
            <Route path="*" element={<p>Some other page</p>} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByLabelText("Switch active role")).toBeInTheDocument();
    });
    expect(screen.getByText("Some other page")).toBeInTheDocument();

    const select = screen.getByLabelText("Switch active role") as HTMLSelectElement;
    expect(select.options.length).toBe(2);

    // Switch to Head of Department
    fireEvent.change(select, { target: { value: "as-2" } });

    await waitFor(() => {
      expect(meService.switchActiveRole).toHaveBeenCalledWith("as-2");
      expect(store.getState().auth.user?.roleCode).toBe("HEAD_OF_DEPARTMENT");
      expect(store.getState().auth.user?.roleName).toBe("Head of Department");
      expect(screen.getByText("Dashboard page")).toBeInTheDocument();
    });
  });

  it("stays on the current page when the role switch fails", async () => {
    vi.mocked(meService.getMe).mockResolvedValueOnce({
      data: { currentAssignmentId: "as-1", roles: mockRoles },
    } as never);
    vi.mocked(meService.switchActiveRole).mockRejectedValueOnce(new Error("denied"));

    const store = storeWithUser();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={["/sessions"]}>
          <RoleSwitcher />
          <Routes>
            <Route path="/dashboard" element={<p>Dashboard page</p>} />
            <Route path="*" element={<p>Some other page</p>} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByLabelText("Switch active role")).toBeInTheDocument();
    });
    fireEvent.change(screen.getByLabelText("Switch active role"), { target: { value: "as-2" } });

    await waitFor(() => {
      expect(meService.switchActiveRole).toHaveBeenCalledWith("as-2");
    });
    expect(screen.getByText("Some other page")).toBeInTheDocument();
    expect(store.getState().auth.user?.roleCode).toBe("PROFESSOR");
  });
});
