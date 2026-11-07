import { describe, expect, it } from "vitest";
import authReducer, {
  clearUser,
  setLoading,
  setUser,
  type AuthUser,
} from "./authSlice";

describe("authSlice", () => {
  it("starts unauthenticated while the session is being restored", () => {
    const state = authReducer(undefined, { type: "@@test/init" });

    expect(state).toEqual({
      user: null,
      isAuthenticated: false,
      loading: true,
    });
  });

  it("marks the session authenticated when a tenant user is set", () => {
    const user: AuthUser = {
      id: "user-123",
      displayName: "Test Teacher",
      tenantId: "tenant-abc",
      userType: "STAFF",
      platformRole: null,
      roleCode: null,
      roleName: null,
      photoUrl: null,
      mustChangePassword: false,
    };

    const state = authReducer(undefined, setUser(user));

    expect(state.user).toEqual(user);
    expect(state.isAuthenticated).toBe(true);
    expect(state.loading).toBe(true);
  });

  it("clears the user and finishes loading when the session is cleared", () => {
    const user: AuthUser = {
      id: "user-456",
      displayName: "Test Student",
      tenantId: "tenant-abc",
      userType: "STUDENT",
      platformRole: null,
      roleCode: null,
      roleName: null,
      photoUrl: null,
      mustChangePassword: false,
    };
    const authenticated = authReducer(undefined, setUser(user));

    const state = authReducer(authenticated, clearUser());

    expect(state.user).toBeNull();
    expect(state.isAuthenticated).toBe(false);
    expect(state.loading).toBe(false);
  });

  it("updates the session restoration loading state without changing identity", () => {
    const user: AuthUser = {
      id: "user-789",
      displayName: "Test Admin",
      tenantId: null,
      userType: null,
      platformRole: "PLATFORM_ADMIN",
      roleCode: null,
      roleName: null,
      photoUrl: null,
      mustChangePassword: false,
    };
    const authenticated = authReducer(undefined, setUser(user));

    const state = authReducer(authenticated, setLoading(false));

    expect(state.user).toEqual(user);
    expect(state.isAuthenticated).toBe(true);
    expect(state.loading).toBe(false);
  });

  it("keeps authentication false when a null user is set", () => {
    const state = authReducer(undefined, setUser(null));

    expect(state.user).toBeNull();
    expect(state.isAuthenticated).toBe(false);
  });
});
