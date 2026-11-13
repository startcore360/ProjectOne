import type { ReactElement } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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
    expect(screen.getByRole("heading", { name: "Platform overview" })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("Greenwood Public School")).toBeInTheDocument();
    });
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

    fireEvent.change(screen.getByLabelText(/Institution name/i), { target: { value: "Doon World Academy" } });
    fireEvent.change(screen.getByLabelText(/Assigned subdomain/i), { target: { value: "doon-world" } });
    fireEvent.change(screen.getByLabelText(/Primary administrator full name/i), { target: { value: "Anita Roy" } });
    fireEvent.change(screen.getByLabelText(/Primary administrator email/i), { target: { value: "principal@doon.edu" } });
    fireEvent.change(screen.getByLabelText(/Primary contact mobile/i), { target: { value: "+919876543210" } });
    fireEvent.change(screen.getByLabelText(/Aadhaar \/ National ID/i), { target: { value: "7891" } });

    fireEvent.click(screen.getByRole("button", { name: /Submit & create tenant/i }));

    await waitFor(() => {
      expect(mockedApi.post).toHaveBeenCalledWith("/platform/tenants", expect.objectContaining({
        tenantName: "Doon World Academy",
        subdomain: "doon-world",
        firstAdminEmail: "principal@doon.edu",
        firstAdminMobile: "+919876543210",
        firstAdminNationalIdLast4: "7891",
      }));
    });
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
    fireEvent.change(screen.getByLabelText(/Institution name/i), { target: { value: "Doon World Academy" } });
    fireEvent.change(screen.getByLabelText(/Assigned subdomain/i), { target: { value: "doon-world" } });
    fireEvent.change(screen.getByLabelText(/Primary administrator full name/i), { target: { value: "Anita Roy" } });
    fireEvent.change(screen.getByLabelText(/Primary administrator email/i), { target: { value: "principal@doon.edu" } });
    fireEvent.change(screen.getByLabelText(/Primary contact mobile/i), { target: { value: "+919876543210" } });
    fireEvent.change(screen.getByLabelText(/Aadhaar \/ National ID/i), { target: { value: "7891" } });
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
            keycloakRealm: "riverbank-realm",
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
        { paymentReference: "PAY-2026-X8841", note: "Verified" }
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
    await waitFor(() => expect(screen.getByText("Temp1234Pass")).toBeInTheDocument());
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
    await waitFor(() => {
      expect(screen.getAllByRole("button", { name: "Settings" }).length).toBeGreaterThan(0);
    });
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

    render(<PlansPage />);

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
          keycloakRealm: "greenwood-realm",
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
        { reason: "Statutory compliance audit pending." }
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

    const settingsBtns = screen.getAllByRole("button", { name: /Settings/i });
    fireEvent.click(settingsBtns[0]);

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
    expect(mockedApi.get).toHaveBeenCalledWith("/platform/audit-logs", { params: { page: 0, size: 100 } });
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
  it("shows real counts and marks the flagged gap as not available", async () => {
    mockedApi.get.mockResolvedValue({
      data: {
        totalTenantCount: 4,
        metrics: [
          { metricCode: "LOGINS", value: 120, asOfDate: "2026-11-04" },
          { metricCode: "ATTENDANCE_MARKED", value: null, asOfDate: null },
        ],
        totalUserCountGap: "Not available: cross-tenant RLS boundary.",
      },
    });
    render(<UsageCountsPage />);
    await waitFor(() => expect(screen.getByText("Logins")).toBeInTheDocument());
    expect(screen.getByText("120")).toBeInTheDocument();
    expect(screen.getByText("Not available")).toBeInTheDocument();
    expect(screen.getByText(/Total user count: not available\./)).toBeInTheDocument();
    expect(screen.getByText(/cross-tenant RLS boundary\./)).toBeInTheDocument();
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
