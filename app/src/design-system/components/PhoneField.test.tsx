import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import PhoneField from "./PhoneField";
import { validatePhoneNumber } from "./phoneUtils";

describe("PhoneField component", () => {
  it("renders with default label 'Mobile number', hint, and proper tel attributes", () => {
    render(<PhoneField id="test-phone" value="" onChange={() => {}} />);

    const input = screen.getByLabelText(/Mobile number/i);
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute("type", "tel");
    expect(input).toHaveAttribute("inputmode", "tel");
    expect(input).toHaveAttribute("placeholder", "+919876543210");
    expect(screen.getByText("Include the country code, for example +919876543210.")).toBeInTheDocument();
  });

  it("renders custom label and displays error message with role='alert'", () => {
    render(
      <PhoneField
        id="test-phone"
        label="Emergency contact mobile"
        error="Include the country code, for example +919876543210."
        value="12345"
        onChange={() => {}}
      />
    );

    expect(screen.getByLabelText(/Emergency contact mobile/i)).toBeInTheDocument();
    const errorEl = screen.getByRole("alert");
    expect(errorEl).toHaveTextContent("Include the country code, for example +919876543210.");
    expect(screen.getByLabelText(/Emergency contact mobile/i)).toHaveAttribute("aria-invalid", "true");
  });

  it("validates phone numbers correctly via validatePhoneNumber helper", () => {
    expect(validatePhoneNumber("", false)).toBeNull();
    expect(validatePhoneNumber("", true)).toBe("Mobile number is required.");
    expect(validatePhoneNumber("9876543210", false)).toBe("Include the country code, for example +919876543210.");
    expect(validatePhoneNumber("+91", false)).toBe("Include the country code, for example +919876543210.");
    expect(validatePhoneNumber("+919876543210", true)).toBeNull();
    expect(validatePhoneNumber("+14155552671", true)).toBeNull();
  });

  it("integrates with useFormErrors via setFieldError and clearFieldError on blur and change", () => {
    const setFieldError = vi.fn();
    const clearFieldError = vi.fn();

    render(
      <PhoneField
        id="mobile"
        name="mobile"
        required
        value="9876543210"
        setFieldError={setFieldError}
        clearFieldError={clearFieldError}
        onChange={() => {}}
      />
    );

    const input = screen.getByLabelText(/Mobile number/i);
    fireEvent.blur(input);
    expect(setFieldError).toHaveBeenCalledWith("mobile", "Include the country code, for example +919876543210.");

    fireEvent.change(input, { target: { value: "+919876543210" } });
    expect(clearFieldError).toHaveBeenCalledWith("mobile");
  });
});
