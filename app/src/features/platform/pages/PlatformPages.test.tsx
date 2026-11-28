import type { ReactElement } from "react";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import api from "../../../services/axios";
import authReducer, { setUser } from "../../../store/slices/authSlice";
import {
  AuditLogPage,
  BackupStatusPage,
  IntegrationTypesPage,
  PlatformOverviewPage,
  PlatformUsersPage,
  PlansPage,
  SupportAccessPage,
  TenantsPage,
  UsageCountsPage,
} from "./PlatformPages";

vi.mock("../../../services/axios", () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}));

const mockedApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
  patch: ReturnType<typeof vi.fn>;
};

beforeEach(() => {
  mockedApi.get.mockReset();
  mockedApi.post.mockReset();
  mockedApi.patch.mockReset();
  mockedApi.get.mockResolvedValue({ data: { items: [], page: 0, size: 50, totalElements: 0, totalPages: 0 } });
});

function renderWithStore(ui: ReactElement, platformRole: string | null = "PRODUCT_OWNER") {
  const store = configureStore({ reducer: { auth: authReducer } });
  store.dispatch(
    setUser({
      id: "plt-1",
      displayName: "Priya Nair",
      tenantId: null,
      userType: null,
      // @ts-expect-error - test-only literal, not every PlatformRole is needed here
      platformRole,
      roleCode: null,
      roleName: null,
      photoUrl: null,
      mustChangePassword: false,
    }),
  );
  return render(<Provider store={store}>{ui}</Provider>);
}

