import { describe, expect, it } from "vitest";
import reducer, {
  clearTenantPermissions,
  setTenantPermissions,
  tenantPermissionsFailed,
  tenantPermissionsLoading,
} from "./tenantPermissionsSlice";

describe("tenantPermissionsSlice", () => {
  it("loads actions for the selected tenant role", () => {
    const loading = reducer(undefined, tenantPermissionsLoading());
    expect(loading.loading).toBe(true);
    const loaded = reducer(loading, setTenantPermissions({
      tenantId: "tenant-a",
      userAccountId: "user-a",
      activeRoleCode: "TEACHER",
      activeRoleName: "Teacher",
      allowedActions: ["TIMETABLE_VIEW"],
    }));
    expect(loaded.current?.allowedActions).toContain("TIMETABLE_VIEW");
    expect(loaded.loading).toBe(false);
  });

  it("clears permission data when role context changes or user signs out", () => {
    const loaded = reducer(undefined, setTenantPermissions({
      tenantId: "tenant-a", userAccountId: "user-a", activeRoleCode: "TEACHER",
      activeRoleName: "Teacher", allowedActions: ["TIMETABLE_VIEW"],
    }));
    expect(reducer(loaded, clearTenantPermissions()).current).toBeNull();
  });

  it("fails closed when permission loading fails", () => {
    const state = reducer(undefined, tenantPermissionsFailed("unavailable"));
    expect(state.current).toBeNull();
    expect(state.error).toBe("unavailable");
  });
});
