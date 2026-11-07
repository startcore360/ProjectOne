import { configureStore } from "@reduxjs/toolkit";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { Provider } from "react-redux";
import { beforeEach, describe, expect, it, vi } from "vitest";
import api from "../../../services/axios";
import authReducer, { setUser } from "../../../store/slices/authSlice";
import platformPermissionsReducer, {
  setPlatformPermissions,
} from "../../../store/slices/platformPermissionsSlice";
import { PlatformRolesPage } from "../components/roles/RolesView";
import { PlatformRoleTemplatesPage } from "../components/roles/TemplatesView";

vi.mock("../../../services/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn() },
}));
const mockedApi = vi.mocked(api, true);

const CATALOG = [
  { actionCode: "INVOICE_MANAGE", description: "Create invoices", moduleName: "Platform", sensitive: false, lockedReason: null },
  { actionCode: "OVERDUE_FLAG", description: "Flag overdue tenants", moduleName: "Platform", sensitive: false, lockedReason: null },
  { actionCode: "DISCOUNT_APPLY", description: "Give a discount", moduleName: "Platform", sensitive: false, lockedReason: null },
  { actionCode: "TENANT_DELETION_APPROVE", description: "Approve tenant deletion", moduleName: "Governance", sensitive: true, lockedReason: "Stays with the Data Protection Officer." },
];
const TEMPLATES = [
  { platformRoleTemplateId: "tpl-1", templateName: "Billing Manager", description: "Standard", basePlatformRole: "BILLING_MANAGER", standard: true, locked: false, status: "ACTIVE", actionCodes: ["INVOICE_MANAGE", "OVERDUE_FLAG"] },
  { platformRoleTemplateId: "tpl-2", templateName: "Data Protection Officer", description: null, basePlatformRole: "DATA_PROTECTION_OFFICER", standard: true, locked: true, status: "ACTIVE", actionCodes: ["TENANT_DELETION_APPROVE"] },
];
const ACTIVE_ROLE = {
  platformRoleId: "role-1",
  roleName: "Billing Assistant",
  basePlatformRole: "BILLING_MANAGER",
  sourceTemplateId: "tpl-1",
  status: "ACTIVE",
  actions: [
    { actionCode: "OVERDUE_FLAG", accessMode: "HANDED_OVER" },
    { actionCode: "INVOICE_MANAGE", accessMode: "SHARED" },
  ],
  holders: 1,
};

function serve({ roles = [] as unknown[], stepIns = [] as unknown[], fail = false } = {}) {
  mockedApi.get.mockImplementation((url: string) => {
    if (fail) return Promise.reject(new Error("Network down"));
    if (url === "/platform/roles") return Promise.resolve({ data: roles });
    if (url === "/platform/role-templates") return Promise.resolve({ data: TEMPLATES });
    if (url === "/platform/role-actions") return Promise.resolve({ data: CATALOG });
    if (url === "/platform/step-ins") return Promise.resolve({ data: stepIns });
    if (url === "/platform/me/permissions") return Promise.resolve({ data: { actions: ["PLATFORM_USER_MANAGE"] } });
    return Promise.reject(new Error("Unexpected " + url));
  });
}

