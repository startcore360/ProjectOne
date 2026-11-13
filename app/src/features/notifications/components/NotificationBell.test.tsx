import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import authReducer, { setUser } from "../../../store/slices/authSlice";
import { notificationsApi, type BellNotification } from "../api/notificationsApi";
import { NotificationBell } from "./NotificationBell";
import { timeAgo } from "../utils/timeAgo";

vi.mock("../api/notificationsApi", () => ({
  notificationsApi: { list: vi.fn(), summary: vi.fn(), pendingApprovalCount: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn() },
}));

function renderBell(tenantId: string | null = "tenant-1") {
  const store = configureStore({ reducer: { auth: authReducer } });
  store.dispatch(
    setUser({
      id: "u1",
      displayName: "Riya Teacher",
      tenantId,
      userType: "STAFF",
      platformRole: null,
      roleCode: "PROFESSOR",
      roleName: "Professor",
      photoUrl: null,
      mustChangePassword: false,
    }),
  );
  return render(
    <Provider store={store}>
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>
    </Provider>,
  );
}

const base = (over: Partial<BellNotification>): BellNotification => ({
  notificationId: "n1",
  triggerCode: "TIMETABLE_PUBLISHED",
  title: "Timetable published",
  body: "Week 12 is live",
  priorityClass: "NORMAL",
  createdAt: new Date().toISOString(),
  ...over,
});

describe("NotificationBell", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(notificationsApi.summary).mockResolvedValue({ unreadCount: 0 });
    vi.mocked(notificationsApi.pendingApprovalCount).mockResolvedValue(0);
  });

  it("shows an unread count and lists notifications when the server sends a read state", async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue([
      base({ notificationId: "a", readAt: null }),
      base({ notificationId: "b", title: "Absence alert", readAt: null }),
      base({ notificationId: "c", title: "Old one", readAt: "2026-11-01T00:00:00Z" }),
    ]);
    renderBell();
    const button = await screen.findByRole("button", { name: "Notifications, 2 unread" });
    fireEvent.click(button);
    expect(await screen.findByText("Absence alert")).toBeInTheDocument();
    expect(screen.getByText("Old one")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /Mark ".*" as read/ })).toHaveLength(2);
  });

  it("includes pending approvals in the bell badge and announces them in the panel", async () => {\n    vi.mocked(notificationsApi.list).mockResolvedValue([]);\n    vi.mocked(notificationsApi.summary).mockResolvedValue({ unreadCount: 2 });\n    vi.mocked(notificationsApi.pendingApprovalCount).mockResolvedValue(3);\n    renderBell();\n    const bell = await screen.findByRole("button", { name: "Notifications, 2 unread, 3 pending approvals" });\n    expect(bell).toHaveTextContent("5");\n    fireEvent.click(bell);\n    expect(await screen.findByRole("status")).toHaveTextContent("3 approvals awaiting your decision");\n  });\n\n  it("marks one notification as read and lowers the count", async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue([base({ readAt: null })]);
    vi.mocked(notificationsApi.markRead).mockResolvedValue();
    renderBell();
    fireEvent.click(await screen.findByRole("button", { name: "Notifications, 1 unread" }));
    fireEvent.click(await screen.findByRole("button", { name: 'Mark "Timetable published" as read' }));
    await waitFor(() => expect(notificationsApi.markRead).toHaveBeenCalledWith("tenant-1", "n1"));
    expect(await screen.findByRole("button", { name: "Notifications" })).toBeInTheDocument();
  });

  it("marks everything read", async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue([base({ readAt: null }), base({ notificationId: "b", readAt: null })]);
    vi.mocked(notificationsApi.markAllRead).mockResolvedValue();
    renderBell();
    fireEvent.click(await screen.findByRole("button", { name: "Notifications, 2 unread" }));
    fireEvent.click(await screen.findByRole("button", { name: "Mark all read" }));
    await waitFor(() => expect(notificationsApi.markAllRead).toHaveBeenCalledWith("tenant-1"));
  });

  it("shows no count and no mark-read controls while the server has no read state", async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue([base({})]);
    renderBell();
    const button = await screen.findByRole("button", { name: "Notifications" });
    fireEvent.click(button);
    expect(await screen.findByText("Timetable published")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Mark/ })).not.toBeInTheDocument();
  });

  it("shows the empty state", async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue([]);
    renderBell();
    fireEvent.click(await screen.findByRole("button", { name: "Notifications" }));
    expect(await screen.findByText("You have no notifications.")).toBeInTheDocument();
  });

  it("shows an error state when loading fails", async () => {
    vi.mocked(notificationsApi.list).mockRejectedValue(new Error("boom"));
    renderBell();
    fireEvent.click(await screen.findByRole("button", { name: "Notifications" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load notifications.");
  });

  it("closes on the backdrop and the close button, and returns focus to the bell", async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue([]);
    renderBell();
    const bell = await screen.findByRole("button", { name: "Notifications" });
    fireEvent.click(bell);
    fireEvent.click(await screen.findByRole("button", { name: "Close notifications" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(bell).toHaveFocus();
    fireEvent.click(bell);
    const dialog = await screen.findByRole("dialog", { name: "Notifications" });
    fireEvent.mouseDown(dialog.parentElement as HTMLElement);
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("closes on Escape", async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue([]);
    renderBell();
    fireEvent.click(await screen.findByRole("button", { name: "Notifications" }));
    expect(await screen.findByRole("dialog", { name: "Notifications" })).toBeInTheDocument();
    fireEvent.keyDown(document, { key: "Escape" });
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("renders nothing for a user without a tenant", () => {
    const { container } = renderBell(null);
    expect(container).toBeEmptyDOMElement();
    expect(notificationsApi.list).not.toHaveBeenCalled();
  });

  it("formats relative times safely", () => {
    const now = Date.parse("2026-11-11T12:00:00Z");
    expect(timeAgo("2026-11-11T11:59:50Z", now)).toBe("now");
    expect(timeAgo("2026-11-11T11:55:00Z", now)).toBe("5m");
    expect(timeAgo("2026-11-11T09:00:00Z", now)).toBe("3h");
    expect(timeAgo("2026-11-09T12:00:00Z", now)).toBe("2d");
    expect(timeAgo("not-a-date", now)).toBe("");
  });
});
