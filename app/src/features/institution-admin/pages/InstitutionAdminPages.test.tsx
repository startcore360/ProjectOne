import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { describe, expect, it, vi, beforeEach } from "vitest";
import authReducer, { setUser } from "../../../store/slices/authSlice";
import tenantPermissionsReducer, { setTenantPermissions } from "../../../store/slices/tenantPermissionsSlice";
import { institutionService, type InstitutionItem } from "../api/institutionService";
import { leaveApproverService } from "../api/leaveApproverService";
import { roleAssignmentService, type RoleDefinitionItem } from "../api/roleAssignmentService";
import { rolldownService } from "../api/rolldownService";
import {
  InstitutionUsersPage,
  InstitutionsPage,
  MissingRolesPage,
  RolesPermissionsPage,
} from "./InstitutionAdminPages";

vi.mock("../api/roleAssignmentService", () => ({
  roleAssignmentService: {
    listRoles: vi.fn(),
    createCustomRole: vi.fn(),
    retireCustomRole: vi.fn(),
    listUserRoleAssignments: vi.fn(),
    assignRole: vi.fn(),
    endRoleAssignment: vi.fn(),
  },
}));

vi.mock("../api/institutionService", () => ({
  institutionService: {
    listInstitutions: vi.fn(),
    createDraftInstitution: vi.fn(),
    activateInstitution: vi.fn(),
    requestBlock: vi.fn(),
    decideBlock: vi.fn(),
  },
}));

vi.mock("../api/leaveApproverService", () => ({
  leaveApproverService: {
    getGoLiveCheck: vi.fn(),
    setPrincipalLeaveApprover: vi.fn(),
    getPrincipalLeaveApprover: vi.fn(),
    setVcLeaveApprover: vi.fn(),
    getVcLeaveApprover: vi.fn(),
  },
}));

vi.mock("../api/rolldownService", () => ({
  rolldownService: {
    getRolldownMap: vi.fn(),
    getOverrides: vi.fn(),
    createOverride: vi.fn(),
    revokeOverride: vi.fn(),
  },
}));

const mockInstitutions: InstitutionItem[] = [
  {
    tenantId: "tenant-1",
    institutionId: "inst-1",
    institutionType: "COLLEGE",
    institutionCode: "NBC-01",
    institutionName: "Northbridge College of Science",
    status: "ACTIVE",
    externalUniversityMode: false,
    underUniversity: false,
    city: "Bengaluru",
    stateName: "Karnataka",
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
    rowVersion: 1,
  },
  {
    tenantId: "tenant-1",
    institutionId: "inst-2",
    institutionType: "COLLEGE",
    institutionCode: "NBC-02",
    institutionName: "Northbridge School of Commerce",
    status: "DRAFT",
    externalUniversityMode: false,
    underUniversity: false,
    city: "Bengaluru",
    stateName: "Karnataka",
    createdAt: "2026-11-02T00:00:00Z",
    updatedAt: "2026-11-02T00:00:00Z",
    rowVersion: 1,
  },
];

function renderWithStore(ui: React.ReactElement, tenantId: string = "tenant-1") {
  const store = configureStore({ reducer: { auth: authReducer, tenantPermissions: tenantPermissionsReducer } });
  store.dispatch(
    setUser({
      id: "u1",
      displayName: "University Admin",
      tenantId,
      userType: "STAFF",
      platformRole: null,
      roleCode: null,
      roleName: null,
      photoUrl: null,
      mustChangePassword: false,
    })
  );
  store.dispatch(setTenantPermissions({
    tenantId,
    userAccountId: "u1",
    activeRoleCode: "UNIVERSITY_ADMIN",
    activeRoleName: "University Admin",
    allowedActions: ["ROLLDOWN_VIEW", "ROLLDOWN_OVERRIDE_SET"],
  }));
  return render(<Provider store={store}>{ui}</Provider>);
}