function renderPage(ui: React.ReactElement, steppedIn: string[] = []) {
  const store = configureStore({
    reducer: { auth: authReducer, platformPermissions: platformPermissionsReducer },
  });
  store.dispatch(
    setUser({
      id: "plt-1",
      displayName: "Owner",
      tenantId: null,
      userType: null,
      platformRole: "PRODUCT_OWNER",
      roleCode: null,
      roleName: null,
      photoUrl: null,
      mustChangePassword: false,
    }),
  );
  store.dispatch(
    setPlatformPermissions({ userId: "plt-1", actions: ["PLATFORM_USER_MANAGE"], customRoleName: null, stepInRequired: [], steppedIn }),
  );
  return render(<Provider store={store}>{ui}</Provider>);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("Platform roles page", () => {
  it("shows a loading state, then an honest empty state", async () => {
    serve();
    renderPage(<PlatformRolesPage />);
    expect(screen.getByText("Loading platform roles")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("No custom roles yet")).toBeInTheDocument());
    expect(screen.getByText("Nothing handed over")).toBeInTheDocument();
  });

  it("shows an error with a way to retry", async () => {
    serve({ fail: true });
    renderPage(<PlatformRolesPage />);
    await waitFor(() => expect(screen.getByText("Platform roles didn't load")).toBeInTheDocument());
    serve();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    await waitFor(() => expect(screen.getByText("No custom roles yet")).toBeInTheDocument());
  });

  it("copies a template into a new role and keeps edits to that role unless asked", async () => {
    serve();
    mockedApi.post.mockResolvedValue({ data: { platformRoleId: "role-9" } });
    renderPage(<PlatformRolesPage />);
    await waitFor(() => screen.getByText("No custom roles yet"));

    fireEvent.click(screen.getByRole("button", { name: "Create role" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText(/Role name/), { target: { value: "Billing Assistant" } });
    fireEvent.change(within(dialog).getByLabelText(/Start from template/), { target: { value: "tpl-1" } });

    expect(within(dialog).getByRole("checkbox", { name: /Create invoices/ })).toBeChecked();
    expect(within(dialog).getByRole("checkbox", { name: /Flag overdue tenants/ })).toBeChecked();

    fireEvent.click(within(dialog).getByRole("checkbox", { name: /Create invoices/ }));
    fireEvent.change(within(dialog).getByLabelText("Who does Flag overdue tenants"), { target: { value: "HANDED_OVER" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save draft" }));

    await waitFor(() =>
      expect(mockedApi.post).toHaveBeenCalledWith("/platform/roles", {
        roleName: "Billing Assistant",
        basePlatformRole: "BILLING_MANAGER",
        sourceTemplateId: "tpl-1",
        actions: [{ actionCode: "OVERDUE_FLAG", accessMode: "HANDED_OVER" }],
        updateTemplate: false,
      }),
    );
    expect(mockedApi.post).not.toHaveBeenCalledWith("/platform/roles/role-9/activation");
  });

  it("sends the template update only when ticked, and activates when asked", async () => {
    serve();
    mockedApi.post.mockResolvedValue({ data: { platformRoleId: "role-9" } });
    renderPage(<PlatformRolesPage />);
    await waitFor(() => screen.getByText("No custom roles yet"));

    fireEvent.click(screen.getByRole("button", { name: "Create role" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText(/Role name/), { target: { value: "Billing Assistant" } });
    fireEvent.change(within(dialog).getByLabelText(/Start from template/), { target: { value: "tpl-1" } });
    fireEvent.click(within(dialog).getByRole("checkbox", { name: /Also update this template/ }));
    fireEvent.click(within(dialog).getByRole("button", { name: "Save and activate" }));

    await waitFor(() =>
      expect(mockedApi.post).toHaveBeenCalledWith("/platform/roles", expect.objectContaining({ updateTemplate: true })),
    );
    await waitFor(() => expect(mockedApi.post).toHaveBeenCalledWith("/platform/roles/role-9/activation"));
  });

  it("locks what can't be given, including a forbidden pairing with the base role", async () => {
    serve();
    renderPage(<PlatformRolesPage />);
    await waitFor(() => screen.getByText("No custom roles yet"));

    fireEvent.click(screen.getByRole("button", { name: "Create role" }));
    const dialog = await screen.findByRole("dialog");

    expect(within(dialog).getByRole("checkbox", { name: /Approve tenant deletion/ })).toBeDisabled();
    expect(within(dialog).getByText("Stays with the Data Protection Officer.")).toBeInTheDocument();
    expect(within(dialog).queryByRole("option", { name: "Data Protection Officer" })).not.toBeInTheDocument();
    expect(within(dialog).getByRole("checkbox", { name: /Give a discount/ })).toBeEnabled();

    fireEvent.change(within(dialog).getByLabelText(/Based on/), { target: { value: "BILLING_MANAGER" } });
    expect(within(dialog).getByRole("checkbox", { name: /Give a discount/ })).toBeDisabled();
  });

  it("validates before sending and shows server errors in the dialog", async () => {
    serve();
    mockedApi.post.mockRejectedValue({ response: { data: { error: "A platform role named 'X' already exists." } } });
    renderPage(<PlatformRolesPage />);
    await waitFor(() => screen.getByText("No custom roles yet"));

    fireEvent.click(screen.getByRole("button", { name: "Create role" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "Save draft" }));
    expect(within(dialog).getByText("Enter a role name of at least 2 characters.")).toBeInTheDocument();
    expect(mockedApi.post).not.toHaveBeenCalled();

    fireEvent.change(within(dialog).getByLabelText(/Role name/), { target: { value: "XY" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save and activate" }));
    expect(within(dialog).getByText("Choose at least one permission before activating this role.")).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole("button", { name: "Save draft" }));
    await waitFor(() => expect(within(dialog).getByText("A platform role named 'X' already exists.")).toBeInTheDocument());
  });

  it("lists handed-over work and requires a reason to step in", async () => {
    serve({ roles: [ACTIVE_ROLE] });
    mockedApi.post.mockResolvedValue({ data: {} });
    renderPage(<PlatformRolesPage />);
    await screen.findAllByRole("cell", { name: "Billing Assistant" });

    expect(screen.getByRole("cell", { name: "Flag overdue tenants" })).toBeInTheDocument();
    expect(screen.queryByRole("cell", { name: "Create invoices" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Step in" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "Step in" }));
    expect(within(dialog).getByText("Give a reason of at least 5 characters.")).toBeInTheDocument();
    expect(mockedApi.post).not.toHaveBeenCalled();

    fireEvent.change(within(dialog).getByLabelText(/Reason/), { target: { value: "Colleague is on leave" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Step in" }));
    await waitFor(() =>
      expect(mockedApi.post).toHaveBeenCalledWith("/platform/step-ins", {
        actionCode: "OVERDUE_FLAG",
        reason: "Colleague is on leave",
      }),
    );
  });

  it("shows stepped-in work as stepped in", async () => {
    serve({ roles: [ACTIVE_ROLE] });
    renderPage(<PlatformRolesPage />, ["OVERDUE_FLAG"]);
    await waitFor(() => screen.getByText("Stepped in"));
    expect(screen.queryByRole("button", { name: "Step in" })).not.toBeInTheDocument();
  });

  it("takes a permission back after confirming", async () => {
    serve({ roles: [ACTIVE_ROLE] });
    mockedApi.post.mockResolvedValue({ data: {} });
    renderPage(<PlatformRolesPage />);
    await screen.findAllByRole("cell", { name: "Billing Assistant" });

    fireEvent.click(screen.getByRole("button", { name: "Take back" }));
    const buttons = await screen.findAllByRole("button", { name: "Take back" });
    fireEvent.click(buttons[buttons.length - 1]);

    await waitFor(() =>
      expect(mockedApi.post).toHaveBeenCalledWith("/platform/roles/role-1/actions/OVERDUE_FLAG/take-back"),
    );
  });

  it("offers Archive only once a role is not active", async () => {
    serve({ roles: [ACTIVE_ROLE, { ...ACTIVE_ROLE, platformRoleId: "role-2", roleName: "Old Role", status: "INACTIVE", holders: 0 }] });
    renderPage(<PlatformRolesPage />);
    await waitFor(() => screen.getByText("Old Role"));
    expect(screen.getAllByRole("button", { name: "Archive" })).toHaveLength(1);
    expect(screen.getAllByRole("button", { name: "Deactivate" })).toHaveLength(1);
  });
});

describe("Role templates page", () => {
  it("lets standard templates be edited and reset, but keeps the locked one read-only", async () => {
    serve();
    renderPage(<PlatformRoleTemplatesPage />);
    await screen.findByRole("button", { name: "Edit" });

    expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View" })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Reset" })).toHaveLength(1);

    fireEvent.click(screen.getByRole("button", { name: "View" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).queryByRole("button", { name: "Save template" })).not.toBeInTheDocument();
    expect(within(dialog).getByRole("checkbox", { name: /Approve tenant deletion/ })).toBeDisabled();
  });

  it("saves a template edit", async () => {
    serve();
    mockedApi.put.mockResolvedValue({ data: {} });
    renderPage(<PlatformRoleTemplatesPage />);
    await screen.findByRole("button", { name: "Edit" });

    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByRole("checkbox", { name: /Create invoices/ }));
    fireEvent.click(within(dialog).getByRole("button", { name: "Save template" }));

    await waitFor(() =>
      expect(mockedApi.put).toHaveBeenCalledWith(
        "/platform/role-templates/tpl-1",
        expect.objectContaining({ actionCodes: ["OVERDUE_FLAG"] }),
      ),
    );
  });
});
