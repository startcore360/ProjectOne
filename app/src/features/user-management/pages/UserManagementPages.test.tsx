import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import authReducer, { setUser } from "../../../store/slices/authSlice";
import { userManagementService } from "../api/userManagementService";
import { GuardianManagementPanel, StudentStatusPanel, UserDetailPage, UserListPage } from "./UserManagementPages";
import { academicService } from "../../institution-admin/api/academicService";
import { guardianService } from "../api/guardianService";

vi.mock("../../institution-admin/api/academicService", () => ({
  academicService: {
    changeStudentStatus: vi.fn(),
  },
}));

vi.mock("../api/guardianService", () => ({
  guardianService: {
    listGuardianLinks: vi.fn(),
    linkGuardian: vi.fn(),
    requestGuardianChange: vi.fn(),
    decideGuardianChange: vi.fn(),
  },
}));

vi.mock("../api/userManagementService", () => ({
  userManagementService: {
    listUsers: vi.fn(),
    getUser: vi.fn(),
    updateUser: vi.fn(),
    deactivateUser: vi.fn(),
    resetPassword: vi.fn(),
    unlockUser: vi.fn(),
    checkCreateUserAuthorization: vi.fn(),
    createUser: vi.fn(),
    getImportTemplate: vi.fn(),
    createUserImport: vi.fn(),
    getImportJob: vi.fn(),
    listImportErrors: vi.fn(),
    startImport: vi.fn(),
    createTopUser: vi.fn(),
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
      roleCode: null,
      roleName: null,
      photoUrl: null,
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

    fireEvent.click(screen.getByRole("button", { name: /^Load users$/i }));

    await waitFor(() => expect(screen.getByText("Avery Admin")).toBeInTheDocument());
    expect(userManagementService.listUsers).toHaveBeenCalledWith("tenant-1", { scopeType: "TENANT" });
  });

  it("shows a permission-denied state on a 403", async () => {
    vi.mocked(userManagementService.listUsers).mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 403, data: {} },
    });
    renderList();

    fireEvent.click(screen.getByRole("button", { name: /^Load users$/i }));

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

  it("opens create user modal, collects info with DPDP notice, provisions user, and shows temporary password", async () => {
    vi.mocked(userManagementService.createUser).mockResolvedValueOnce({
      data: {
        userAccountId: "new-user-1",
        displayName: "Dr. Anita Sharma",
        email: "anita.sharma@northbridge.edu.in",
        mobile: "+919876543210",
        userType: "STAFF",
        status: "PENDING_ACTIVATION",
        temporaryPassword: "temp-pwd-secure-123",
        initialRoleCode: "TEACHER",
        message: "User created and Keycloak account provisioned successfully.",
      },
    } as never);

    renderList();

    fireEvent.click(screen.getByRole("button", { name: /^Create user$/i }));
    expect(screen.getByRole("heading", { name: "Create user account" })).toBeInTheDocument();
    expect(screen.getByText(/DPDP Act 2023 & UIDAI Guidelines/i)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Full name/i), { target: { value: "Dr. Anita Sharma" } });
    fireEvent.change(screen.getByLabelText(/Email address/i), { target: { value: "anita.sharma@northbridge.edu.in" } });
    fireEvent.change(screen.getByLabelText(/Mobile number/i), { target: { value: "+919876543210" } });
    fireEvent.change(screen.getByLabelText(/Aadhaar \/ National ID/i), { target: { value: "5678" } });

    const modal = screen.getByRole("dialog", { name: "Create user account" });
    fireEvent.click(within(modal).getByRole("button", { name: /^Create user$/i }));

    await waitFor(() => expect(screen.getByRole("heading", { name: "User created successfully" })).toBeInTheDocument());
    expect(screen.getByDisplayValue("temp-pwd-secure-123")).toBeInTheDocument();
    expect(userManagementService.createUser).toHaveBeenCalledWith("tenant-1", expect.objectContaining({
      displayName: "Dr. Anita Sharma",
      email: "anita.sharma@northbridge.edu.in",
      mobile: "+919876543210",
      nationalIdLast4: "5678",
      scopeType: "TENANT",
    }));

    fireEvent.click(screen.getByRole("button", { name: /I've copied it, close/i }));
    expect(screen.queryByRole("heading", { name: "User created successfully" })).not.toBeInTheDocument();
  });

  it("opens bulk upload modal, downloads template, pre-validates CSV with errors, and allows good rows import (Story #10: USER_BULK_UPLOAD)", async () => {
    vi.mocked(userManagementService.getImportTemplate).mockResolvedValueOnce({
      data: {
        importKind: "USERS",
        filename: "template.csv",
        headers: ["email", "fullName"],
        sampleRows: [],
        csvTemplate: "email,fullName,userType\ntest@test.com,Test,STAFF\n",
      },
    } as never);

    vi.mocked(userManagementService.createUserImport).mockResolvedValueOnce({
      data: {
        importJobId: "job-1",
        status: "VALIDATED",
        importKind: "USERS",
        totalRows: 2,
        validRows: 1,
        errorRows: 1,
        createdAt: "2026-11-06T00:00:00Z",
      },
    } as never);

    vi.mocked(userManagementService.listImportErrors).mockResolvedValueOnce({
      data: [
        {
          id: "err-1",
          rowNumber: 2,
          fieldName: "nationalIdLast4",
          errorCode: "UIDAI_MASKED_LAST4_REQUIRED",
          errorMessage: "Unmasked 12-digit Aadhaar",
          rowData: "{}",
        },
      ],
    } as never);

    vi.mocked(userManagementService.startImport).mockResolvedValueOnce({
      data: {
        importJobId: "job-1",
        status: "COMPLETED",
        importKind: "USERS",
        totalRows: 2,
        validRows: 1,
        errorRows: 1,
        importMode: "GOOD_ROWS_ONLY",
        createdAt: "2026-11-06T00:00:00Z",
      },
    } as never);

    renderList();

    // Click Bulk import users
    fireEvent.click(screen.getByRole("button", { name: /Bulk import users/i }));
    expect(screen.getByRole("heading", { name: "Bulk upload users" })).toBeInTheDocument();
    expect(screen.getByText(/Upload supported user accounts with pre-validation and row-level error tracking/i)).toBeInTheDocument();

    // Download template
    fireEvent.click(screen.getByRole("button", { name: /Download CSV Template/i }));
    expect(userManagementService.getImportTemplate).toHaveBeenCalledWith("tenant-1", "USERS");

    // Enter CSV text
    const textarea = screen.getByLabelText(/Or paste CSV text directly:/i);
    fireEvent.change(textarea, {
      target: {
        value: "email,fullName,userType,nationalIdLast4\nclean@test.com,Clean,STAFF,1234\nbad@test.com,Bad,STAFF,123456789012\n",
      },
    });

    // Submit for pre-validation
    fireEvent.click(screen.getByRole("button", { name: /Pre-validate CSV \(Rule MD-27\)/i }));

    await waitFor(() => {
      expect(userManagementService.createUserImport).toHaveBeenCalledWith(
        "tenant-1",
        expect.stringContaining("clean@test.com"),
        undefined
      );
      expect(screen.getByText("Pre-validation Results [Rule MD-27]")).toBeInTheDocument();
      expect(screen.getByText("UIDAI_MASKED_LAST4_REQUIRED")).toBeInTheDocument();
      expect(screen.getAllByText(/Unmasked 12-digit Aadhaar/i).length).toBeGreaterThanOrEqual(1);
    });

    // Verify "Import clean only" is disabled because errorRows > 0
    const cleanOnlyBtn = screen.getByRole("button", { name: /Import clean only/i });
    expect(cleanOnlyBtn).toBeDisabled();

    // Click "Import 1 good row"
    const goodRowsBtn = screen.getByRole("button", { name: /Import 1 good row/i });
    expect(goodRowsBtn).not.toBeDisabled();
    fireEvent.click(goodRowsBtn);

    await waitFor(() => {
      expect(userManagementService.startImport).toHaveBeenCalledWith("tenant-1", "job-1", "GOOD_ROWS_ONLY");
      expect(screen.getByText(/Import completed successfully under GOOD_ROWS_ONLY mode!/i)).toBeInTheDocument();
    });
  });

  it("opens governance user dialog, selects role, creates top user and displays temporary password with MFA notice (Story #23: TENANT_TOP_USERS_CREATE)", async () => {
    vi.mocked(userManagementService.createTopUser).mockResolvedValueOnce({
      data: {
        userAccountId: "top-user-1",
        displayName: "Prof. Suresh Chandra",
        email: "vc@northbridge.edu.in",
        mobile: "+919876543210",
        roleCode: "VICE_CHANCELLOR",
        userType: "STAFF",
        status: "PENDING_ACTIVATION",
        temporaryPassword: "test-only-password",
        twoStepLoginRequired: true,
        message: "Executive account created successfully.",
      },
    } as never);
    vi.mocked(userManagementService.listUsers).mockResolvedValueOnce({
      data: { users: [] },
    } as never);

    renderList();

    // Click "Create governance user"
    fireEvent.click(screen.getByRole("button", { name: /Create governance user/i }));

    const dialog = screen.getByRole("dialog", { name: /Create governance \/ top user/i });
    expect(dialog).toBeInTheDocument();
    expect(screen.getByText(/Two-step login \(MFA\) will be enforced/i)).toBeInTheDocument();

    // Fill in fields
    fireEvent.change(screen.getByLabelText(/Governance Role/i), { target: { value: "VICE_CHANCELLOR" } });
    fireEvent.change(screen.getByLabelText(/Full Name/i), { target: { value: "Prof. Suresh Chandra" } });
    fireEvent.change(screen.getByLabelText(/Email Address/i), { target: { value: "vc@northbridge.edu.in" } });
    fireEvent.change(screen.getByLabelText(/Mobile Number/i), { target: { value: "+919876543210" } });

    // Submit
    fireEvent.click(screen.getByRole("button", { name: /Create governance account/i }));

    await waitFor(() => {
      expect(userManagementService.createTopUser).toHaveBeenCalledWith("tenant-1", {
        roleCode: "VICE_CHANCELLOR",
        displayName: "Prof. Suresh Chandra",
        email: "vc@northbridge.edu.in",
        mobile: "+919876543210",
      });
      expect(screen.getByDisplayValue("test-only-password")).toBeInTheDocument();
      expect(screen.getByText(/Security Invariant \[Rule F-12\]/i)).toBeInTheDocument();
    });

    // Close modal
    fireEvent.click(screen.getByRole("button", { name: /I've copied it, close/i }));
    expect(screen.queryByDisplayValue("test-only-password")).not.toBeInTheDocument();
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

  it("confirms and deactivates a user, updating status to DEACTIVATED (Story #12: USER_DEACTIVATE)", async () => {
    vi.mocked(userManagementService.getUser).mockResolvedValueOnce({ data: sampleUser } as never);
    vi.mocked(userManagementService.deactivateUser).mockResolvedValueOnce({
      data: { actionCode: "USER_DEACTIVATE", reasonCode: "ALLOWED", message: "User deactivated (soft delete; the row was never removed)." },
    } as never);
    renderDetail("/tenants/users/ua-1?scopeType=TENANT");
    await waitFor(() => expect(screen.getByRole("heading", { name: "Avery Admin" })).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: "inst-1" } });
    fireEvent.click(screen.getByRole("button", { name: /^Deactivate user$/i }));

    const confirmDialog = screen.getByRole("dialog", { name: /Deactivate this user\?/i });
    expect(confirmDialog).toBeInTheDocument();
    fireEvent.click(within(confirmDialog).getByRole("button", { name: /^Deactivate$/i }));

    await waitFor(() => {
      expect(userManagementService.deactivateUser).toHaveBeenCalledWith("tenant-1", "ua-1", "inst-1");
      expect(screen.getByText(/User deactivated \(soft delete; the row was never removed\)\./i)).toBeInTheDocument();
      expect(screen.getByText(/^DEACTIVATED$/)).toBeInTheDocument();
    });
  });

  it("confirms and unlocks a locked account (Story #13: USER_PASSWORD_RESET_UNLOCK)", async () => {
    vi.mocked(userManagementService.getUser).mockResolvedValueOnce({ data: sampleUser } as never);
    vi.mocked(userManagementService.unlockUser).mockResolvedValueOnce({
      data: { actionCode: "USER_PASSWORD_RESET_UNLOCK", reasonCode: "ALLOWED", message: "User unlocked: Keycloak credential reset and brute-force lockout cleared." },
    } as never);
    renderDetail("/tenants/users/ua-1?scopeType=TENANT");
    await waitFor(() => expect(screen.getByRole("heading", { name: "Avery Admin" })).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: "inst-1" } });
    fireEvent.click(screen.getByRole("button", { name: /^Unlock account$/i }));

    const confirmDialog = screen.getByRole("dialog", { name: /Unlock this user's account\?/i });
    expect(confirmDialog).toBeInTheDocument();
    fireEvent.click(within(confirmDialog).getByRole("button", { name: /^Unlock$/i }));

    await waitFor(() => {
      expect(userManagementService.unlockUser).toHaveBeenCalledWith("tenant-1", "ua-1", "inst-1");
      expect(screen.getByText(/User unlocked: Keycloak credential reset and brute-force lockout cleared\./i)).toBeInTheDocument();
    });
  });

  it("links guardian to student via modal (Story #17: GUARDIAN_LINK / Rules WF-21, WF-22, WF-24)", async () => {
    vi.mocked(guardianService.listGuardianLinks).mockResolvedValue([]);
    vi.mocked(guardianService.linkGuardian).mockResolvedValue({
      studentGuardianLinkId: "link-1",
      tenantId: "tenant-1",
      studentProfileId: "student-1",
      guardianProfileId: "prof-1",
      guardianName: "Ramesh Kumar",
      guardianMobile: "+919876543210",
      relationship: "FATHER",
      isPrimary: true,
      status: "PENDING_ACTIVATION",
      createdAt: "2026-11-06T00:00:00Z",
      rowVersion: 1,
    });

    const store = storeWithTenantUser("tenant-1");
    render(
      <Provider store={store}>
        <GuardianManagementPanel tenantId="tenant-1" studentId="student-1" institutionId="inst-1" />
      </Provider>
    );

    const openBtn = screen.getByRole("button", { name: /\+ Link Guardian/i });
    fireEvent.click(openBtn);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /Link Parent or Guardian/i })).toBeInTheDocument();
    });

    const nameInput = screen.getByLabelText(/Full Name \*/i);
    const mobileInput = screen.getByLabelText(/Mobile Number/i);

    fireEvent.change(nameInput, { target: { value: "Ramesh Kumar" } });
    fireEvent.change(mobileInput, { target: { value: "+919876543210" } });

    const submitBtn = screen.getByRole("button", { name: /^Link Guardian$/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(guardianService.linkGuardian).toHaveBeenCalledWith(
        "tenant-1",
        "student-1",
        expect.objectContaining({
          fullName: "Ramesh Kumar",
          mobile: "+919876543210",
          relationship: "FATHER",
          isPrimary: true,
        }),
        "inst-1"
      );
      expect(screen.getByText(/Guardian linked successfully \[GUARDIAN_LINK/i)).toBeInTheDocument();
    });
  });

  it("requests guardian change with reason via modal (Story #18: GUARDIAN_CHANGE_REQUEST / Rule WF-23)", async () => {
    vi.mocked(guardianService.listGuardianLinks).mockResolvedValue([
      {
        studentGuardianLinkId: "link-1",
        tenantId: "tenant-1",
        studentProfileId: "student-1",
        guardianProfileId: "prof-1",
        guardianName: "Ramesh Kumar",
        guardianMobile: "+919876543210",
        relationship: "FATHER",
        isPrimary: true,
        status: "ACTIVE",
        createdAt: "2026-11-06T00:00:00Z",
        rowVersion: 1,
      },
    ]);

    vi.mocked(guardianService.requestGuardianChange).mockResolvedValue({
      studentGuardianLinkId: "link-1",
      tenantId: "tenant-1",
      studentProfileId: "student-1",
      guardianProfileId: "prof-1",
      guardianName: "Ramesh Kumar",
      guardianMobile: "+919876543210",
      relationship: "FATHER",
      isPrimary: true,
      status: "ACTIVE",
      changeApprovalRequestId: "appr-1",
      blockReason: "Court custody order updated",
      createdAt: "2026-11-06T00:00:00Z",
      rowVersion: 2,
    });

    const store = storeWithTenantUser("tenant-1");
    render(
      <Provider store={store}>
        <GuardianManagementPanel tenantId="tenant-1" studentId="student-1" institutionId="inst-1" />
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText("Ramesh Kumar")).toBeInTheDocument();
    });

    const reqBtn = screen.getByRole("button", { name: /Request Change/i });
    fireEvent.click(reqBtn);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /Request Change or Block/i })).toBeInTheDocument();
    });

    const reasonInput = screen.getByLabelText(/Reason for Change \/ Block \*/i);
    fireEvent.change(reasonInput, { target: { value: "Court custody order updated" } });

    const submitBtn = screen.getByRole("button", { name: /Submit to Principal/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(guardianService.requestGuardianChange).toHaveBeenCalledWith(
        "tenant-1",
        "link-1",
        { reason: "Court custody order updated" },
        "inst-1"
      );
      expect(screen.getByText(/Guardian change requested with reason/i)).toBeInTheDocument();
    });
  });

  it("reviews guardian change decision and approves block (Story #19: GUARDIAN_CHANGE_APPROVE / Rule WF-25)", async () => {
    vi.mocked(guardianService.listGuardianLinks).mockResolvedValue([
      {
        studentGuardianLinkId: "link-1",
        tenantId: "tenant-1",
        studentProfileId: "student-1",
        guardianProfileId: "prof-1",
        guardianName: "Ramesh Kumar",
        guardianMobile: "+919876543210",
        relationship: "FATHER",
        isPrimary: true,
        status: "ACTIVE",
        changeApprovalRequestId: "appr-1",
        blockReason: "Court custody order updated",
        createdAt: "2026-11-06T00:00:00Z",
        rowVersion: 2,
      },
    ]);

    vi.mocked(guardianService.decideGuardianChange).mockResolvedValue({
      studentGuardianLinkId: "link-1",
      tenantId: "tenant-1",
      studentProfileId: "student-1",
      guardianProfileId: "prof-1",
      guardianName: "Ramesh Kumar",
      guardianMobile: "+919876543210",
      relationship: "FATHER",
      isPrimary: false,
      status: "BLOCKED",
      blockedAt: "2026-11-07T00:00:00Z",
      createdAt: "2026-11-06T00:00:00Z",
      rowVersion: 3,
    });

    const store = storeWithTenantUser("tenant-1");
    render(
      <Provider store={store}>
        <GuardianManagementPanel tenantId="tenant-1" studentId="student-1" institutionId="inst-1" />
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText("Change Pending")).toBeInTheDocument();
    });

    const reviewBtn = screen.getByRole("button", { name: /Review Decision/i });
    fireEvent.click(reviewBtn);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /Review Guardian Change/i })).toBeInTheDocument();
    });

    const approveBtn = screen.getByRole("button", { name: /Approve & Block/i });
    fireEvent.click(approveBtn);

    await waitFor(() => {
      expect(guardianService.decideGuardianChange).toHaveBeenCalledWith(
        "tenant-1",
        "link-1",
        { approved: true },
        "inst-1"
      );
      expect(screen.getByText(/Guardian change approved\. Old guardian access blocked immediately/i)).toBeInTheDocument();
    });
  });
  it("changes student academic status with statutory reason (Story #41: STUDENT_STATUS_CHANGE / Rule AT-08)", async () => {
    (academicService.changeStudentStatus as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      studentId: "student-1",
      status: "LEAVE_OF_ABSENCE",
      statusReason: "Medical leave approved by Board",
      statusChangedAt: "2026-11-07T00:00:00Z",
    });

    const store = storeWithTenantUser("tenant-1");
    render(
      <Provider store={store}>
        <StudentStatusPanel tenantId="tenant-1" studentId="student-1" />
      </Provider>
    );

    expect(screen.getByText("Not available from the current student-status endpoint")).toBeInTheDocument();

    const changeBtn = screen.getByRole("button", { name: /Change Student Status/i });
    fireEvent.click(changeBtn);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /Change Student Academic Status/i })).toBeInTheDocument();
    });

    const reasonInput = screen.getByLabelText(/Statutory Justification \/ Reason \*/i);
    fireEvent.change(reasonInput, { target: { value: "Medical leave approved by Board" } });

    const updateBtn = screen.getByRole("button", { name: /Update Status/i });
    fireEvent.click(updateBtn);

    await waitFor(() => {
      expect(academicService.changeStudentStatus).toHaveBeenCalledWith(
        "tenant-1",
        "student-1",
        {
          status: "LEAVE_OF_ABSENCE",
          reason: "Medical leave approved by Board",
        }
      );
      expect(screen.getByText(/Student status successfully changed to LEAVE_OF_ABSENCE/i)).toBeInTheDocument();
      expect(screen.getByText("LEAVE_OF_ABSENCE")).toBeInTheDocument();
    });
  });
});
