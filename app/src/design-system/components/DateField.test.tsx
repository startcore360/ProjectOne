import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import DateField from "./DateField";

describe("design-system DateField", () => {
  it("renders a labelled date input and forwards changes", () => {
    const onChange = vi.fn();
    render(<DateField id="effective-date" label="Effective date" value="2026-11-11" onChange={onChange} />);
    const input = screen.getByLabelText("Effective date");
    expect(input).toHaveAttribute("type", "date");
    fireEvent.change(input, { target: { value: "2026-11-12" } });
    expect(onChange).toHaveBeenCalled();
  });
});
