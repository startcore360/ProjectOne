import { describe, expect, it, vi } from "vitest";
import { tenantPermissionsService } from "../features/profile/api/tenantPermissionsService";

vi.mock("./modules/authService", () => ({
  authService: {
    login: vi.fn().mockResolvedValue({ data: { accessToken: "access", refreshToken: "refresh", user: {
      id: "user-1", displayName: "Tenant user", tenantId: "tenant-1", userType: "STAFF"
    } } }),
    me: vi.fn().mockResolvedValue({ data: {
      id: "user-1", displayName: "Tenant user", tenantId: "tenant-1", userType: "STAFF"
    } }),
    logout: vi.fn(), changePassword: vi.fn(), refresh: vi.fn()
  }
}));
vi.mock("../features/profile/api/tenantPermissionsService", () => ({
  tenantPermissionsService: { reload: vi.fn().mockResolvedValue({ tenantId: "tenant-1", userAccountId: "user-1", allowedActions: ["TIMETABLE_VIEW"] }) }
}));
vi.mock("./tokenStorage", () => ({
  clearTokens: vi.fn(), getAccessToken: vi.fn(() => "access"), getRefreshToken: vi.fn(() => "refresh"), setTokens: vi.fn()
}));
vi.mock("./logoutNotice", () => ({ setLogoutReason: vi.fn() }));

describe("tenant permission bootstrap", () => {
  it("reloads server permissions after a fresh login", async () => {
    const { login } = await import("./authManager");
    await login("user@example.test", "password");
    expect(tenantPermissionsService.reload).toHaveBeenCalled();
  });

  it("reloads server permissions after session hydration on page refresh", async () => {
    vi.clearAllMocks();
    const { hydrateAuth } = await import("./authManager");
    await hydrateAuth();
    expect(tenantPermissionsService.reload).toHaveBeenCalled();
  });
});
