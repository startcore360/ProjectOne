import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import authReducer, { setUser } from "../../../store/slices/authSlice";
import tenantPermissionsReducer, { setTenantPermissions } from "../../../store/slices/tenantPermissionsSlice";
import { staffService, type StaffProfile } from "../api/staffService";
import StaffRecordsPage from "./StaffRecordsPage";

vi.mock("../api/staffService", () => ({
  staffService: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    listAssignments: vi.fn(),
    addAssignment: vi.fn(),
    endAssignment: vi.fn(),
    listDocuments: vi.fn(),
    attachDocument: vi.fn(),
    verifyDocument: vi.fn(),
    listServiceRecords: vi.fn(),
    addServiceRecord: vi.fn(),
    uploadDocument: vi.fn(),
  },
}));

const staffMember: StaffProfile = {
  id: "staff-1",
  userAccountId: null,
  staffCode: "ST-004",
  fullName: "Taylor Reed",
  employmentStatus: "ACTIVE",
  version: 1,
};

function renderWithActions(allowedActions: string[] = ["STAFF_RECORD_VIEW"]) {
  const store = configureStore({ reducer: { auth: authReducer, tenantPermissions: tenantPermissionsReducer } });
  store.dispatch(setUser({
    id: "u-1",
    displayName: "HR Manager",
    tenantId: "tenant-1",
    userType: "STAFF",
    platformRole: null,
    roleCode: "HR_MANAGER",
    roleName: "HR Manager",
    photoUrl: null,
    mustChangePassword: false,
  }));
  store.dispatch(setTenantPermissions({
    tenantId: "tenant-1",
    userAccountId: "u-1",
    activeRoleCode: "HR_MANAGER",
    activeRoleName: "HR Manager",
    allowedActions,
  }));
  return render(<Provider store={store}><StaffRecordsPage /></Provider>);
}

describe("StaffRecordsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads staff records and renders the scoped directory", async () => {
    vi.mocked(staffService.list).mockResolvedValue({
      items: [staffMember],
      page: { limit: 100, nextCursor: null, hasMore: false, totalCount: 1 },
    });

    renderWithActions();

    expect(await screen.findByText("Taylor Reed")).toBeInTheDocument();
    expect(screen.getByText("ST-004 · Active")).toBeInTheDocument();
    expect(screen.queryByText("No staff records found")).not.toBeInTheDocument();
  });

  it("shows a retryable error rather than a misleading empty directory when loading fails", async () => {
    vi.mocked(staffService.list).mockRejectedValue(new Error("Directory unavailable"));

    renderWithActions();

    expect(await screen.findByText("Could not load staff")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
    expect(screen.queryByText("No staff records found")).not.toBeInTheDocument();
  });

  it("shows profile creation only to a role with staff-management permission", async () => {
    vi.mocked(staffService.list).mockResolvedValue({
      items: [],
      page: { limit: 100, nextCursor: null, hasMore: false, totalCount: 0 },
    });

    const { rerender } = renderWithActions(["STAFF_RECORD_VIEW"]);
    await waitFor(() => expect(staffService.list).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: "Add staff member" })).not.toBeInTheDocument();

    // The same route can expose management actions when the server supplies the management grant.
    const store = configureStore({ reducer: { auth: authReducer, tenantPermissions: tenantPermissionsReducer } });
    store.dispatch(setUser({
      id: "u-1",
      displayName: "HR Manager",
      tenantId: "tenant-1",
      userType: "STAFF",
      platformRole: null,
      roleCode: "HR_MANAGER",
      roleName: "HR Manager",
      photoUrl: null,
      mustChangePassword: false,
    }));
    store.dispatch(setTenantPermissions({
      tenantId: "tenant-1", userAccountId: "u-1", activeRoleCode: "HR_MANAGER",
      activeRoleName: "HR Manager", allowedActions: ["STAFF_RECORD_VIEW", "STAFF_RECORD_MANAGE"],
    }));
    rerender(<Provider store={store}><StaffRecordsPage /></Provider>);
    expect(screen.getByRole("button", { name: "Add staff member" })).toBeInTheDocument();
  });
});
