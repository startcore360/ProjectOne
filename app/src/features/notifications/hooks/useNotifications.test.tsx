import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { notificationsApi } from "../api/notificationsApi";
import { NOTIFICATION_REFRESH_MS, useNotifications } from "./useNotifications";

vi.mock("../api/notificationsApi", () => ({
  notificationsApi: { list: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn() },
}));

describe("useNotifications polling fallback", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.mocked(notificationsApi.list).mockResolvedValue([]);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.resetAllMocks();
  });

  it("refreshes once per 60 seconds when no live event arrives", async () => {
    const { result, unmount } = renderHook(() => useNotifications("tenant-1"));
    await act(async () => { await Promise.resolve(); });
    expect(notificationsApi.list).toHaveBeenCalledTimes(1);
    expect(NOTIFICATION_REFRESH_MS).toBe(60_000);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(NOTIFICATION_REFRESH_MS);
    });
    expect(notificationsApi.list).toHaveBeenCalledTimes(2);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(NOTIFICATION_REFRESH_MS);
    });
    expect(notificationsApi.list).toHaveBeenCalledTimes(3);
    unmount();
  });

  it("refreshes immediately when the browser tab regains focus", async () => {
    const { unmount } = renderHook(() => useNotifications("tenant-1"));
    await act(async () => { await Promise.resolve(); });
    expect(notificationsApi.list).toHaveBeenCalledTimes(1);

    await act(async () => {
      window.dispatchEvent(new Event("focus"));
      await Promise.resolve();
    });
    expect(notificationsApi.list).toHaveBeenCalledTimes(2);
    unmount();
  });

  it("does not poll when no tenant is active", async () => {
    const { unmount } = renderHook(() => useNotifications(null));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3 * NOTIFICATION_REFRESH_MS);
    });
    expect(notificationsApi.list).not.toHaveBeenCalled();
    unmount();
  });
});