describe("Sprint 1 platform screens", () => {
  it("shows the platform overview and the tenant count", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url.includes("/platform/tenants")) {
        return Promise.resolve({
          data: {
            items: [
              {
                tenantId: "TEN-0001",
                tenantName: "Greenwood Public School",
                tenantType: "STANDALONE_SCHOOL",
                planName: "Standard",
                state: "ACTIVE",
                isolationMode: "SHARED",
                subdomain: "greenwood",
                timeZone: "Asia/Kolkata",
                dataRegion: "IN",
                keycloakRealm: "greenwood-realm",
                createdAt: "2026-11-01T00:00:00Z",
              },
            ],
            page: 0,
            size: 50,
            totalElements: 1,
            totalPages: 1,
          },
        });
      }
      return Promise.resolve({ data: { items: [] } });
    });
    renderWithStore(<MemoryRouter><PlatformOverviewPage /></MemoryRouter>);
    expect(screen.getByRole("heading", { level: 1, name: /^Good (morning|afternoon|evening)/ })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Go-live and trials" })).toBeInTheDocument();
      expect(screen.getByRole("heading", { name: "Platform activity" })).toBeInTheDocument();
    });
  });



  it("shows the Platform Admin dashboard only for Platform Admin and uses its existing read endpoints", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url.includes("/platform/tenants")) {
        return Promise.resolve({ data: {
          items: [{ tenantId: "tenant-pa-1", state: "TRIAL" }],
          page: 0, size: 200, totalElements: 1, totalPages: 1,
        } });
      }
      if (url.includes("/provisioning")) {
        return Promise.resolve({ data: { overallStatus: "DONE", steps: [] } });
      }
      if (url.includes("/support-tickets")) return Promise.resolve({ data: [] });
      if (url.includes("/backup-status")) {
        return Promise.resolve({ data: { wiredToRealBackupSystem: true, note: "", tenants: [{ tenantId: "tenant-pa-1", stale: true }] } });
      }
      return Promise.resolve({ data: [] });
    });

    renderWithStore(<MemoryRouter><PlatformOverviewPage /></MemoryRouter>, "PLATFORM_ADMIN");

    expect(screen.getByRole("heading", { name: "Platform Admin dashboard" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("Tenants pending activation")).toBeInTheDocument());
    expect(screen.getByText("Failed setups")).toBeInTheDocument();
    expect(screen.getByText("Open tickets")).toBeInTheDocument();
    expect(screen.getByText("Backup alerts")).toBeInTheDocument();
    expect(mockedApi.get).toHaveBeenCalledWith(expect.stringContaining("/platform/tenants"));
    expect(mockedApi.get).toHaveBeenCalledWith(expect.stringContaining("/platform/backup-status"));
  });

  it("loads only billing dashboard sources for a Billing Manager", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url === "/platform/reports/REVENUE") {
        return Promise.resolve({ data: { currency: "INR", metrics: { totalCollectionsThisMonth: 0, renewalsDueIn60Days: 0 } } });
      }
      return Promise.resolve({ data: [] });
    });
    renderWithStore(<PlatformOverviewPage />, "BILLING_MANAGER");

    expect(screen.getByRole("heading", { name: "Billing Manager dashboard" })).toBeInTheDocument();
    await waitFor(() => expect(mockedApi.get).toHaveBeenCalledWith("/platform/invoices/overdue"));
    expect(mockedApi.get).toHaveBeenCalledWith("/platform/invoices");
    expect(mockedApi.get).toHaveBeenCalledWith("/platform/reports/REVENUE");
    expect(mockedApi.get).not.toHaveBeenCalledWith(expect.stringContaining("/platform/tenants"));
    expect(mockedApi.get).not.toHaveBeenCalledWith("/platform/platform-users");
    expect(mockedApi.get).not.toHaveBeenCalledWith("/platform/audit-logs");
  });

  it("renders the DPO workspace without calling unrelated admin endpoints", async () => {
    renderWithStore(<PlatformOverviewPage />, "DATA_PROTECTION_OFFICER");
    expect(await screen.findByText(/Data protection workspace/)).toBeInTheDocument();
    expect(mockedApi.get).not.toHaveBeenCalledWith(expect.stringContaining("/platform/tenants"));
    expect(mockedApi.get).not.toHaveBeenCalledWith("/platform/platform-users");
    expect(mockedApi.get).not.toHaveBeenCalledWith("/platform/audit-logs");
  });


  it("hides usage and compliance actions from a Billing Manager", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url.includes("/platform/tenants")) {
        return Promise.resolve({ data: { items: [{
          tenantId: "TEN-BILL", tenantName: "Billing School", tenantType: "STANDALONE_SCHOOL",
          planName: "Standard", state: "ACTIVE", isolationMode: "SHARED", subdomain: "billing",
          timeZone: "Asia/Kolkata", dataRegion: "IN", keycloakRealm: "billing-realm",
          createdAt: "2026-11-01T00:00:00Z",
        }] } });
      }
      return Promise.resolve({ data: [] });
    });

    renderWithStore(<TenantsPage />, "BILLING_MANAGER");
    expect(await screen.findByText("Billing School")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Tenant actions|⋯/i }));
    expect(screen.queryByRole("menuitem", { name: "Usage" })).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "Compliance" })).not.toBeInTheDocument();
  });

  it("filters tenants by search text", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url.includes("/platform/tenants")) {
        return Promise.resolve({
          data: {
            items: [
              {
                tenantId: "TEN-0001",
                tenantName: "Greenwood Public School",
                tenantType: "STANDALONE_SCHOOL",
                planName: "Standard",
                state: "ACTIVE",
                isolationMode: "SHARED",
                subdomain: "greenwood",
                timeZone: "Asia/Kolkata",
                dataRegion: "IN",
                keycloakRealm: "greenwood-realm",
                createdAt: "2026-11-01T00:00:00Z",
              },
              {
                tenantId: "TEN-0003",
                tenantName: "Riverbank College",
                tenantType: "STANDALONE_COLLEGE",
                planName: "Standard",
                state: "TRIAL",
                isolationMode: "SHARED",
                subdomain: "riverbank",
                timeZone: "Asia/Kolkata",
                dataRegion: "IN",
                keycloakRealm: "riverbank-realm",
                createdAt: "2026-11-01T00:00:00Z",
              },
            ],
          },
        });
      }
      return Promise.resolve({ data: [] });
    });
    renderWithStore(<TenantsPage />);
    await waitFor(() => {
      expect(screen.getByText("Riverbank College")).toBeInTheDocument();
    });
    fireEvent.change(screen.getByPlaceholderText("Search by institution or ID"), { target: { value: "Riverbank" } });
    expect(screen.getByText("Riverbank College")).toBeInTheDocument();
    expect(screen.queryByText("Greenwood Public School")).not.toBeInTheDocument();
  });


  it("shows suspension and archival timestamps in the tenant register", async () => {
    const suspendedAt = "2026-11-02T10:30:00Z";
    const archivedAt = "2026-11-03T11:45:00Z";
    mockedApi.get.mockImplementation((url: string) => {
      if (url.includes("/platform/tenants")) {
        return Promise.resolve({ data: { items: [{
          tenantId: "TEN-ARCH", tenantName: "Archived School", tenantType: "STANDALONE_SCHOOL",
          planName: "Standard", state: "ARCHIVED", isolationMode: "SHARED", subdomain: "archived",
          timeZone: "Asia/Kolkata", dataRegion: "IN", keycloakRealm: "archived-realm",
          createdAt: "2026-11-01T00:00:00Z", suspendedAt, archivedAt,
        }] } });
      }
      return Promise.resolve({ data: [] });
    });

    renderWithStore(<TenantsPage />);
    expect(await screen.findByText("Archived School")).toBeInTheDocument();
    // "Archived" is also a status-filter option, so check it inside the tenant's own row.
    const row = screen.getByText("Archived School").closest("tr") as HTMLElement;
    expect(within(row).getByText("Archived")).toBeInTheDocument();
    expect(within(row).getByText(new Date(suspendedAt).toLocaleString())).toBeInTheDocument();
    expect(within(row).getByText(new Date(archivedAt).toLocaleString())).toBeInTheDocument();
  });

  it("opens the create trial tenant modal with statutory privacy note and fields", () => {
    renderWithStore(<TenantsPage />);
    fireEvent.click(screen.getByRole("button", { name: /Create trial tenant/i }));
    expect(screen.getByRole("dialog", { name: "Create trial tenant" })).toBeInTheDocument();
    expect(screen.getByText(/Per UIDAI guidelines and DPDP Act 2023/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText("+919876543210")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("e.g. 4582")).toBeInTheDocument();
  });

  it("submits new trial tenant to platform tenants API", async () => {
    mockedApi.get.mockImplementation((url: string) => Promise.resolve({ data: url === "/platform/plans" ? [{ planId: "plan-1", planName: "Standard trial", pricingType: "PER_STUDENT_YEAR", priceAmount: 499, currencyCode: "INR", status: "ACTIVE" }] : [] }));
    mockedApi.post.mockResolvedValue({
      data: {
        tenantId: "ten-99",
        tenantName: "Doon World Academy",
        subdomain: "doon-world",
        state: "TRIAL",
      },
    });

    renderWithStore(<TenantsPage />);
    fireEvent.click(screen.getByRole("button", { name: /Create trial tenant/i }));
    await screen.findByRole("option", { name: /Standard trial/ });

    fireEvent.change(screen.getByLabelText(/Institution name/i), { target: { value: "Doon World Academy" } });
    fireEvent.change(screen.getByLabelText(/Assigned subdomain/i), { target: { value: "doon-world" } });
    fireEvent.change(screen.getByLabelText(/Primary administrator full name/i), { target: { value: "Anita Roy" } });
    fireEvent.change(screen.getByLabelText(/Primary administrator email/i), { target: { value: "principal@doon.edu" } });
    fireEvent.change(screen.getByLabelText(/Primary contact mobile/i), { target: { value: "+919876543210" } });
    fireEvent.change(screen.getByLabelText(/Aadhaar \/ National ID/i), { target: { value: "7891" } });
    // PD-9: retention is required at creation, with no default.
    fireEvent.change(screen.getByLabelText(/Data retention \(months\)/i), { target: { value: "84" } });
    fireEvent.change(screen.getByLabelText(/Backup retention \(days\)/i), { target: { value: "30" } });

    fireEvent.click(screen.getByRole("button", { name: /Submit & create tenant/i }));

    await waitFor(() => {
      expect(mockedApi.post).toHaveBeenCalledWith("/platform/tenants", expect.objectContaining({
        tenantName: "Doon World Academy",
        subdomain: "doon-world",
        firstUserRole: "ADMIN",
        firstAdminEmail: "principal@doon.edu",
        firstAdminMobile: "+919876543210",
        firstAdminNationalIdLast4: "7891",
        retentionMonths: 84,
        backupRetentionDays: 30,
      }), expect.objectContaining({ headers: expect.objectContaining({ "Idempotency-Key": expect.any(String) }) }));
    });
  });

  it("asks who the first user is, offering the Admin owner now and Principal + Admin as coming soon", async () => {
    mockedApi.get.mockImplementation((url: string) => Promise.resolve({ data: url === "/platform/plans" ? [{ planId: "plan-1", planName: "Standard trial", pricingType: "PER_STUDENT_YEAR", priceAmount: 499, currencyCode: "INR", status: "ACTIVE" }] : [] }));

    renderWithStore(<TenantsPage />);
    fireEvent.click(screen.getByRole("button", { name: /Create trial tenant/i }));
    await screen.findByRole("option", { name: /Standard trial/ });

    const roleSelect = screen.getByLabelText(/First user's role/i) as HTMLSelectElement;
    // School (the default type): Admin owner is chosen; the combined owner shows but cannot be picked.
    expect(roleSelect.value).toBe("ADMIN");
    expect(screen.getByRole("option", { name: /School Admin: owner who runs it/i })).toBeEnabled();
    expect(screen.getByRole("option", { name: /Principal \+ Admin.*coming soon/i })).toBeDisabled();

    // College: same choice, named for the college.
    fireEvent.change(screen.getByLabelText(/Institution type/i), { target: { value: "STANDALONE_COLLEGE" } });
    expect(screen.getByRole("option", { name: /College Admin: owner who runs it/i })).toBeEnabled();
    expect(screen.getByRole("option", { name: /Principal \+ Admin.*coming soon/i })).toBeDisabled();

    // University: the University Admin is the only first user.
    fireEvent.change(screen.getByLabelText(/Institution type/i), { target: { value: "UNIVERSITY_GROUP" } });
    expect(screen.getByRole("option", { name: "University Admin" })).toBeEnabled();
    expect(screen.queryByRole("option", { name: /Principal \+ Admin/i })).not.toBeInTheDocument();
  });

  it("shows the first admin's temporary password once after creating a tenant, until dismissed", async () => {
    mockedApi.get.mockImplementation((url: string) => Promise.resolve({ data: url === "/platform/plans" ? [{ planId: "plan-1", planName: "Standard trial", pricingType: "PER_STUDENT_YEAR", priceAmount: 499, currencyCode: "INR", status: "ACTIVE" }] : [] }));
    mockedApi.post.mockResolvedValue({
      data: {
        tenantId: "ten-99",
        tenantName: "Doon World Academy",
        subdomain: "doon-world",
        state: "TRIAL",
        firstAdminEmail: "principal@doon.edu",
        firstAdminTemporaryPassword: "Edu@abc12345!",
      },
    });

    renderWithStore(<TenantsPage />);
    fireEvent.click(screen.getByRole("button", { name: /Create trial tenant/i }));
    await screen.findByRole("option", { name: /Standard trial/ });
    fireEvent.change(screen.getByLabelText(/Institution name/i), { target: { value: "Doon World Academy" } });
    fireEvent.change(screen.getByLabelText(/Assigned subdomain/i), { target: { value: "doon-world" } });
    fireEvent.change(screen.getByLabelText(/Primary administrator full name/i), { target: { value: "Anita Roy" } });
    fireEvent.change(screen.getByLabelText(/Primary administrator email/i), { target: { value: "principal@doon.edu" } });
    fireEvent.change(screen.getByLabelText(/Primary contact mobile/i), { target: { value: "+919876543210" } });
    fireEvent.change(screen.getByLabelText(/Aadhaar \/ National ID/i), { target: { value: "7891" } });
    fireEvent.change(screen.getByLabelText(/Data retention \(months\)/i), { target: { value: "84" } });
    fireEvent.change(screen.getByLabelText(/Backup retention \(days\)/i), { target: { value: "30" } });
    fireEvent.click(screen.getByRole("button", { name: /Submit & create tenant/i }));

    expect(await screen.findByText("Edu@abc12345!")).toBeInTheDocument();
    expect(screen.getByText("principal@doon.edu")).toBeInTheDocument();
    expect(screen.getByText(/doon-world\./)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "I've saved it" }));
    expect(screen.queryByText("Edu@abc12345!")).not.toBeInTheDocument();
  });

  it("opens activation dialog for a trial tenant and calls activation endpoint upon confirmation", async () => {
    mockedApi.get.mockResolvedValue({
      data: {
        items: [
          {
            tenantId: "TEN-0003",
            tenantName: "Riverbank College",
            tenantType: "STANDALONE_COLLEGE",
            planName: "Standard",
            state: "TRIAL",
            isolationMode: "SHARED",
            subdomain: "riverbank",
            timeZone: "Asia/Kolkata",
            dataRegion: "IN",
            keycloakRealm: "riverbank-realm", rowVersion: 7,
            createdAt: "2026-11-01T00:00:00Z",
          },
        ],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      },
    });
    mockedApi.post.mockResolvedValue({
      data: {
        tenantId: "TEN-0003",
        tenantName: "Riverbank College",
        state: "ACTIVE",
      },
    });

    renderWithStore(<TenantsPage />);

    await waitFor(() => {
      expect(screen.getByText("Riverbank College")).toBeInTheDocument();
    });

    const activateButtons = screen.getAllByRole("button", { name: /^Activate$/i });
    expect(activateButtons.length).toBeGreaterThan(0);
    fireEvent.click(activateButtons[0]);

    expect(screen.getByRole("dialog", { name: /Activate Tenant: Riverbank College/i })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Payment \/ Transaction Reference/i), {
      target: { value: "PAY-2026-X8841" },
    });
    fireEvent.change(screen.getByLabelText(/Activation Note/i), {
      target: { value: "Verified" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Confirm Activation/i }));

    await waitFor(() => {
      expect(mockedApi.post).toHaveBeenCalledWith(
        "/platform/tenants/TEN-0003/activation",
        { paymentReference: "PAY-2026-X8841", note: "Verified" },
        { headers: { "Idempotency-Key": expect.any(String), "If-Match": "\"7\"" } }
      );
    });
  });

  it("shows platform user management without inventing institution users", async () => {
    mockedApi.get.mockResolvedValue({
      data: [{
        platformUserId: "plt-1",
        fullName: "Aarav Mehta",
        email: "aarav.mehta@edu360.com",
        platformRole: "PLATFORM_ADMIN",
        status: "ACTIVE",
        mfaRequired: true,
        lastLoginAt: null,
        createdAt: "2026-01-01T00:00:00Z",
      }],
    });
    renderWithStore(<PlatformUsersPage />);
    expect(screen.getByRole("heading", { name: "Platform users" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("Aarav Mehta")).toBeInTheDocument());
  });

  it("shows recent platform sign-ins as relative days and older ones as dates", async () => {
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 9);
    const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1, 15);
    const older = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 3, 10);
    mockedApi.get.mockResolvedValue({
      data: [
        {
          platformUserId: "plt-today",
          fullName: "Today User",
          email: "today@edu360.com",
          platformRole: "PLATFORM_ADMIN",
          status: "ACTIVE",
          mfaRequired: true,
          lastLoginAt: today.toISOString(),
          createdAt: "2026-01-01T00:00:00Z",
        },
        {
          platformUserId: "plt-yesterday",
          fullName: "Yesterday User",
          email: "yesterday@edu360.com",
          platformRole: "PLATFORM_ADMIN",
          status: "ACTIVE",
          mfaRequired: true,
          lastLoginAt: yesterday.toISOString(),
          createdAt: "2026-01-01T00:00:00Z",
        },
        {
          platformUserId: "plt-older",
          fullName: "Older User",
          email: "older@edu360.com",
          platformRole: "PLATFORM_ADMIN",
          status: "ACTIVE",
          mfaRequired: true,
          lastLoginAt: older.toISOString(),
          createdAt: "2026-01-01T00:00:00Z",
        },
        {
          platformUserId: "plt-never",
          fullName: "Never User",
          email: "never@edu360.com",
          platformRole: "PLATFORM_ADMIN",
          status: "ACTIVE",
          mfaRequired: true,
          lastLoginAt: null,
          createdAt: "2026-01-01T00:00:00Z",
        },
      ],
    });

    renderWithStore(<PlatformUsersPage />);

    await waitFor(() => expect(screen.getByText("Today User")).toBeInTheDocument());
    expect(screen.getByText("Today")).toBeInTheDocument();
    expect(screen.getByText("Yesterday")).toBeInTheDocument();
    expect(screen.getByText(new Intl.DateTimeFormat("en-IN", {
      day: "numeric",
      month: "short",
      year: "numeric",
    }).format(older))).toBeInTheDocument();
    expect(screen.getByText("Never logged in")).toBeInTheDocument();
  });

  it("only lets a Product Owner invite a platform user", async () => {
    mockedApi.get.mockResolvedValue({ data: [] });
    renderWithStore(<PlatformUsersPage />, "PLATFORM_ADMIN");
    await waitFor(() => expect(mockedApi.get).toHaveBeenCalledWith("/platform/platform-users"));
    expect(screen.getByRole("button", { name: /Invite platform user/i })).toBeDisabled();
  });

  it("creates a platform user and shows the one-time temporary password", async () => {
    mockedApi.get.mockResolvedValue({ data: [] });
    mockedApi.post.mockResolvedValue({
      data: {
        platformUserId: "plt-2",
        fullName: "Priya Nair",
        email: "priya.nair@edu360.com",
        platformRole: "BILLING_MANAGER",
        status: "ACTIVE",
        mfaRequired: true,
        createdAt: "2026-01-01T00:00:00Z",
        tempPassword: "Temp1234Pass",
      },
    });

    renderWithStore(<PlatformUsersPage />);
    fireEvent.click(screen.getByRole("button", { name: /Invite platform user/i }));
    fireEvent.change(screen.getByLabelText(/Full name/i), { target: { value: "Priya Nair" } });
    fireEvent.change(screen.getByLabelText(/Email/i), { target: { value: "priya.nair@edu360.com" } });
    fireEvent.click(screen.getByRole("button", { name: /^Create platform user$/i }));

    await waitFor(() => {
      expect(mockedApi.post).toHaveBeenCalledWith("/platform/platform-users", expect.objectContaining({
        fullName: "Priya Nair",
        email: "priya.nair@edu360.com",
      }));
    });
    // The one-time password is shown in a read-only, copyable field.
    await waitFor(() => expect(screen.getByDisplayValue("Temp1234Pass")).toBeInTheDocument());
  });

  it("hides tenant lifecycle actions from a Platform Admin", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url.includes("/platform/tenants")) {
        return Promise.resolve({
          data: {
            items: [
              {
                tenantId: "TEN-0001",
                tenantName: "Greenwood Public School",
                tenantType: "STANDALONE_SCHOOL",
                planName: "Standard",
                state: "ACTIVE",
                isolationMode: "SHARED",
                subdomain: "greenwood",
                timeZone: "Asia/Kolkata",
                dataRegion: "IN",
                keycloakRealm: "greenwood-realm",
                createdAt: "2026-11-01T00:00:00Z",
              },
              {
                tenantId: "TEN-0003",
                tenantName: "Riverbank College",
                tenantType: "STANDALONE_COLLEGE",
                planName: "Standard",
                state: "TRIAL",
                isolationMode: "SHARED",
                subdomain: "riverbank",
                timeZone: "Asia/Kolkata",
                dataRegion: "IN",
                keycloakRealm: "riverbank-realm",
                createdAt: "2026-11-01T00:00:00Z",
              },
            ],
          },
        });
      }
      return Promise.resolve({ data: [] });
    });
    renderWithStore(<TenantsPage />, "PLATFORM_ADMIN");
    // Settings lives in each row's "⋯" menu; a Platform Admin can still open it.
    await waitFor(() => expect(screen.getByText("Greenwood Public School")).toBeInTheDocument());
    const row = screen.getByText("Greenwood Public School").closest("tr") as HTMLElement;
    fireEvent.click(within(row).getByRole("button", { name: /Tenant actions|⋯/i }));
    expect(within(row).getByRole("menuitem", { name: "Settings" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Activate" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Suspend" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reactivate" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Archive" })).not.toBeInTheDocument();
  });

  it("shows tenant lifecycle actions to a Product Owner", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url.includes("/platform/tenants")) {
        return Promise.resolve({
          data: {
            items: [
              {
                tenantId: "TEN-0001",
                tenantName: "Greenwood Public School",
                tenantType: "STANDALONE_SCHOOL",
                planName: "Standard",
                state: "ACTIVE",
                isolationMode: "SHARED",
                subdomain: "greenwood",
                timeZone: "Asia/Kolkata",
                dataRegion: "IN",
                keycloakRealm: "greenwood-realm",
                createdAt: "2026-11-01T00:00:00Z",
              },
              {
                tenantId: "TEN-0003",
                tenantName: "Riverbank College",
                tenantType: "STANDALONE_COLLEGE",
                planName: "Standard",
                state: "TRIAL",
                isolationMode: "SHARED",
                subdomain: "riverbank",
                timeZone: "Asia/Kolkata",
                dataRegion: "IN",
                keycloakRealm: "riverbank-realm",
                createdAt: "2026-11-01T00:00:00Z",
              },
            ],
          },
        });
      }
      return Promise.resolve({ data: [] });
    });
    renderWithStore(<TenantsPage />, "PRODUCT_OWNER");
    await waitFor(() => {
      expect(screen.getAllByRole("button", { name: "Activate" }).length).toBeGreaterThan(0);
      expect(screen.getAllByRole("button", { name: "Suspend" }).length).toBeGreaterThan(0);
    });
  });

  it("shows only real staff, with no sample rows", async () => {
    mockedApi.get.mockResolvedValue({ data: [] });
    renderWithStore(<PlatformUsersPage />);
    await waitFor(() => expect(screen.getByText("No platform staff yet.")).toBeInTheDocument());
    expect(screen.queryByText("Aarav Mehta")).not.toBeInTheDocument();
  });

  it("lets a Product Owner deactivate someone else, but not themselves", async () => {
    mockedApi.get.mockResolvedValue({
      data: [
        { platformUserId: "plt-1", fullName: "Me Myself", email: "me@edu360.com", platformRole: "PRODUCT_OWNER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" },
        { platformUserId: "plt-2", fullName: "Priya Nair", email: "priya@edu360.com", platformRole: "BILLING_MANAGER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" },
      ],
    });
    mockedApi.patch.mockResolvedValue({ data: {} });
    renderWithStore(<PlatformUsersPage />);
    await waitFor(() => expect(screen.getByText("Priya Nair")).toBeInTheDocument());

    expect(screen.getAllByRole("button", { name: "Deactivate" })).toHaveLength(1);
    expect(screen.getByText("This is you")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Deactivate" }));
    const buttons = await screen.findAllByRole("button", { name: "Deactivate" });
    fireEvent.click(buttons[buttons.length - 1]);

    await waitFor(() =>
      expect(mockedApi.patch).toHaveBeenCalledWith("/platform/platform-users/plt-2", { status: "INACTIVE" }),
    );
  });

  it("lets a Product Owner clear another active platform user's sign-in lockout", async () => {
    mockedApi.get.mockResolvedValue({
      data: [
        { platformUserId: "plt-1", fullName: "Me Myself", email: "me@edu360.com", platformRole: "PRODUCT_OWNER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" },
        { platformUserId: "plt-2", fullName: "Priya Nair", email: "priya@edu360.com", platformRole: "BILLING_MANAGER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" },
      ],
    });
    mockedApi.post.mockResolvedValue({ data: undefined });
    renderWithStore(<PlatformUsersPage />);
    await waitFor(() => expect(screen.getByText("Priya Nair")).toBeInTheDocument());

    expect(screen.getAllByRole("button", { name: "Unlock sign-in" })).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "Unlock sign-in" }));
    expect(screen.getByText(/clears the failed-login lockout/i)).toBeInTheDocument();
    const confirmButtons = screen.getAllByRole("button", { name: "Unlock sign-in" });
    fireEvent.click(confirmButtons[confirmButtons.length - 1]);

    await waitFor(() => {
      expect(mockedApi.post).toHaveBeenCalledWith("/platform/platform-users/plt-2/unlock");
      expect(screen.getByText(/Priya Nair's sign-in lockout was cleared/i)).toBeInTheDocument();
    });
  });

  it("shows an error and no success notice when unlock fails", async () => {
    mockedApi.get.mockResolvedValue({ data: [{ platformUserId: "plt-1", fullName: "Me Myself", email: "me@edu360.com", platformRole: "PRODUCT_OWNER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" }, { platformUserId: "plt-2", fullName: "Priya Nair", email: "priya@edu360.com", platformRole: "BILLING_MANAGER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" }] });
    mockedApi.post.mockRejectedValue({ response: { data: { message: "Keycloak is not reachable." } } });
    renderWithStore(<PlatformUsersPage />);
    await waitFor(() => expect(screen.getByText("Priya Nair")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "Unlock sign-in" }));
    const confirmButtons = screen.getAllByRole("button", { name: "Unlock sign-in" });
    fireEvent.click(confirmButtons[confirmButtons.length - 1]);

    await waitFor(() => expect(mockedApi.post).toHaveBeenCalledWith("/platform/platform-users/plt-2/unlock"));
    expect(screen.queryByText(/sign-in lockout was cleared/i)).not.toBeInTheDocument();
  });

  it("asks for a reason before resetting someone's two-step login, then sends it", async () => {
    mockedApi.get.mockResolvedValue({ data: [{ platformUserId: "plt-1", fullName: "Me Myself", email: "me@edu360.com", platformRole: "PRODUCT_OWNER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" }, { platformUserId: "plt-2", fullName: "Priya Nair", email: "priya@edu360.com", platformRole: "BILLING_MANAGER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" }] });
    mockedApi.post.mockResolvedValue({ data: { actionCode: "PLATFORM_USER_MANAGE", status: "RESET", message: "ok" } });
    renderWithStore(<PlatformUsersPage />);
    await waitFor(() => expect(screen.getByText("Priya Nair")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "Reset two-step login" }));
    // Confirm without a reason: nothing is sent.
    fireEvent.click(screen.getAllByRole("button", { name: "Reset two-step login" }).at(-1)!);
    expect(screen.getByText(/at least 5 characters/i)).toBeInTheDocument();
    expect(mockedApi.post).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText(/Reason/i), { target: { value: "Lost phone, identity confirmed" } });
    fireEvent.click(screen.getAllByRole("button", { name: "Reset two-step login" }).at(-1)!);

    await waitFor(() => {
      expect(mockedApi.post).toHaveBeenCalledWith("/platform/platform-users/plt-2/mfa-reset", {
        reason: "Lost phone, identity confirmed",
      });
    });
  });

  it("keeps the dialog open with the server message when the two-step reset fails", async () => {
    mockedApi.get.mockResolvedValue({ data: [{ platformUserId: "plt-1", fullName: "Me Myself", email: "me@edu360.com", platformRole: "PRODUCT_OWNER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" }, { platformUserId: "plt-2", fullName: "Priya Nair", email: "priya@edu360.com", platformRole: "BILLING_MANAGER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" }] });
    mockedApi.post.mockRejectedValue({ response: { data: { message: "Keycloak is not reachable." } } });
    renderWithStore(<PlatformUsersPage />);
    await waitFor(() => expect(screen.getByText("Priya Nair")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "Reset two-step login" }));
    fireEvent.change(screen.getByLabelText(/Reason/i), { target: { value: "Lost phone, identity confirmed" } });
    fireEvent.click(screen.getAllByRole("button", { name: "Reset two-step login" }).at(-1)!);

    await waitFor(() => expect(screen.getByText(/Keycloak is not reachable/i)).toBeInTheDocument());
    expect(screen.getByLabelText(/Reason/i)).toBeInTheDocument();
  });

  it("offers only Change role and Reactivate for an inactive person", async () => {
    mockedApi.get.mockResolvedValue({ data: [{ platformUserId: "plt-1", fullName: "Me Myself", email: "me@edu360.com", platformRole: "PRODUCT_OWNER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" }, { platformUserId: "plt-2", fullName: "Priya Nair", email: "priya@edu360.com", platformRole: "BILLING_MANAGER", status: "INACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" }] });
    renderWithStore(<PlatformUsersPage />);
    await waitFor(() => expect(screen.getByText("Priya Nair")).toBeInTheDocument());

    expect(screen.getByRole("button", { name: "Reactivate" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Change role" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Unlock sign-in" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reset password" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reset two-step login" })).not.toBeInTheDocument();
  });

  it("hides every account action from someone who cannot manage platform users", async () => {
    mockedApi.get.mockResolvedValue({ data: [{ platformUserId: "plt-2", fullName: "Priya Nair", email: "priya@edu360.com", platformRole: "BILLING_MANAGER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" }] });
    renderWithStore(<PlatformUsersPage />, "PLATFORM_ADMIN");
    await waitFor(() => expect(screen.getByText("Priya Nair")).toBeInTheDocument());

    for (const name of ["Unlock sign-in", "Reset password", "Reset two-step login", "Deactivate", "Change role"]) {
      expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
    }
  });

  it("lets a Product Owner reset another platform user's password and shows it once", async () => {
    mockedApi.get.mockResolvedValue({
      data: [
        { platformUserId: "plt-1", fullName: "Me Myself", email: "me@edu360.com", platformRole: "PRODUCT_OWNER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" },
        { platformUserId: "plt-2", fullName: "Priya Nair", email: "priya@edu360.com", platformRole: "PRODUCT_OWNER", status: "ACTIVE", mfaRequired: true, createdAt: "2026-01-01T00:00:00Z" },
      ],
    });
    mockedApi.post.mockResolvedValue({
      data: {
        platformUserId: "plt-2",
        fullName: "Priya Nair",
        email: "priya@edu360.com",
        platformRole: "PRODUCT_OWNER",
        status: "ACTIVE",
        mfaRequired: true,
        createdAt: "2026-01-01T00:00:00Z",
        tempPassword: "Reset-Temp-123!",
      },
    });
    renderWithStore(<PlatformUsersPage />);
    await waitFor(() => expect(screen.getByText("Priya Nair")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "Reset password" }));
    expect(screen.getByText(/current password stops working immediately/i)).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "Reset password" }).at(-1)!);

    await waitFor(() => {
      expect(mockedApi.post).toHaveBeenCalledWith("/platform/platform-users/plt-2/password-reset");
      expect(screen.getByDisplayValue("Reset-Temp-123!")).toBeInTheDocument();
    });
  });

  it("shows the plan catalogue", async () => {
    mockedApi.get.mockResolvedValue({
      data: [
        {
          planId: "plan-ent",
          planName: "Enterprise",
          baseMonthlyPrice: 150000,
          maxStudents: 5000,
          maxStaff: 500,
          storageLimitGb: 500,
          supportSlaHours: 4,
          features: ["SSO", "CUSTOM_DOMAIN"],
        },
      ],
    });
    renderWithStore(<PlansPage />);
    expect(screen.getByRole("heading", { name: "Plans & billing" })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("Enterprise")).toBeInTheDocument();
    });
  });

  it("creates a subscription plan through POST /platform/plans (Story #47: PLAN_MANAGE)", async () => {
    mockedApi.get.mockResolvedValue({ data: [] });
    mockedApi.post.mockResolvedValue({
      data: {
        planId: "plan-99",
        planName: "Custom College Plan",
        pricingType: "PER_STUDENT_YEAR",
        priceAmount: 750,
        currencyCode: "INR",
        userLimit: 3000,
        storageLimitGb: 150,
      },
    });

    // The plan form reads the caller's role (price fields are Product Owner only), so it needs the store.
    renderWithStore(<PlansPage />);

    fireEvent.click(screen.getByRole("button", { name: /Add subscription plan/i }));
    expect(screen.getByRole("heading", { name: "Add subscription plan" })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Plan Name/i), { target: { value: "Custom College Plan" } });
    fireEvent.change(screen.getByLabelText(/Price Amount/i), { target: { value: "750" } });
    fireEvent.change(screen.getByLabelText(/User Limit/i), { target: { value: "3000" } });

    fireEvent.click(screen.getByRole("button", { name: "Create plan" }));

    await waitFor(() => {
      expect(mockedApi.post).toHaveBeenCalledWith("/platform/plans", expect.objectContaining({
        planName: "Custom College Plan",
        priceAmount: 750,
        userLimit: 3000,
      }));
    });
  });

  it("suspends tenant upon confirmation (Story #50: TENANT_SUSPEND_ARCHIVE)", async () => {
    mockedApi.get.mockResolvedValue({
      data: {
        items: [{
          tenantId: "TEN-0001",
          tenantName: "Greenwood Public School",
          tenantType: "STANDALONE_SCHOOL",
          planName: "Standard",
          state: "ACTIVE",
          isolationMode: "SHARED",
          subdomain: "greenwood",
          timeZone: "Asia/Kolkata",
          dataRegion: "IN",
          keycloakRealm: "greenwood-realm", rowVersion: 2,
          createdAt: "2026-11-01T00:00:00Z",
        }],
      },
    });
    mockedApi.post.mockResolvedValue({ data: {} });

    renderWithStore(<TenantsPage />);

    await waitFor(() => {
      expect(screen.getByText("Greenwood Public School")).toBeInTheDocument();
    });

    const suspendBtns = screen.getAllByRole("button", { name: /Suspend/i });
    fireEvent.click(suspendBtns[0]);

    expect(screen.getByRole("heading", { name: /Suspend Tenant: Greenwood Public School/i })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Reason for suspension \*/i), {
      target: { value: "Statutory compliance audit pending." },
    });

    fireEvent.click(screen.getByRole("button", { name: /Confirm Suspension/i }));

    await waitFor(() => {
      expect(mockedApi.post).toHaveBeenCalledWith(
        "/platform/tenants/TEN-0001/suspension",
        { reason: "Statutory compliance audit pending." },
        { headers: { "Idempotency-Key": expect.any(String), "If-Match": "\"2\"" } }
      );
    });
  });

  it("updates tenant settings and analytics opt-out (Story #51: TENANT_SETTINGS)", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url.includes("/settings")) {
        return Promise.resolve({
          data: {
            tenantId: "TEN-0001",
            tenantName: "Greenwood Public School",
            planName: "Standard",
            state: "ACTIVE",
            timeZone: "Asia/Kolkata",
            dataRegion: "IN",
            analyticsOptOut: false,
            customDomain: "portal.greenwood.edu",
            userLimit: 1000,
            storageLimitGb: 50,
          },
        });
      }
      return Promise.resolve({
        data: {
          items: [{
            tenantId: "TEN-0001",
            tenantName: "Greenwood Public School",
            tenantType: "STANDALONE_SCHOOL",
            planName: "Standard",
            state: "ACTIVE",
            isolationMode: "SHARED",
            subdomain: "greenwood",
            timeZone: "Asia/Kolkata",
            dataRegion: "IN",
            keycloakRealm: "greenwood-realm",
            createdAt: "2026-11-01T00:00:00Z",
          }],
        },
      });
    });
    mockedApi.patch.mockResolvedValue({ data: {} });

    renderWithStore(<TenantsPage />);

    await waitFor(() => {
      expect(screen.getByText("Greenwood Public School")).toBeInTheDocument();
    });

    // Settings is in the row's "⋯" menu.
    const row = screen.getByText("Greenwood Public School").closest("tr") as HTMLElement;
    fireEvent.click(within(row).getByRole("button", { name: /Tenant actions|⋯/i }));
    fireEvent.click(within(row).getByRole("menuitem", { name: "Settings" }));

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /Tenant Settings: Greenwood Public School/i })).toBeInTheDocument();
    });

    const optOutCheckbox = screen.getByLabelText(/Opt out of anonymous platform analytics/i);
    fireEvent.click(optOutCheckbox);

    fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));

    await waitFor(() => {
      expect(mockedApi.patch).toHaveBeenCalledWith(
        "/platform/tenants/TEN-0001/settings",
        expect.objectContaining({
          analyticsOptOut: true,
        })
      );
    });
  });

  it("shows the support-access policy and sessions", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url.includes("support-access-grants")) {
        return Promise.resolve({
          data: [
            {
              supportAccessGrantId: "grant-1",
              ticketId: "ticket-1",
              tenantId: "TEN-0001",
              grantedAt: new Date().toISOString(),
              expiresAt: new Date(Date.now() + 3600000).toISOString(),
              status: "ACTIVE",
            },
          ],
        });
      }
      if (url.includes("support-tickets")) {
        return Promise.resolve({
          data: [
            {
              supportTicketId: "ticket-1",
              ticketNumber: "SUP-2026-00398",
              subject: "Database connection intermittent",
              tenantId: "TEN-0001",
              status: "OPEN",
              priority: "HIGH",
              createdAt: new Date().toISOString(),
            },
          ],
        });
      }
      return Promise.resolve({ data: [] });
    });
    renderWithStore(<SupportAccessPage />);
    expect(screen.getByText("Tenant data remains protected")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("SUP-2026-00398")).toBeInTheDocument();
    });
  });
});

