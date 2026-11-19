import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import api from "../../../../services/axios";
import { AuditLogView, type AuditLogItem } from "./AuditLogView";

vi.mock("../../../../services/axios", () => ({
  default: { get: vi.fn() },
}));

const mockedApi = api as unknown as { get: ReturnType<typeof vi.fn> };

function items(count: number, offset = 0): AuditLogItem[] {
  return Array.from({ length: count }, (_, i) => ({
    auditOutboxId: `evt-${offset + i}`,
    tenantId: null,
    occurredAt: "2026-11-16T12:00:00Z",
    actorKind: "PLATFORM_USER",
    actorUserId: null,
    operation: "UPDATE",
    actionCode: `ACTION_${offset + i}`,
    entityType: "TENANT",
    entityId: null,
    outcome: "SUCCESS",
    correlationId: null,
    eventType: "TENANT_UPDATED",
    payload: { old_values: { state: "TRIAL" }, new_values: { state: "ACTIVE" } },
  }));
}

function page(pageNo: number, count: number, totalElements: number, totalPages: number) {
  return { data: { items: items(count, pageNo * 25), page: pageNo, size: 25, totalElements, totalPages } };
}

beforeEach(() => {
  mockedApi.get.mockReset();
});

describe("AuditLogView", () => {
  it("shows a single server pager with the real total", async () => {
    mockedApi.get.mockResolvedValueOnce(page(0, 25, 62, 3));
    render(<AuditLogView />);

    expect(await screen.findByText("ACTION_0")).toBeInTheDocument();
    expect(screen.getByText("ACTION_24")).toBeInTheDocument();
    expect(screen.getAllByRole("navigation", { name: /pagination/i })).toHaveLength(1);
    expect(screen.queryByText(/Rows per page/i)).not.toBeInTheDocument();
    expect(screen.getByText("Showing 1–25 of 62")).toBeInTheDocument();
    expect(screen.getByText("Page 1 of 3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Next" })).toBeEnabled();
  });

  it("disables Next on the last page and requests the next page from the server", async () => {
    mockedApi.get.mockResolvedValueOnce(page(0, 25, 30, 2)).mockResolvedValueOnce(page(1, 5, 30, 2));
    render(<AuditLogView />);
    fireEvent.click(await screen.findByRole("button", { name: "Next" }));

    expect(await screen.findByText("Showing 26–30 of 30")).toBeInTheDocument();
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
    expect(mockedApi.get).toHaveBeenLastCalledWith("/platform/audit-logs", { params: { page: 1, size: 25 } });
  });

  it("shows no pager when everything fits on one page", async () => {
    mockedApi.get.mockResolvedValueOnce(page(0, 25, 25, 1));
    render(<AuditLogView />);
    expect(await screen.findByText("ACTION_24")).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: /pagination/i })).not.toBeInTheDocument();
  });

  it("applies server-side tenant filters", async () => {
    mockedApi.get.mockResolvedValueOnce(page(0, 1, 1, 1)).mockResolvedValueOnce(page(0, 1, 1, 1));
    render(<AuditLogView />);
    await screen.findByText("ACTION_0");
    fireEvent.change(screen.getByLabelText("Tenant ID"), { target: { value: "tenant-123" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply filters" }));

    await waitFor(() => expect(mockedApi.get).toHaveBeenLastCalledWith("/platform/audit-logs", {
      params: { page: 0, size: 25, tenantId: "tenant-123" },
    }));
  });

  it("opens the full event payload in the details drawer", async () => {
    mockedApi.get.mockResolvedValueOnce(page(0, 1, 1, 1));
    render(<AuditLogView />);
    fireEvent.click(await screen.findByRole("button", { name: "Details" }));
    expect(screen.getByRole("dialog", { name: "Audit event details" })).toBeInTheDocument();
    expect(screen.getByText(/"old_values"/)).toBeInTheDocument();
    expect(screen.getByText(/"ACTIVE"/)).toBeInTheDocument();
  });

  it("requests a CSV export using the applied filters", async () => {
    mockedApi.get.mockResolvedValueOnce(page(0, 1, 1, 1)).mockResolvedValueOnce({ data: new Blob(["csv"]) });
    const createObjectURL = vi.fn(() => "blob:platform-audit");
    const revokeObjectURL = vi.fn();
    Object.defineProperty(window.URL, "createObjectURL", { configurable: true, value: createObjectURL });
    Object.defineProperty(window.URL, "revokeObjectURL", { configurable: true, value: revokeObjectURL });

    render(<AuditLogView />);
    fireEvent.click(await screen.findByRole("button", { name: "Export CSV" }));

    await waitFor(() => expect(mockedApi.get).toHaveBeenLastCalledWith("/platform/audit-logs/export", {
      params: {}, responseType: "blob",
    }));
    expect(createObjectURL).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:platform-audit");
  });
});