describe("Sprint 1 university administration screens (Stories #25 & #26)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(institutionService.listInstitutions).mockResolvedValue({
      items: mockInstitutions,
      total: 2,
    });
    vi.mocked(leaveApproverService.getGoLiveCheck).mockResolvedValue({
      ready: true,
      blockers: [],
    });
    vi.mocked(roleAssignmentService.listRoles).mockResolvedValue([]);
    vi.mocked(rolldownService.getRolldownMap).mockResolvedValue([]);
    vi.mocked(rolldownService.getOverrides).mockResolvedValue([]);
  });

  it("shows the institutions in the live register", async () => {
    renderWithStore(<InstitutionsPage />);

    await waitFor(() => {
      expect(screen.getByText("Northbridge College of Science")).toBeInTheDocument();
      expect(screen.getByText("Northbridge School of Commerce")).toBeInTheDocument();
      expect(screen.getByText("NBC-01")).toBeInTheDocument();
      expect(screen.getByText("NBC-02")).toBeInTheDocument();
    });
  });

  it("opens modal and creates draft institution upon submit (Story #25: INSTITUTION_CREATE_DRAFT)", async () => {
    const newInstitution: InstitutionItem = {
      tenantId: "tenant-1",
      institutionId: "inst-3",
      institutionType: "COLLEGE",
      institutionCode: "NBC-03",
      institutionName: "Northbridge Institute of Technology",
      status: "DRAFT",
      externalUniversityMode: false,
      underUniversity: false,
      city: "Bengaluru",
      stateName: "Karnataka",
      createdAt: "2026-11-06T00:00:00Z",
      updatedAt: "2026-11-06T00:00:00Z",
      rowVersion: 1,
    };
    vi.mocked(institutionService.createDraftInstitution).mockResolvedValue(newInstitution);

    renderWithStore(<InstitutionsPage />);

    // Open creation modal
    fireEvent.click(screen.getByRole("button", { name: /Register institution \(Draft\)/i }));

    expect(screen.getByRole("heading", { name: "Register institution (Draft)" })).toBeInTheDocument();

    // Fill form
    fireEvent.change(screen.getByLabelText(/Institution Code \*/i), { target: { value: "NBC-03" } });
    fireEvent.change(screen.getByLabelText(/Institution Name \*/i), {
      target: { value: "Northbridge Institute of Technology" },
    });
    fireEvent.change(screen.getByLabelText(/City/i), { target: { value: "Bengaluru" } });

    // Submit
    fireEvent.click(screen.getByRole("button", { name: /Submit & create draft/i }));

    await waitFor(() => {
      expect(institutionService.createDraftInstitution).toHaveBeenCalledWith("tenant-1", expect.objectContaining({
        institutionCode: "NBC-03",
        institutionName: "Northbridge Institute of Technology",
        institutionType: "COLLEGE",
      }));
      expect(screen.getAllByText(/Northbridge Institute of Technology/i).length).toBeGreaterThanOrEqual(1);
    });
  });

  it("activates draft institution when leave approver is configured (Story #26: INSTITUTION_ACTIVATE)", async () => {
    const activated: InstitutionItem = {
      ...mockInstitutions[1],
      status: "ACTIVE",
      activatedAt: "2026-11-06T12:00:00Z",
      rowVersion: 2,
    };
    vi.mocked(institutionService.activateInstitution).mockResolvedValue(activated);

    renderWithStore(<InstitutionsPage />);

    await waitFor(() => {
      expect(screen.getByText("Northbridge School of Commerce")).toBeInTheDocument();
    });

    const activateBtn = screen.getByRole("button", { name: "Activate" });
    fireEvent.click(activateBtn);

    await waitFor(() => {
      expect(institutionService.activateInstitution).toHaveBeenCalledWith("tenant-1", "inst-2");
      expect(screen.getByText(/activated successfully!/i)).toBeInTheDocument();
    });
  });

  it("displays Rule C7 banner when activation is refused without Principal leave approver", async () => {
    vi.mocked(institutionService.activateInstitution).mockRejectedValue({
      isAxiosError: true,
      response: {
        data: {
          status: 422,
          errorCode: "GO_LIVE_BLOCKED",
          error: "Principal's-leave approver not set for institution inst-2 [C7]",
        },
      },
    });

    renderWithStore(<InstitutionsPage />);

    await waitFor(() => {
      expect(screen.getByText("Northbridge School of Commerce")).toBeInTheDocument();
    });

    const activateBtn = screen.getByRole("button", { name: "Activate" });
    fireEvent.click(activateBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/Principal's leave approver is not configured for 'Northbridge School of Commerce'/i)
      ).toBeInTheDocument();
    });
  });

  it("opens modal and submits block request with reason (Story #27: INSTITUTION_BLOCK_REQUEST)", async () => {
    vi.mocked(institutionService.requestBlock).mockResolvedValue({
      status: "REQUESTED",
      institutionId: "inst-1",
      message: "Block request submitted for VC review",
    });

    renderWithStore(<InstitutionsPage />);

    await waitFor(() => {
      expect(screen.getByText("Northbridge College of Science")).toBeInTheDocument();
    });

    // Find and click Block button on active institution
    const blockBtn = screen.getByRole("button", { name: "Block" });
    fireEvent.click(blockBtn);

    expect(
      screen.getByRole("heading", { name: /Request to block Northbridge College of Science/i })
    ).toBeInTheDocument();

    const reasonInput = screen.getByLabelText(/Reason for blocking \*/i);
    fireEvent.change(reasonInput, {
      target: { value: "Repeated regulatory non-compliance during state board inspection." },
    });

    const submitBtn = screen.getByRole("button", { name: /Submit block request/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(institutionService.requestBlock).toHaveBeenCalledWith(
        "tenant-1",
        "inst-1",
        "Repeated regulatory non-compliance during state board inspection."
      );
      expect(
        screen.getByText(/Block request submitted for VC review/i)
      ).toBeInTheDocument();
    });
  });

  it("opens modal and submits executive block decision (Story #28: INSTITUTION_BLOCK_APPROVE)", async () => {
    vi.mocked(institutionService.decideBlock).mockResolvedValue({
      status: "BLOCKED",
      institutionId: "inst-1",
      message: "Institution blocked successfully by executive approval.",
    });

    renderWithStore(<InstitutionsPage />);

    await waitFor(() => {
      expect(screen.getByText("Northbridge College of Science")).toBeInTheDocument();
    });

    // Find and click "Review Block Decision"
    const decideBtns = screen.getAllByRole("button", { name: /Review Block Decision/i });
    fireEvent.click(decideBtns[0]);

    expect(
      screen.getByRole("heading", { name: /Review Block Decision: Northbridge College of Science/i })
    ).toBeInTheDocument();

    const notesInput = screen.getByLabelText(/Decision Notes/i);
    fireEvent.change(notesInput, {
      target: { value: "Executive approval of suspension due to regulatory breach." },
    });

    const submitBtn = screen.getByRole("button", { name: /Approve Block/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(institutionService.decideBlock).toHaveBeenCalledWith(
        "tenant-1",
        "inst-1",
        true,
        "Executive approval of suspension due to regulatory breach."
      );
      expect(
        screen.getByText(/Institution blocked successfully by executive approval\./i)
      ).toBeInTheDocument();
    });
  });

  it("submits executive rejection of block to keep institution active (Story #28: INSTITUTION_BLOCK_APPROVE)", async () => {
    vi.mocked(institutionService.decideBlock).mockResolvedValue({
      status: "ACTIVE",
      institutionId: "inst-1",
      message: "Institution block request was rejected by Vice Chancellor: Issues remedied.",
    });

    renderWithStore(<InstitutionsPage />);

    await waitFor(() => {
      expect(screen.getByText("Northbridge College of Science")).toBeInTheDocument();
    });

    const decideBtns = screen.getAllByRole("button", { name: /Review Block Decision/i });
    fireEvent.click(decideBtns[0]);

    // Choose reject block
    const rejectRadio = screen.getByLabelText(/Reject Block \/ Keep Active/i);
    fireEvent.click(rejectRadio);

    const notesInput = screen.getByLabelText(/Decision Notes/i);
    fireEvent.change(notesInput, {
      target: { value: "Issues remedied." },
    });

    const submitBtn = screen.getByRole("button", { name: /Reject Block/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(institutionService.decideBlock).toHaveBeenCalledWith(
        "tenant-1",
        "inst-1",
        false,
        "Issues remedied."
      );
      expect(
        screen.getByText(/Institution block request was rejected by Vice Chancellor: Issues remedied\./i)
      ).toBeInTheDocument();
    });
  });

  it("makes the unavailable university user records explicit", () => {
    renderWithStore(<InstitutionUsersPage />);
    expect(screen.getByText(/No Northbridge University user records are loaded/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Create user/i })).toBeDisabled();
  });

  it("shows an honest empty state when the tenant role catalogue is empty", async () => {
    vi.mocked(roleAssignmentService.listRoles).mockResolvedValue([]);
    renderWithStore(<RolesPermissionsPage />);
    await waitFor(() => expect(screen.getByText("No roles were returned by the tenant role catalogue.")).toBeInTheDocument());
    expect(screen.queryByText("University Admin")).not.toBeInTheDocument();
    expect(screen.getByText(/Role assignment and delegation policy/i)).toBeInTheDocument();
  });

  it("displays Go-Live readiness warning banner when missing approver blockers exist (Rules C6 & C7)", async () => {
    vi.mocked(leaveApproverService.getGoLiveCheck).mockResolvedValue({
      ready: false,
      blockers: [
        { blocker: "VC_LEAVE_APPROVER_MISSING" },
        { blocker: "PRINCIPAL_LEAVE_APPROVER_MISSING", institutionId: "inst-2" },
      ],
    });

    renderWithStore(<InstitutionsPage />);

    await waitFor(() => {
      expect(screen.getByText(/Go-live readiness warning:/i)).toBeInTheDocument();
      expect(screen.getByText(/Vice-Chancellor leave approver not set/i)).toBeInTheDocument();
      expect(screen.getByText(/Principal leave approver not set for institution ID: inst-2/i)).toBeInTheDocument();
    });
  });

  it("opens modal and configures Principal leave approver (Story #45: PRINCIPAL_LEAVE_APPROVER_SET)", async () => {
    vi.mocked(leaveApproverService.setPrincipalLeaveApprover).mockResolvedValue({
      leaveApproverSettingId: "setting-1",
      tenantId: "tenant-1",
      subjectRole: "PRINCIPAL",
      institutionId: "inst-1",
      approverKind: "MANAGEMENT",
      approverUserId: "00000000-0000-0000-0000-000000000009",
      status: "ACTIVE",
      effectiveFrom: "2026-11-01T00:00:00Z",
      rowVersion: 1,
    });

    renderWithStore(<InstitutionsPage />);

    await waitFor(() => {
      expect(screen.getByText("Northbridge College of Science")).toBeInTheDocument();
    });

    const approverButtons = screen.getAllByRole("button", { name: /^Approver$/i });
    fireEvent.click(approverButtons[0]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: /Configure Leave Approver: Northbridge College of Science/i })
      ).toBeInTheDocument();
    });

    const userIdInput = screen.getByLabelText(/Approver User Account ID \(UUID\) \*/i);
    fireEvent.change(userIdInput, {
      target: { value: "00000000-0000-0000-0000-000000000009" },
    });

    const submitBtn = screen.getByRole("button", { name: /Save Approver/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(leaveApproverService.setPrincipalLeaveApprover).toHaveBeenCalledWith(
        "tenant-1",
        "inst-1",
        {
          approverKind: "MANAGEMENT",
          approverUserId: "00000000-0000-0000-0000-000000000009",
        }
      );
      expect(
        screen.getByText(/Leave approver configured successfully for 'Northbridge College of Science'/i)
      ).toBeInTheDocument();
    });
  });

  it("opens modal and configures Vice-Chancellor leave approver (Story #46: VC_LEAVE_APPROVER_SET)", async () => {
    vi.mocked(leaveApproverService.setVcLeaveApprover).mockResolvedValue({
      leaveApproverSettingId: "setting-vc",
      tenantId: "tenant-1",
      subjectRole: "VICE_CHANCELLOR",
      approverKind: "GOVERNING_BODY_APPROVER",
      approverUserId: "00000000-0000-0000-0000-000000000010",
      status: "ACTIVE",
      effectiveFrom: "2026-11-01T00:00:00Z",
      rowVersion: 1,
    });

    renderWithStore(<InstitutionsPage />);

    const setVcBtn = screen.getByRole("button", { name: /Set VC Approver/i });
    fireEvent.click(setVcBtn);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: /Configure Vice-Chancellor's Leave Approver/i })
      ).toBeInTheDocument();
    });

    const userIdInput = screen.getByLabelText(/Approver User Account ID \(UUID\) \*/i);
    fireEvent.change(userIdInput, {
      target: { value: "00000000-0000-0000-0000-000000000010" },
    });

    const submitBtn = screen.getByRole("button", { name: /Save VC Approver/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(leaveApproverService.setVcLeaveApprover).toHaveBeenCalledWith(
        "tenant-1",
        {
          approverKind: "GOVERNING_BODY_APPROVER",
          approverUserId: "00000000-0000-0000-0000-000000000010",
        }
      );
      expect(
        screen.getByText(/Vice-Chancellor leave approver configured successfully/i)
      ).toBeInTheDocument();
    });
  });

  it("explains the approved V4.0 role roll-down rules", () => {
    renderWithStore(<MissingRolesPage />);
    expect(screen.getByText("How role coverage works")).toBeInTheDocument();
    expect(screen.getByText("Next-level leader")).toBeInTheDocument();
    expect(screen.getByText("Next-level administrator")).toBeInTheDocument();
  });

  it("creates custom role via modal (Story #16: ROLE_CUSTOM_MANAGE)", async () => {
    const mockRoles: RoleDefinitionItem[] = [
      {
        roleDefinitionId: "role-1",
        tenantId: "tenant-1",
        roleCode: "CUSTOM_EXAM_CELL",
        roleName: "Examination Cell Coordinator",
        roleLevel: "COLLEGE",
        roleOrigin: "CUSTOM",
        sensitiveRole: false,
        status: "ACTIVE",
      },
    ];

    vi.mocked(roleAssignmentService.listRoles).mockResolvedValue(mockRoles);
    vi.mocked(roleAssignmentService.createCustomRole).mockResolvedValue(mockRoles[0]);

    renderWithStore(<RolesPermissionsPage />);

    await waitFor(() => {
      expect(screen.getByText("Examination Cell Coordinator")).toBeInTheDocument();
    });

    const createBtn = screen.getByRole("button", { name: /\+ Create Custom Role/i });
    fireEvent.click(createBtn);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /Create Custom Role/i })).toBeInTheDocument();
    });

    const codeInput = screen.getByLabelText(/Role Code \*/i);
    const nameInput = screen.getByLabelText(/Role Name \*/i);

    fireEvent.change(codeInput, { target: { value: "CUSTOM_LAB_COORD" } });
    fireEvent.change(nameInput, { target: { value: "Lab Coordinator" } });

    const submitBtn = screen.getByRole("button", { name: /^Create Role$/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(roleAssignmentService.createCustomRole).toHaveBeenCalledWith("tenant-1", {
        roleCode: "CUSTOM_LAB_COORD",
        roleName: "Lab Coordinator",
        roleLevel: "COLLEGE",
        customScope: "INSTITUTION",
        actionCodes: ["USER_VIEW"],
      });
      expect(
        screen.getByText(/Custom role CUSTOM_LAB_COORD created successfully/i)
      ).toBeInTheDocument();
    });
  });

  it("retires custom role via modal (Story #16: ROLE_CUSTOM_MANAGE / Rule 17)", async () => {
    const mockRoles: RoleDefinitionItem[] = [
      {
        roleDefinitionId: "role-1",
        tenantId: "tenant-1",
        roleCode: "CUSTOM_EXAM_CELL",
        roleName: "Examination Cell Coordinator",
        roleLevel: "COLLEGE",
        roleOrigin: "CUSTOM",
        sensitiveRole: false,
        status: "ACTIVE",
      },
    ];

    vi.mocked(roleAssignmentService.listRoles).mockResolvedValue(mockRoles);
    vi.mocked(roleAssignmentService.retireCustomRole).mockResolvedValue({
      ...mockRoles[0],
      status: "RETIRED",
    });

    renderWithStore(<RolesPermissionsPage />);

    await waitFor(() => {
      expect(screen.getByText("Examination Cell Coordinator")).toBeInTheDocument();
    });

    const retireBtn = screen.getByRole("button", { name: /^Retire$/i });
    fireEvent.click(retireBtn);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: /Retire Examination Cell Coordinator\?/i })
      ).toBeInTheDocument();
    });

    const confirmBtn = screen.getByRole("button", { name: /Confirm Retirement/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(roleAssignmentService.retireCustomRole).toHaveBeenCalledWith("tenant-1", "role-1");
      expect(
        screen.getByText(/retired\. Existing assignments remain historical/i)
      ).toBeInTheDocument();
    });
  });

  it("loads and displays dynamic roll-down map entries (Story #23: ROLLDOWN_VIEW)", async () => {
    vi.mocked(rolldownService.getRolldownMap).mockResolvedValue([
      {
        missingRoleCode: "VICE_CHANCELLOR",
        context: "UNIVERSITY_WIDE",
        permissionKind: "APPROVE",
        preference: 1,
        standInRoleCode: "REGISTRAR",
        note: "Registrar acts for VC",
        hasOverride: false,
        overrideMode: null,
        overrideTargetRoleCode: null,
      },
    ]);

    renderWithStore(<MissingRolesPage />);

    await waitFor(() => {
      expect(screen.getByText("Vice Chancellor")).toBeInTheDocument();
      expect(screen.getByText("Registrar")).toBeInTheDocument();
      expect(screen.getByText("Default order")).toBeInTheDocument();
    });
  });

  it("opens modal and creates a statutory rolldown override (Story #24: ROLLDOWN_OVERRIDE_SET)", async () => {
    vi.mocked(rolldownService.getRolldownMap).mockResolvedValue([
      {
        missingRoleCode: "VICE_CHANCELLOR",
        context: "UNIVERSITY_WIDE",
        permissionKind: "APPROVE",
        preference: 1,
        standInRoleCode: "REGISTRAR",
        note: null,
        hasOverride: false,
        overrideMode: null,
        overrideTargetRoleCode: null,
      },
    ]);
    vi.mocked(rolldownService.createOverride).mockResolvedValue({
      roleRolldownOverrideId: "ov-1",
      institutionId: null,
      missingRoleCode: "VICE_CHANCELLOR",
      permissionKind: "APPROVE",
      actionCode: null,
      mode: "ASSIGN",
      targetRoleCode: "CONTROLLER_OF_EXAMINATIONS",
      reason: "Statutory delegation during executive travel",
      setByUserId: "user-1",
      setByRole: "MANAGEMENT",
      status: "ACTIVE",
      effectiveFrom: "2026-11-07T00:00:00Z",
      createdAt: "2026-11-07T00:00:00Z",
    });

    renderWithStore(<MissingRolesPage />);

    await waitFor(() => {
      expect(screen.getByText("Vice Chancellor")).toBeInTheDocument();
    });

    const setBtn = screen.getByRole("button", { name: /Set role coverage override/i });
    fireEvent.click(setBtn);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /Set role coverage override/i })).toBeInTheDocument();
    });

    const reasonInput = screen.getByLabelText(/Reason for this change/i);
    fireEvent.change(reasonInput, { target: { value: "Statutory delegation during executive travel" } });

    const submitBtn = screen.getByRole("button", { name: /^Save Override$/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(rolldownService.createOverride).toHaveBeenCalledWith(
        "tenant-1",
        "UNIVERSITY_ADMIN",
        expect.objectContaining({
          missingRoleCode: "VICE_CHANCELLOR",
          permissionKind: "APPROVE",
          reason: "Statutory delegation during executive travel",
        })
      );
      expect(screen.getByText(/Role coverage override saved for Vice Chancellor/i)).toBeInTheDocument();
    });
  });
});