describe("AuditLogPage (wired to GET /api/platform/audit-logs)", () => {
  it("shows a loading state while the request is in flight", () => {
    mockedApi.get.mockReturnValue(new Promise(() => {}));
    render(<AuditLogPage />);
    expect(screen.getByText("Loading audit events")).toBeInTheDocument();
  });

  it("shows an honest empty state when the outbox has no rows yet, not a fake row", async () => {
    mockedApi.get.mockResolvedValue({ data: { items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } });
    render(<AuditLogPage />);
    await waitFor(() => expect(screen.getByText("No platform audit events recorded yet.")).toBeInTheDocument());
    // The audit log is paged, 25 events per page.
    expect(mockedApi.get).toHaveBeenCalledWith("/platform/audit-logs", { params: { page: 0, size: 25 } });
  });

  it("renders real audit rows when the backend returns them", async () => {
    mockedApi.get.mockResolvedValue({
      data: {
        items: [{
          auditOutboxId: "aud-1",
          tenantId: null,
          occurredAt: "2026-11-01T10:00:00Z",
          actorKind: "PLATFORM_ADMIN",
          actorUserId: "user-1",
          operation: "SUSPEND_TENANT",
          actionCode: "PLATFORM_TENANT_SUSPEND",
          entityType: "TENANT",
          entityId: "tenant-1",
          outcome: "DENIED",
          correlationId: "corr-1",
        }],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
      },
    });
    render(<AuditLogPage />);
    await waitFor(() => expect(screen.getByText("PLATFORM_TENANT_SUSPEND")).toBeInTheDocument());
    expect(screen.getByRole("cell", { name: "Denied" })).toBeInTheDocument();
  });

  it("shows an error state on failure instead of silently showing nothing", async () => {
    mockedApi.get.mockRejectedValue(new Error("network down"));
    render(<AuditLogPage />);
    await waitFor(() => expect(screen.getByText(/Could not load audit events: network down/)).toBeInTheDocument());
  });
});

