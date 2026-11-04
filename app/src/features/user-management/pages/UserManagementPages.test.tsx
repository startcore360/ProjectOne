import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import authReducer, { setUser } from "../../../store/slices/authSlice";
import { userManagementService } from "../api/userManagementService";
import { UserDetailPage, UserListPage } from "./UserManagementPages";

vi.mock("../api/userManagementService", () => ({
  userManagementService: {
    listUsers: vi.fn(),
    getUser: vi.fn(),
    updateUser: vi.fn(),
    deactivateUser: vi.fn(),
    resetPassword: vi.fn(),
    unlockUser: vi.fn(),
    checkCreateUserAuthorization: vi.fn(),
  },
}));

function storeWithTenantUser(tenantId: string | null = "tenant-1") {
  const store = configureStore({ reducer: { auth: authReducer } });
  store.dispatch(
    setUser({
      id: "u1",
      displayName: "Jordan Lee",
      tenantId,
      userType: "STAFF",
      platformRole: null,
      mustChangePassword: false,
    }),
  );
  return store;
}

function renderList(tenantId: string | null = "tenant-1") {
  return render(
    <Provider store={storeWithTenantUser(tenantId)}>
      <MemoryRouter>
        <UserListPage />
      </MemoryRouter>
    </Provider>,
  );
}

function renderDetail(initialPath: string, tenantId: string | null = "tenant-1") {
  return render(
    <Provider store={storeWithTenantUser(tenantId)}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/tenants/users/:userId" element={<UserDetailPage />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}

const sampleUser = {
  userAccountId: "ua-1",
  userType: "STAFF" as const,
  displayName: "Avery Admin",
  email: "avery@example.edu",
  mobile: "+15550001111",
  status: "ACTIVE" as const,
};

describe("UserListPage", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("shows a not-available state for a non-tenant (platform) account", () => {
    renderList(null);
    expect(screen.getByText(/This screen is for tenant accounts/i)).toBeInTheDocument();
  });

  it("shows an empty prompt before any scope has been loaded", () => {
    renderList();
    expect(screen.getByText(/No users loaded yet/i)).toBeInTheDocument();
  });

  it("loads and shows users for the tenant scope", async () => {
    vi.mocked(userManagementService.listUsers).mockResolvedValueOnce({
      data: { users: [sampleUser] },
    } as never);
    renderList();

    fireEvent.click(screen.getByRole("button", { name: /Load users/i }));

    await waitFor(() => expect(screen.getByText("Avery Admin")).toBeInTheDocument());
    expect(userManagementService.listUsers).toHaveBeenCalledWith("tenant-1", { scopeType: "TENANT" });
  });

  it("shows a permission-denied state on a 403", async () => {
    vi.mocked(userManagementService.listUsers).mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 403, data: {} },
    });
    renderList();

    fireEvent.click(screen.getByRole("button", { name: /Load users/i }));

    await waitFor(() => expect(screen.getByText("Permission denied")).toBeInTheDocument());
  });

  it("opens the create-user authorization check dialog without claiming to create an account", async () => {
    vi.mocked(userManagementService.checkCreateUserAuthorization).mockResolvedValueOnce({
      data: { actionCode: "USER_CREATE", reasonCode: "ALLOWED", message: "Authorized to create a user in this scope." },
    } as never);
    renderList();

    fireEvent.click(screen.getByRole("button", { name: /Check if I can create a user/i }));
    expect(screen.getByText(/does not create an account/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Check authorization/i }));
    await waitFor(() => expect(screen.getByText(/Authorized to create a user/i)).toBeInTheDocument());
  });
});

describe("UserDetailPage", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("loads a user immediately when the scope is already supplied via the URL", async () => {
    vi.mocked(userManagementService.getUser).mockResolvedValueOnce({ data: sampleUser } as never);
    renderDetail("/tenants/users/ua-1?scopeType=TENANT");

    await waitFor(() => expect(screen.getByRole("heading", { name: "Avery Admin" })).toBeInTheDocument());
    expect(userManagementService.getUser).toHaveBeenCalledWith("tenant-1", "ua-1", { scopeType: "TENANT", institutionId: "", departmentId: "" });
  });

  it("asks for a scope first when none was supplied in the URL", () => {
    renderDetail("/tenants/users/ua-1");
    expect(screen.getByText(/Choose a scope/i)).toBeInTheDocument();
    expect(userManagementService.getUser).not.toHaveBeenCalled();
  });

  it("disables editing and the deactivate action for an already-deactivated user", async () => {
    vi.mocked(userManagementService.getUser).mockResolvedValueOnce({
      data: { ...sampleUser, status: "DEACTIVATED" },
    } as never);
    renderDetail("/tenants/users/ua-1?scopeType=TENANT");

    await waitFor(() => expect(screen.getByText(/no longer be edited/i)).toBeInTheDocument());
    expect(screen.getByLabelText(/Display name/i)).toBeDisabled();
  });

  it("shows the one-time temporary password dialog after a successful reset, then clears it", async () => {
    vi.mocked(userManagementService.getUser).mockResolvedValueOnce({ data: sampleUser } as never);
    vi.mocked(userManagementService.resetPassword).mockResolvedValueOnce({
      data: { actionCode: "USER_PASSWORD_RESET_UNLOCK", reasonCode: "ALLOWED", message: "Password reset.", temporaryPassword: "one-time-secret" },
    } as never);
    renderDetail("/tenants/users/ua-1?scopeType=TENANT");
    await waitFor(() => expect(screen.getByRole("heading", { name: "Avery Admin" })).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: "inst-1" } });
    fireEvent.click(screen.getByRole("button", { name: /^Reset password$/i }));

    const confirmDialog = screen.getByRole("dialog", { name: /Reset this user's password\?/i });
    fireEvent.click(within(confirmDialog).getByRole("button", { name: /^Reset password$/i }));

    await waitFor(() => expect(screen.getByDisplayValue("one-time-secret")).toBeInTheDocument());
    expect(userManagementService.resetPassword).toHaveBeenCalledWith("tenant-1", "ua-1", "inst-1");

    fireEvent.click(screen.getByRole("button", { name: /I've copied it, close/i }));
    expect(screen.queryByDisplayValue("one-time-secret")).not.toBeInTheDocument();
  });
});
