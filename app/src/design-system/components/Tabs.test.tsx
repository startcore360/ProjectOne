import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Tabs } from "./Tabs";

const items = [
  { id: "overview", label: "Overview" },
  { id: "details", label: "Details" },
  { id: "history", label: "History", disabled: true },
];

describe("Tabs", () => {
  it("uses tab semantics and supports arrow-key navigation", () => {
    const onChange = vi.fn();
    render(<Tabs items={items} activeId="overview" onChange={onChange} />);

    const overview = screen.getByRole("tab", { name: "Overview" });
    const details = screen.getByRole("tab", { name: "Details" });
    expect(overview).toHaveAttribute("aria-selected", "true");
    expect(details).toHaveAttribute("tabindex", "-1");

    overview.focus();
    fireEvent.keyDown(overview, { key: "ArrowRight" });
    expect(details).toHaveFocus();
    expect(onChange).toHaveBeenCalledWith("details");
  });

  it("renders button-mode navigation as a labelled button group", () => {
    render(<Tabs items={items} activeId="overview" onChange={vi.fn()} tabRole="button" />);

    expect(screen.getByRole("group", { name: "Navigation tabs" })).toBeInTheDocument();
    expect(screen.queryByRole("tablist")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Overview" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Details" })).toHaveAttribute("aria-pressed", "false");
  });
});