describe("UsageCountsPage (wired to GET /api/platform/usage-counts)", () => {
  it("shows real counts including materialized active users", async () => {
    mockedApi.get.mockResolvedValue({
      data: {
        totalTenantCount: 4,
        tenantsInAnalytics: 3,
        tenantsOptedOut: 1,
        metrics: [
          { metricCode: "LOGINS", value: 120, asOfDate: "2026-11-04" },
          { metricCode: "ATTENDANCE_MARKED", value: null, asOfDate: null },
          { metricCode: "ACTIVE_USERS", value: 45, asOfDate: "2026-11-01" },
        ],
      },
    });
    render(<UsageCountsPage />);
    await waitFor(() => expect(screen.getByText("Logins")).toBeInTheDocument());
    expect(screen.getByText("120")).toBeInTheDocument();
    expect(screen.getByText("Not available")).toBeInTheDocument();
    expect(screen.getByText("Active users")).toBeInTheDocument();
    expect(screen.getByText("45")).toBeInTheDocument();
    expect(screen.queryByText(/Total user count: not available/)).not.toBeInTheDocument();
  });

  it("shows an error state on failure", async () => {
    mockedApi.get.mockRejectedValue(new Error("boom"));
    render(<UsageCountsPage />);
    await waitFor(() => expect(screen.getByText(/Could not load usage counts: boom/)).toBeInTheDocument());
  });
});

