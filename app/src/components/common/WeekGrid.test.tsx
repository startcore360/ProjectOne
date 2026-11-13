import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WeekGrid } from "./WeekGrid";

const slots = [
  { id: "slot-1", day: "Monday", start: "09:00", end: "10:00", label: "Mathematics" },
  { id: "slot-2", day: "Tuesday", start: "10:00", end: "11:00", label: "Physics" },
  { id: "slot-3", day: "Wednesday", start: "11:00", end: "12:00", label: "Chemistry" },
];

describe("WeekGrid", () => {
  it("exposes valid grid rows and grid cells", () => {
    render(<WeekGrid slots={slots} />);
    expect(screen.getByRole("grid", { name: "Weekly timetable" })).toHaveAttribute("aria-rowcount", "3");
    expect(screen.getAllByRole("row")).toHaveLength(3);
    expect(screen.getAllByRole("gridcell")).toHaveLength(3);
  });

  it("supports arrow, Home and End navigation", () => {
    render(<WeekGrid slots={slots} />);
    const first = screen.getByRole("button", { name: /Monday.*Mathematics/ });
    const second = screen.getByRole("button", { name: /Tuesday.*Physics/ });
    const last = screen.getByRole("button", { name: /Wednesday.*Chemistry/ });

    first.focus();
    fireEvent.keyDown(first, { key: "ArrowDown" });
    expect(second).toHaveFocus();

    fireEvent.keyDown(second, { key: "End" });
    expect(last).toHaveFocus();

    fireEvent.keyDown(last, { key: "Home" });
    expect(first).toHaveFocus();
  });

  it("selects the focused timetable slot when activated", () => {
    const onSelect = vi.fn();
    render(<WeekGrid slots={slots} onSelect={onSelect} />);
    fireEvent.click(screen.getByRole("button", { name: /Tuesday.*Physics/ }));
    expect(onSelect).toHaveBeenCalledWith(slots[1]);
  });
});
