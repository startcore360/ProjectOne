import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CreateTopUserModal } from "./CreateTopUserModal";

describe("CreateTopUserModal", () => {
  it("fires blur-triggered format validation when an invalid mobile number is entered and blurred", async () => {
    render(
      <CreateTopUserModal
        tenantId="tenant-1"
        isOpen={true}
        onClose={vi.fn()}
        onCreated={vi.fn()}
      />
    );

    const mobileInput = screen.getByLabelText("Mobile number");
    fireEvent.change(mobileInput, { target: { value: "invalid-phone" } });
    fireEvent.blur(mobileInput);

    await waitFor(() => {
      expect(mobileInput).toHaveAttribute("aria-invalid", "true");
      expect(screen.getByRole("alert")).toHaveTextContent("Include the country code, for example +919876543210.");
    });
  });

  it("fires blur-triggered validation for required name and invalid email", async () => {
    render(
      <CreateTopUserModal
        tenantId="tenant-1"
        isOpen={true}
        onClose={vi.fn()}
        onCreated={vi.fn()}
      />
    );

    const nameInput = screen.getByLabelText("Full Name (Required)");
    fireEvent.focus(nameInput);
    fireEvent.blur(nameInput);

    await waitFor(() => {
      expect(nameInput).toHaveAttribute("aria-invalid", "true");
      expect(document.getElementById("top-user-name-error")).toHaveTextContent("Full Name is required.");
    });

    const emailInput = screen.getByLabelText("Email Address (Required)");
    fireEvent.change(emailInput, { target: { value: "invalid-email" } });
    fireEvent.blur(emailInput);

    await waitFor(() => {
      expect(emailInput).toHaveAttribute("aria-invalid", "true");
      expect(document.getElementById("top-user-email-error")).toHaveTextContent("Enter a valid email address.");
    });
  });
});
