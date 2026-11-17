import { fireEvent, render, screen } from "@testing-library/react";
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
  }));
}

function page(pageNo: number, count: number, totalElements: number, totalPages: number) {
  return { data: { items: items(count, pageNo * 25), page: pageNo, size: 25, totalElements, totalPages } };
}

beforeEach(() => {
  mockedApi.get.mockReset();
});

describe("AuditLogView pagination", () => {
  it("shows a single server pager with the real total, not the table's own pager", async () => {
    mockedApi.get.mockResolvedValueOnce(page(0, 25, 62, 3));

    render(<AuditLogView />);

    expect(await screen.findByText("ACTION_0")).toBeInTheDocument();
    // All 25 rows of this page are shown - the client pager (10 rows per page) is off.
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

  it("shows no pager when everything fits on one page (exactly 25 events)", async () => {
    mockedApi.get.mockResolvedValueOnce(page(0, 25, 25, 1));

    render(<AuditLogView />);

    expect(await screen.findByText("ACTION_24")).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: /pagination/i })).not.toBeInTheDocument();
  });
});
