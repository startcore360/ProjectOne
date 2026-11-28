import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ProfileDetailsTab } from "./ProfileDetailsTab";

describe("ProfileDetailsTab", () => {
  const mockProfile = {
    id: "user-1",
    displayName: "Jane Doe",
    email: "jane.doe@example.com",
    mobile: "+919876543210",
    userType: "STAFF" as const,
    roleName: "Teacher",
    institutionName: "Main Campus",
    departmentName: "Science",
    tenantId: "tenant-1",
    userAccountId: "ua-1",
    platformUserId: null,
    platformRole: null,
    photoUrl: null,
  };

  it("fires blur-triggered format validation when an invalid mobile number is entered and blurred", async () => {
    render(
      <ProfileDetailsTab
        profile={mockProfile}
        isPlatform={false}
        onSaved={vi.fn()}
        onDirtyChange={vi.fn()}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: /Edit/i }));

    const mobileInput = screen.getByLabelText("Mobile number");
    fireEvent.change(mobileInput, { target: { value: "invalid-phone" } });
    fireEvent.blur(mobileInput);

    await waitFor(() => {
      expect(mobileInput).toHaveAttribute("aria-invalid", "true");
      expect(document.getElementById("profile-mobile-error")).toHaveTextContent("Include the country code, for example +919876543210.");
    });
  });

  it("fires blur-triggered validation when display name is cleared and blurred", async () => {
    render(
      <ProfileDetailsTab
        profile={mockProfile}
        isPlatform={false}
        onSaved={vi.fn()}
        onDirtyChange={vi.fn()}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: /Edit/i }));

    const nameInput = screen.getByLabelText("Display name (Required)");
    fireEvent.change(nameInput, { target: { value: "" } });
    fireEvent.blur(nameInput);

    await waitFor(() => {
      expect(nameInput).toHaveAttribute("aria-invalid", "true");
      expect(document.getElementById("profile-display-name-error")).toHaveTextContent("Display name is required.");
    });
  });
});
