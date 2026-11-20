import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import Button from "./Button";

describe("legacy UI Button adapter", () => {
  it("maps legacy variants and sizes to the design-system implementation", () => {
    render(<Button variant="ghost" size="md" fullWidth>Open records</Button>);
    const button = screen.getByRole("button", { name: "Open records" });
    expect(button).toHaveClass("eds-button--tertiary");
    expect(button).toHaveClass("eds-button--block");
    expect(button).not.toHaveClass("eds-button--sm");
  });

  it("maps the legacy loading prop to the shared busy state", () => {
    render(<Button loading>Saving</Button>);
    const button = screen.getByRole("button", { name: "Saving" });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-busy", "true");
  });
});
