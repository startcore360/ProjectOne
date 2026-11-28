import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import TextField from "./TextField";

describe("TextField component", () => {
  it("renders with label, input, and hint", () => {
    render(
      <TextField
        id="test-input"
        label="Full Name"
        hint="Enter legal full name"
        value=""
        onChange={() => {}}
      />
    );

    const input = screen.getByLabelText(/Full Name/i);
    expect(input).toBeInTheDocument();
    expect(screen.getByText("Enter legal full name")).toBeInTheDocument();
  });

  it("fires blur-triggered required validation when value is empty and required", () => {
    const setFieldError = vi.fn();
    const clearFieldError = vi.fn();

    render(
      <TextField
        id="test-required"
        label="Full Name"
        required
        value=""
        onChange={() => {}}
        setFieldError={setFieldError}
        clearFieldError={clearFieldError}
      />
    );

    const input = screen.getByLabelText(/Full Name/i);
    fireEvent.blur(input);

    expect(setFieldError).toHaveBeenCalledWith("test-required", "Full Name is required.");
  });

  it("fires blur-triggered email format validation for invalid email", () => {
    const setFieldError = vi.fn();
    const clearFieldError = vi.fn();

    render(
      <TextField
        id="test-email"
        label="Email address"
        type="email"
        required
        value="not-an-email"
        onChange={() => {}}
        setFieldError={setFieldError}
        clearFieldError={clearFieldError}
      />
    );

    const input = screen.getByLabelText(/Email address/i);
    fireEvent.blur(input);

    expect(setFieldError).toHaveBeenCalledWith("test-email", "Enter a valid email address.");
  });

  it("clears field error on change", () => {
    const clearFieldError = vi.fn();

    render(
      <TextField
        id="test-clear"
        label="Username"
        error="Invalid username"
        value="abc"
        onChange={() => {}}
        clearFieldError={clearFieldError}
      />
    );

    const input = screen.getByLabelText(/Username/i);
    fireEvent.change(input, { target: { value: "abcd" } });

    expect(clearFieldError).toHaveBeenCalledWith("test-clear");
  });

  it("executes custom validator when provided", () => {
    const setFieldError = vi.fn();
    const customValidator = vi.fn((val: string) => (val === "bad" ? "Bad value entered" : null));

    render(
      <TextField
        id="test-custom"
        label="Custom Field"
        value="bad"
        onChange={() => {}}
        validator={customValidator}
        setFieldError={setFieldError}
      />
    );

    const input = screen.getByLabelText(/Custom Field/i);
    fireEvent.blur(input);

    expect(customValidator).toHaveBeenCalledWith("bad");
    expect(setFieldError).toHaveBeenCalledWith("test-custom", "Bad value entered");
  });
});