describe("BackupStatusPage (wired to GET /api/platform/backup-status)", () => {
  it("surfaces the not-connected banner honestly instead of hiding it", async () => {
    mockedApi.get.mockResolvedValue({
      data: {
        wiredToRealBackupSystem: false,
        note: "No backup system writes to this table yet.",
        tenants: [],
      },
    });
    render(<BackupStatusPage />);
    await waitFor(() => expect(screen.getByText("Not connected to a real backup system.")).toBeInTheDocument());
    expect(screen.getByText(/No backup system writes to this table yet\./)).toBeInTheDocument();
    expect(screen.getByText("No backup status rows recorded yet.")).toBeInTheDocument();
  });
});

describe("IntegrationTypesPage (wired to /api/platform/integration-types)", () => {
  it("lists real integration types", async () => {
    mockedApi.get.mockResolvedValue({
      data: [{
        integrationTypeId: "it-1",
        integrationTypeCode: "SMS_DLT",
        description: "DLT-registered SMS",
        status: "ALLOWED",
        allowedProviders: ["MSG91"],
        createdAt: "2026-10-01T00:00:00Z",
        updatedAt: "2026-10-01T00:00:00Z",
        rowVersion: 1,
      }],
    });
    render(<IntegrationTypesPage />);
    await waitFor(() => expect(screen.getByText("SMS_DLT")).toBeInTheDocument());
    expect(screen.getByText("Allowed")).toBeInTheDocument();
  });

  it("creates a new integration type through POST and reloads the list", async () => {
    mockedApi.get
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({
        data: [{
          integrationTypeId: "it-2",
          integrationTypeCode: "RAZORPAY",
          description: "Payments",
          status: "ALLOWED",
          allowedProviders: [],
          createdAt: "2026-10-01T00:00:00Z",
          updatedAt: "2026-10-01T00:00:00Z",
          rowVersion: 1,
        }],
      });
    mockedApi.post.mockResolvedValue({ data: {} });

    render(<IntegrationTypesPage />);
    await waitFor(() => expect(screen.getByText("No integration types defined yet.")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /Add integration type/i }));
    fireEvent.change(screen.getByLabelText(/Integration type code/i), { target: { value: "RAZORPAY" } });
    fireEvent.change(screen.getByLabelText(/^Description/i), { target: { value: "Payments" } });
    fireEvent.click(screen.getByRole("button", { name: "Create integration type" }));

    await waitFor(() => expect(mockedApi.post).toHaveBeenCalledWith("/platform/integration-types", {
      integrationTypeCode: "RAZORPAY",
      description: "Payments",
      allowedProviders: [],
    }));
    await waitFor(() => expect(screen.getByText("RAZORPAY")).toBeInTheDocument());
    expect(screen.getByText("Integration type created.")).toBeInTheDocument();
  });

  it("shows an error state on failure instead of a blank table", async () => {
    mockedApi.get.mockRejectedValue(new Error("down"));
    render(<IntegrationTypesPage />);
    await waitFor(() => expect(screen.getByText(/Could not load integration types: down/)).toBeInTheDocument());
  });
});

describe("Audit, usage, backup and integration screens stay out of other mock pages' scope", () => {
  it("shows an empty audit state until audit records are connected (legacy guard)", async () => {
    mockedApi.get.mockResolvedValue({ data: { items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } });
    render(<AuditLogPage />);
    await waitFor(() => expect(screen.getByText("No platform audit events recorded yet.")).toBeInTheDocument());
  });
});
