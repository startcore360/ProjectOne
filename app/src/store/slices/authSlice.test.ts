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

  it("marks the session authenticated when a user is set", () => {
    const user: AuthUser = {
      id: "user-123",
      email: "teacher@example.edu",
      name: "Test Teacher",
      role: "TEACHER",
    };

    const state = authReducer(undefined, setUser(user));

    expect(state.user).toEqual(user);
    expect(state.isAuthenticated).toBe(true);
    expect(state.loading).toBe(true);
  });

  it("clears the user and finishes loading when the session is cleared", () => {
    const user: AuthUser = {
      id: 42,
      email: "student@example.edu",
      name: "Test Student",
      role: "STUDENT",
    };
    const authenticated = authReducer(undefined, setUser(user));

    const state = authReducer(authenticated, clearUser());

    expect(state.user).toBeNull();
    expect(state.isAuthenticated).toBe(false);
    expect(state.loading).toBe(false);
  });

  it("updates the session restoration loading state without changing identity", () => {
    const user: AuthUser = {
      id: 7,
      email: "admin@example.edu",
      name: "Test Admin",
      role: "PLATFORM_ADMIN",
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
