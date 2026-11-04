import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import api from "../../../services/axios";
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
});

describe("Sprint 1 platform screens", () => {
  it("shows the platform overview and the sample tenant count", () => {
    render(<MemoryRouter><PlatformOverviewPage /></MemoryRouter>);
    expect(screen.getByRole("heading", { name: "Platform overview" })).toBeInTheDocument();
    expect(screen.getByText("Greenwood Public School")).toBeInTheDocument();
  });

  it("filters tenants by search text", () => {
    render(<TenantsPage />);
    fireEvent.change(screen.getByPlaceholderText("Search by institution or ID"), { target: { value: "Riverbank" } });
    expect(screen.getByText("Riverbank College")).toBeInTheDocument();
    expect(screen.queryByText("Greenwood Public School")).not.toBeInTheDocument();
  });

  it("opens the create trial tenant preview without claiming to create a record", () => {
    render(<TenantsPage />);
    fireEvent.click(screen.getByRole("button", { name: /Create trial tenant/i }));
    expect(screen.getByRole("dialog", { name: "Create trial tenant" })).toBeInTheDocument();
    expect(screen.getByText(/will not create a tenant/i)).toBeInTheDocument();
  });

  it("shows platform user management without inventing institution users", () => {
    render(<PlatformUsersPage />);
    expect(screen.getByRole("heading", { name: "Platform users" })).toBeInTheDocument();
    expect(screen.getByText("Aarav Mehta")).toBeInTheDocument();
  });

  it("shows the plan catalogue", () => {
    render(<PlansPage />);
    expect(screen.getByRole("heading", { name: "Plans & billing" })).toBeInTheDocument();
    expect(screen.getByText("Enterprise")).toBeInTheDocument();
  });

  it("shows the support-access policy and prototype session", () => {
    render(<SupportAccessPage />);
    expect(screen.getByText("Tenant data remains protected")).toBeInTheDocument();
    expect(screen.getByText("SUP-2026-00398")).toBeInTheDocument();
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
    fireEvent.click(screen.getByRole("button", { name: "Add integration type" }));

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
