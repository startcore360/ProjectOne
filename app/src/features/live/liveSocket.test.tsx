import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getAccessToken } from "../../services/tokenStorage";
import { logout } from "../../services/authManager";
import { useLiveSocket } from "./liveSocket";

vi.mock("../../services/tokenStorage", () => ({
  getAccessToken: vi.fn(() => "access-token"),
  getRefreshToken: vi.fn(() => null),
  setTokens: vi.fn(),
  clearTokens: vi.fn(),
}));
vi.mock("../../services/authManager", () => ({ logout: vi.fn() }));
vi.mock("../../services/logoutNotice", () => ({ setLogoutReason: vi.fn() }));
vi.mock("../../services/modules/authService", () => ({ authService: { refresh: vi.fn() } }));
vi.mock("../profile/api/tenantPermissionsService", () => ({ tenantPermissionsService: { reload: vi.fn() } }));

class FakeWebSocket {
  static OPEN = 1;
  static instances: FakeWebSocket[] = [];
  readyState = 0;
  onopen: ((event: Event) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  send = vi.fn();
  close = vi.fn();
  constructor(public url: string) { FakeWebSocket.instances.push(this); }
  open() { this.readyState = FakeWebSocket.OPEN; this.onopen?.(new Event("open")); }
  closeWith(code: number) {
    this.readyState = 3;
    this.onclose?.({ code, reason: "", wasClean: false } as CloseEvent);
  }
}

describe("useLiveSocket", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(Math, "random").mockReturnValue(0.5);
    FakeWebSocket.instances = [];
    vi.stubGlobal("WebSocket", FakeWebSocket as unknown as typeof WebSocket);
    vi.mocked(getAccessToken).mockReturnValue("access-token");
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("authenticates on open and reconnects with backoff after an unexpected close", async () => {
    const { unmount } = renderHook(() => useLiveSocket("tenant-1", false));
    expect(FakeWebSocket.instances).toHaveLength(1);
    const first = FakeWebSocket.instances[0];
    act(() => first.open());
    expect(first.send).toHaveBeenCalledWith(JSON.stringify({ type: "auth", token: "access-token" }));

    act(() => first.closeWith(1006));
    await act(async () => { await vi.advanceTimersByTimeAsync(1_000); });
    expect(FakeWebSocket.instances).toHaveLength(2);
    unmount();
  });

  it("does not connect for platform users or without a tenant", () => {
    const a = renderHook(() => useLiveSocket("tenant-1", true));
    const b = renderHook(() => useLiveSocket(null, false));
    expect(FakeWebSocket.instances).toHaveLength(0);
    a.unmount();
    b.unmount();
  });

  it("logs out when the server rejects the account from the live channel", () => {
    const { unmount } = renderHook(() => useLiveSocket("tenant-1", false));
    act(() => FakeWebSocket.instances[0].closeWith(4403));
    expect(logout).toHaveBeenCalledTimes(1);
    unmount();
  });
});
