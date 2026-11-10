import { beforeEach, describe, expect, it, vi } from "vitest";
import { store } from "../store";
import { setUser, type AuthUser } from "../store/slices/authSlice";
import { changePassword } from "./authManager";
import { getLogoutReason } from "./logoutNotice";
import { authService } from "./modules/authService";
import { getAccessToken, getRefreshToken, setTokens } from "./tokenStorage";

vi.mock("./modules/authService", () => ({
  authService: { changePassword: vi.fn(), refresh: vi.fn() },
}));
const mocked = vi.mocked(authService, true);

const user = { id: "u-1", displayName: "Admin", mustChangePassword: true } as AuthUser;
const changed = { ...user, mustChangePassword: false };

describe("changePassword", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    setTokens("stale-access", "refresh-1");
    store.dispatch(setUser(user));
    // @ts-expect-error - only the fields this code reads
    mocked.changePassword.mockResolvedValue({ data: changed });
  });

  it("replaces the stale token before letting the user continue", async () => {
    // @ts-expect-error - only the fields this code reads
    mocked.refresh.mockResolvedValue({ data: { accessToken: "fresh-access", refreshToken: "refresh-2" } });

    const result = await changePassword("Old-password1", "New-password1");

    expect(mocked.refresh).toHaveBeenCalledWith("refresh-1");
    expect(getAccessToken()).toBe("fresh-access");
    expect(getRefreshToken()).toBe("refresh-2");
    expect(result?.mustChangePassword).toBe(false);
    expect(store.getState().auth.user?.mustChangePassword).toBe(false);
  });

  it("sends the user to sign in again if the token can't be refreshed", async () => {
    mocked.refresh.mockRejectedValue(new Error("expired"));
    const replace = vi.fn();
    vi.stubGlobal("location", { ...window.location, replace });

    const result = await changePassword("Old-password1", "New-password1");

    expect(result).toBeNull();
    expect(getAccessToken()).toBeNull();
    expect(getLogoutReason()).toContain("new password");
    expect(replace).toHaveBeenCalledWith("/login");
    vi.unstubAllGlobals();
  });

  it("doesn't touch the session if the password change itself fails", async () => {
    mocked.changePassword.mockRejectedValue(new Error("wrong password"));

    await expect(changePassword("Wrong-password1", "New-password1")).rejects.toThrow("wrong password");

    expect(mocked.refresh).not.toHaveBeenCalled();
    expect(getAccessToken()).toBe("stale-access");
  });
});
