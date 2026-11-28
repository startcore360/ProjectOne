import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { userManagementService } from "../../api/userManagementService";
import { CreateUserModal } from "./CreateUserModal";

vi.mock("../../api/userManagementService", () => ({
  userManagementService: {
    createUser: vi.fn(),
  },
}));

vi.mock("../../../institution-admin/api/institutionService", () => ({
  institutionService: {
    listInstitutions: vi.fn().mockResolvedValue({ items: [] }),
    listDepartments: vi.fn().mockResolvedValue([]),
  },
}));

const tenantId = "11111111-1111-1111-1111-111111111111";

describe("CreateUserModal - Duplicate Error Field Highlighting", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("highlights the email field inline when duplicate email error is returned", async () => {
    const duplicateEmailMessage = "A user with email 'anita.sharma@northbridge.edu.in' already exists in this tenant.";
    vi.mocked(userManagementService.createUser).mockRejectedValueOnce({
      isAxiosError: true,
      response: {
        status: 409,
        data: {
          code: "DUPLICATE_RESOURCE",
          message: duplicateEmailMessage,
          fieldErrors: {
            email: duplicateEmailMessage,
          },
        },
      },
    });

    render(<CreateUserModal tenantId={tenantId} isOpen onClose={vi.fn()} onCreated={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/Full name/i), { target: { value: "Anita Sharma" } });
    fireEvent.change(screen.getByLabelText(/Email address/i), { target: { value: "anita.sharma@northbridge.edu.in" } });
    fireEvent.change(screen.getByLabelText(/Mobile number/i), { target: { value: "+919876543210" } });

    fireEvent.click(screen.getByRole("button", { name: "Create user" }));

    await waitFor(() => {
      const emailInput = screen.getByLabelText(/Email address/i);
      expect(emailInput).toHaveAttribute("aria-invalid", "true");
    });

    // Verify the inline error message is rendered
    expect(screen.getByText(duplicateEmailMessage)).toBeInTheDocument();

    // Verify the generic danger banner notice is NOT rendered
    expect(document.querySelector(".eds-notice")).not.toBeInTheDocument();
  });

  it("highlights the mobile field inline when duplicate guardian mobile error is returned", async () => {
    const duplicateMobileMessage = "A guardian with mobile number '+919876543210' already exists in this tenant.";
    vi.mocked(userManagementService.createUser).mockRejectedValueOnce({
      isAxiosError: true,
      response: {
        status: 409,
        data: {
          code: "DUPLICATE_RESOURCE",
          message: duplicateMobileMessage,
          fieldErrors: {
            mobile: duplicateMobileMessage,
          },
        },
      },
    });

    render(<CreateUserModal tenantId={tenantId} isOpen onClose={vi.fn()} onCreated={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/Full name/i), { target: { value: "Anita Sharma" } });
    fireEvent.change(screen.getByLabelText(/Email address/i), { target: { value: "anita.sharma@northbridge.edu.in" } });
    fireEvent.change(screen.getByLabelText(/Mobile number/i), { target: { value: "+919876543210" } });

    fireEvent.click(screen.getByRole("button", { name: "Create user" }));

    await waitFor(() => {
      const mobileInput = screen.getByLabelText(/Mobile number/i);
      expect(mobileInput).toHaveAttribute("aria-invalid", "true");
    });

    // Verify the inline error message is rendered
    expect(screen.getByText(duplicateMobileMessage)).toBeInTheDocument();

    // Verify the generic danger banner notice is NOT rendered
    expect(document.querySelector(".eds-notice")).not.toBeInTheDocument();
  });

  it("fires blur-triggered format validation when an invalid mobile number is entered and blurred", async () => {
    render(<CreateUserModal tenantId={tenantId} isOpen onClose={vi.fn()} onCreated={vi.fn()} />);

    const mobileInput = screen.getByLabelText(/Mobile number/i);
    fireEvent.change(mobileInput, { target: { value: "invalid-phone" } });
    fireEvent.blur(mobileInput);

    await waitFor(() => {
      expect(mobileInput).toHaveAttribute("aria-invalid", "true");
      expect(screen.getByRole("alert")).toHaveTextContent("Include the country code, for example +919876543210.");
    });
  });

  it("fires blur-triggered format validation when an invalid email is entered and blurred", async () => {
    render(<CreateUserModal tenantId={tenantId} isOpen onClose={vi.fn()} onCreated={vi.fn()} />);

    const emailInput = screen.getByLabelText(/Email address/i);
    fireEvent.change(emailInput, { target: { value: "invalid-email" } });
    fireEvent.blur(emailInput);

    await waitFor(() => {
      expect(emailInput).toHaveAttribute("aria-invalid", "true");
      expect(screen.getByRole("alert")).toHaveTextContent("Enter a valid email address.");
    });
  });

  it("fires blur-triggered required validation when full name is left empty and blurred", async () => {
    render(<CreateUserModal tenantId={tenantId} isOpen onClose={vi.fn()} onCreated={vi.fn()} />);

    const nameInput = screen.getByLabelText(/Full name/i);
    fireEvent.blur(nameInput);

    await waitFor(() => {
      expect(nameInput).toHaveAttribute("aria-invalid", "true");
      expect(screen.getByRole("alert")).toHaveTextContent("Full name is required.");
    });
  });
});
