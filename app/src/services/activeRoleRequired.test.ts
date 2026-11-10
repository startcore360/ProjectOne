import { describe, expect, it, vi } from "vitest";
import {
  ACTIVE_ROLE_REQUIRED_EVENT,
  announceActiveRoleRequired,
  isActiveRoleRequired,
} from "./activeRoleRequired";

describe("ACTIVE_ROLE_REQUIRED (A-02)", () => {
  it("recognises only a 409 carrying the ACTIVE_ROLE_REQUIRED code", () => {
    expect(isActiveRoleRequired(409, { code: "ACTIVE_ROLE_REQUIRED" })).toBe(true);
    expect(isActiveRoleRequired(409, { code: "DUPLICATE_RESOURCE" })).toBe(false);
    expect(isActiveRoleRequired(403, { code: "ACTIVE_ROLE_REQUIRED" })).toBe(false);
    expect(isActiveRoleRequired(409, undefined)).toBe(false);
  });

  it("announces the event the role switcher listens for", () => {
    const listener = vi.fn();
    window.addEventListener(ACTIVE_ROLE_REQUIRED_EVENT, listener);
    announceActiveRoleRequired();
    window.removeEventListener(ACTIVE_ROLE_REQUIRED_EVENT, listener);
    expect(listener).toHaveBeenCalledTimes(1);
  });
});
