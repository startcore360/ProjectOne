import { describe, expect, it } from "vitest";
import { getLocalDateString, getLocalMonthStart } from "./dateUtils";

describe("dateUtils (EDU-UX-010, EDU-UX-030)", () => {
  it("formats local calendar date consistently", () => {
    // 2026-11-20 at 01:30 AM local time
    const testDate = new Date(2026, 10, 20, 1, 30, 0); // Month is 0-indexed, 10 = November
    expect(getLocalDateString(testDate)).toBe("2026-11-20");
    expect(getLocalMonthStart(testDate)).toBe("2026-11-01");
  });

  it("formats first day of month and leap years accurately", () => {
    const leapDate = new Date(2024, 1, 29, 23, 59, 59); // Feb 29, 2024
    expect(getLocalDateString(leapDate)).toBe("2024-02-29");
    expect(getLocalMonthStart(leapDate)).toBe("2024-02-01");
  });
});
