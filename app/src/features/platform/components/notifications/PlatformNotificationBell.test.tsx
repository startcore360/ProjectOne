import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { platformNotificationsApi, type PlatformBellNotification } from "../../api/platformNotificationsApi";
import { PlatformNotificationBell } from "./PlatformNotificationBell";

vi.mock("../../api/platformNotificationsApi", () => ({
  platformNotificationsApi: { list: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn() },
}));

const notification = (overrides: Partial<PlatformBellNotification> = {}): PlatformBellNotification => ({
  notificationId: "notification-1",
  triggerCode: "TENANT_CREATED",
  title: "Tenant created",
  body: "A new tenant is ready.",
  priorityClass: "NORMAL",
  readAt: null,
  createdAt: "2026-11-17T10:00:00Z",
  ...overrides,
});

describe("PlatformNotificationBell", () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.resetAllMocks();
    vi.mocked(platformNotificationsApi.list).mockResolvedValue([]);
    vi.mocked(platformNotificationsApi.markRead).mockResolvedValue();
    vi.mocked(platformNotificationsApi.markAllRead).mockResolvedValue();
  });

  it("shows the unread badge and notification list", async () => {
    vi.mocked(platformNotificationsApi.list).mockResolvedValue([
      notification(),
      notification({ notificationId: "notification-2", title: "Invoice overdue" }),
      notification({ notificationId: "notification-3", title: "Previously read", readAt: "2026-11-16T10:00:00Z" }),
    ]);
    render(<PlatformNotificationBell />);
    const bell = await screen.findByRole("button", { name: "Notifications, 2 unread" });
    expect(bell).toHaveTextContent("2");
    fireEvent.click(bell);
    expect(await screen.findByText("Tenant created")).toBeInTheDocument();
    expect(screen.getByText("Invoice overdue")).toBeInTheDocument();
    expect(screen.getByText("Previously read")).toBeInTheDocument();
  });

  it("marks one notification as read", async () => {
    vi.mocked(platformNotificationsApi.list).mockResolvedValue([notification()]);
    render(<PlatformNotificationBell />);
    fireEvent.click(await screen.findByRole("button", { name: "Notifications, 1 unread" }));
    fireEvent.click(await screen.findByRole("button", { name: 'Mark "Tenant created" as read' }));
    await waitFor(() => expect(platformNotificationsApi.markRead).toHaveBeenCalledWith("notification-1"));
    expect(await screen.findByRole("button", { name: "Notifications" })).toBeInTheDocument();
  });

  it("marks all notifications as read", async () => {
    vi.mocked(platformNotificationsApi.list).mockResolvedValue([
      notification(),
      notification({ notificationId: "notification-2", title: "Invoice overdue" }),
    ]);
    render(<PlatformNotificationBell />);
    fireEvent.click(await screen.findByRole("button", { name: "Notifications, 2 unread" }));
    fireEvent.click(await screen.findByRole("button", { name: "Mark all read" }));
    await waitFor(() => expect(platformNotificationsApi.markAllRead).toHaveBeenCalledOnce());
    expect(await screen.findByRole("button", { name: "Notifications" })).toBeInTheDocument();
  });

  it("shows the loading state", async () => {
    let resolveList!: (value: PlatformBellNotification[]) => void;
    vi.mocked(platformNotificationsApi.list).mockReturnValue(new Promise((resolve) => { resolveList = resolve; }));
    render(<PlatformNotificationBell />);
    fireEvent.click(await screen.findByRole("button", { name: "Notifications" }));
    expect(await screen.findByText("Loading notifications")).toBeInTheDocument();
    await act(async () => resolveList([]));
  });

  it("shows the empty state", async () => {
    render(<PlatformNotificationBell />);
    fireEvent.click(await screen.findByRole("button", { name: "Notifications" }));
    expect(await screen.findByText("You have no notifications.")).toBeInTheDocument();
  });

  it("shows the error state", async () => {
    vi.mocked(platformNotificationsApi.list).mockRejectedValue(new Error("network"));
    render(<PlatformNotificationBell />);
    fireEvent.click(await screen.findByRole("button", { name: "Notifications" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load notifications.");
  });

  it("refreshes the list every 30 seconds and clears the interval on unmount", async () => {
    vi.useFakeTimers();
    vi.mocked(platformNotificationsApi.list).mockResolvedValue([]);
    const { unmount } = render(<PlatformNotificationBell />);
    await act(async () => { await Promise.resolve(); });
    expect(platformNotificationsApi.list).toHaveBeenCalledTimes(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
    expect(platformNotificationsApi.list).toHaveBeenCalledTimes(2);
    unmount();
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
    expect(platformNotificationsApi.list).toHaveBeenCalledTimes(2);
  });
});
