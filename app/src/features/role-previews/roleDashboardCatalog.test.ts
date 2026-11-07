import { describe, expect, it } from "vitest";
import { roleDashboardCatalog, roleDashboardByCode } from "./roleDashboardCatalog";

describe("EDU 360 role dashboard catalogue", () => {
  it("contains all 71 V4.0 standard roles", () => {
    expect(roleDashboardCatalog).toHaveLength(71);
  });

  it("keeps role codes unique and resolves every role", () => {
    const codes = roleDashboardCatalog.map((role) => role.code);
    expect(new Set(codes).size).toBe(71);
    for (const code of codes) expect(roleDashboardByCode(code)).toBeDefined();
  });

  it("assigns every role to a dashboard family and preserves its purpose", () => {
    for (const role of roleDashboardCatalog) {
      expect(role.name.trim()).not.toBe("");
      expect(role.purpose.trim()).not.toBe("");
      expect(role.family).toBeTruthy();
      expect(role.level).toBeTruthy();
    }
  });

  it("does not resolve unknown role codes", () => {
    expect(roleDashboardByCode("NOT_A_REAL_ROLE")).toBeUndefined();
  });
});
