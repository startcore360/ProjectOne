import { describe, expect, it } from "vitest";
import type { SessionResponse } from "../api/sessionsService";
import { activityStatus, deviceTitle } from "./deviceLabels";

const NOW = Date.parse("2026-11-09T12:00:00Z");
const base: SessionResponse = {
  sessionId: "s",
  current: false,
  revocable: true,
  issuedAt: null,
  expiresAt: null,
  deviceType: "PHONE",
  deviceName: null,
  ipAddress: null,
  lastActiveAt: null,
};
const minutesAgo = (m: number) => new Date(NOW - m * 60_000).toISOString();

describe("activityStatus", () => {
  it("treats this device as active now", () => {
    expect(activityStatus({ ...base, current: true }, NOW)).toEqual({ kind: "live", label: "Active now" });
  });

  it("shows another device as active now when it was used in the last 5 minutes", () => {
    expect(activityStatus({ ...base, lastActiveAt: minutesAgo(3) }, NOW).kind).toBe("live");
    expect(activityStatus({ ...base, lastActiveAt: minutesAgo(5) }, NOW).kind).toBe("live");
  });

  it("shows minutes ago within the hour", () => {
    expect(activityStatus({ ...base, lastActiveAt: minutesAgo(25) }, NOW)).toEqual({
      kind: "recent",
      label: "Active 25 min ago",
    });
  });

  it("shows idle after an hour, or when last activity is unknown", () => {
    expect(activityStatus({ ...base, lastActiveAt: minutesAgo(90) }, NOW).kind).toBe("idle");
    expect(activityStatus({ ...base, lastActiveAt: null }, NOW).kind).toBe("idle");
  });
});

describe("deviceTitle", () => {
  it("prefers the recorded label, then the device type", () => {
    expect(deviceTitle({ ...base, deviceName: "Safari on iPhone" })).toBe("Safari on iPhone");
    expect(deviceTitle(base)).toBe("Phone");
    expect(deviceTitle({ ...base, deviceType: "UNKNOWN" })).toBe("Unknown device");
  });
});
