import { describe, expect, it } from "vitest";
import authReducer, {
  clearUser,
  profileUpdated,
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

  it("reflects a profile photo upload and name change in the signed-in identity", () => {
    const user: AuthUser = {
      id: "user-321",
      displayName: "Old Name",
      tenantId: "tenant-abc",
      userType: "STAFF",
      platformRole: null,
      roleCode: null,
      roleName: null,
      photoUrl: null,
      mustChangePassword: false,
    };
    let state = authReducer(undefined, setUser(user));

    // First upload: photoUrl goes from null to the path, and the revision starts counting.
    state = authReducer(state, profileUpdated({ photoUrl: "/me/profile/photo", photoChanged: true }));
    expect(state.user?.photoUrl).toBe("/me/profile/photo");
    expect(state.user?.photoRevision).toBe(1);

    // Replacement: same path, so only the revision bump can trigger the avatar re-fetch.
    state = authReducer(state, profileUpdated({ photoUrl: "/me/profile/photo", photoChanged: true }));
    expect(state.user?.photoRevision).toBe(2);

    // Name-only save leaves the photo alone.
    state = authReducer(state, profileUpdated({ displayName: "New Name" }));
    expect(state.user?.displayName).toBe("New Name");
    expect(state.user?.photoRevision).toBe(2);
  });

  it("ignores a profile update when nobody is signed in", () => {
    const state = authReducer(undefined, profileUpdated({ displayName: "X", photoChanged: true }));

    expect(state.user).toBeNull();
  });
});
