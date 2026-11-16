import { act, cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { announceTrialExpired, clearTrialExpired } from "../../services/trialNotice";
import { TrialExpiredBanner } from "./TrialExpiredBanner";

describe("TrialExpiredBanner (E1-S7)", () => {
  afterEach(() => {
    cleanup();
    clearTrialExpired();
  });

  it("shows nothing until the server says the trial has ended", () => {
    const { container } = render(<TrialExpiredBanner />);
    expect(container.textContent).toBe("");
  });

  it("appears when a TRIAL_EXPIRED answer is announced, with the end date", () => {
    render(<TrialExpiredBanner />);
    act(() => announceTrialExpired("2026-11-10T00:00:00Z"));
    expect(screen.getByText(`Trial ended on ${new Date("2026-11-10T00:00:00Z").toLocaleDateString()}.`)).toBeTruthy();
    expect(screen.getByText(/Contact your administrator/)).toBeTruthy();
  });

  it("stays after a page change in the same tab", () => {
    announceTrialExpired(null);
    render(<TrialExpiredBanner />);
    expect(screen.getByText("The trial has ended.")).toBeTruthy();
  });
});
