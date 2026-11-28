import { useState } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CreateTenantModal, type CreateTenantDraft } from "./CreateTenantModal";

describe("CreateTenantModal", () => {
  const initialDraft: CreateTenantDraft = {
    tenantName: "Test University",
    tenantType: "UNIVERSITY_GROUP",
    planId: "plan-1",
    subdomain: "testuni",
    isolationMode: "SHARED",
    firstUserRole: "ADMIN",
    firstAdminName: "Admin User",
    firstAdminEmail: "admin@testuni.edu",
    firstAdminMobile: "",
    firstAdminNationalIdLast4: "1234",
    retentionMonths: 12,
    backupRetentionDays: 30,
  };

  function TestWrapper() {
    const [draft, setDraft] = useState(initialDraft);
    return (
      <CreateTenantModal
        isOpen={true}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
        draft={draft}
        setDraft={setDraft}
        plans={[]}
        creating={false}
        createError=""
        onNameChange={vi.fn()}
      />
    );
  }

  it("fires blur-triggered format validation when an invalid mobile number is entered and blurred", async () => {
    render(<TestWrapper />);

    const mobileInput = screen.getByLabelText(/Primary contact mobile/i);
    fireEvent.change(mobileInput, { target: { value: "invalid-phone" } });
    fireEvent.blur(mobileInput);

    await waitFor(() => {
      expect(mobileInput).toHaveAttribute("aria-invalid", "true");
      expect(screen.getByRole("alert")).toHaveTextContent("Include the country code, for example +919876543210.");
    });
  });

  it("fires blur-triggered validation for admin email and national id last 4", async () => {
    render(<TestWrapper />);

    const emailInput = screen.getByLabelText("Primary administrator email (Required)");
    fireEvent.change(emailInput, { target: { value: "bad-email" } });
    fireEvent.blur(emailInput);

    await waitFor(() => {
      expect(emailInput).toHaveAttribute("aria-invalid", "true");
      expect(document.getElementById("tenant-admin-email-error")).toHaveTextContent("Enter a valid email address.");
    });

    const nidInput = screen.getByLabelText("Aadhaar / National ID (Last 4 digits)");
    fireEvent.change(nidInput, { target: { value: "12" } });
    fireEvent.blur(nidInput);

    await waitFor(() => {
      expect(nidInput).toHaveAttribute("aria-invalid", "true");
      expect(document.getElementById("tenant-admin-national-id-error")).toHaveTextContent("Enter exactly 4 numeric digits.");
    });
  });
});
