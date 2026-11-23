import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { GuardianManagerModal } from "./GuardianManagerModal";
import { studentApi, type GuardianLink } from "../api/studentApi";

vi.mock("../api/studentApi", () => ({
  studentApi: {
    linkGuardian: vi.fn(),
    guardianPreferences: vi.fn(),
  },
}));

const guardians: GuardianLink[] = [];
const props = { tenantId: "tenant-1", studentId: "student-1", guardians, onClose: vi.fn(), onSaved: vi.fn() };

describe("GuardianManagerModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(studentApi.linkGuardian).mockResolvedValue({ data: { studentGuardianLinkId: "link-1" } } as never);
    vi.mocked(studentApi.guardianPreferences).mockResolvedValue({ data: {} } as never);
  });

  it("completes the add-guardian journey and saves preferences against the returned link id", async () => {
    const user = userEvent.setup();
    render(<GuardianManagerModal {...props} />);

    await user.type(screen.getByLabelText("Full name (Required)"), "Meera Rao");
    await user.type(screen.getByLabelText("Mobile (E.164) (Required)"), "+919876543210");
    await user.type(screen.getByLabelText("Email"), "meera@example.com");
    await user.selectOptions(screen.getByLabelText("Relationship"), "MOTHER");
    await user.click(screen.getByLabelText("Authorized for contact"));
    await user.click(screen.getByLabelText("Emergency contact"));
    await user.click(screen.getByRole("button", { name: "Save guardian" }));

    await waitFor(() => expect(studentApi.linkGuardian).toHaveBeenCalledWith("tenant-1", "student-1", {
      fullName: "Meera Rao", mobile: "+919876543210", email: "meera@example.com", relationship: "MOTHER", isPrimary: true,
    }));
    expect(studentApi.guardianPreferences).toHaveBeenCalledWith("tenant-1", "student-1", "link-1", {
      contactAuthorized: false, emergencyContact: true, primary: true,
    });
    expect(props.onSaved).toHaveBeenCalledOnce();
    expect(props.onClose).toHaveBeenCalledOnce();
  });

  it("shows GUARDIAN_ACCOUNT_NOT_FOUND and keeps the modal open for retry", async () => {
    const user = userEvent.setup();
    vi.mocked(studentApi.linkGuardian).mockRejectedValueOnce(new Error("GUARDIAN_ACCOUNT_NOT_FOUND"));
    render(<GuardianManagerModal {...props} />);

    await user.type(screen.getByLabelText("Full name (Required)"), "Unknown Guardian");
    await user.type(screen.getByLabelText("Mobile (E.164) (Required)"), "+919876543210");
    await user.click(screen.getByRole("button", { name: "Save guardian" }));

    expect(await screen.findByText("GUARDIAN_ACCOUNT_NOT_FOUND")).toBeTruthy();
    expect(screen.getByRole("dialog")).toBeTruthy();
    expect(props.onClose).not.toHaveBeenCalled();
    expect(props.onSaved).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Save guardian" })).toBeEnabled();
  });

  it("does not claim success when the preferences update fails and permits retry", async () => {
    const user = userEvent.setup();
    vi.mocked(studentApi.guardianPreferences).mockRejectedValueOnce(new Error("preferences unavailable"));
    render(<GuardianManagerModal {...props} />);
    await user.type(screen.getByLabelText("Full name (Required)"), "Meera Rao");
    await user.type(screen.getByLabelText("Mobile (E.164) (Required)"), "+919876543210");
    await user.click(screen.getByRole("button", { name: "Save guardian" }));

    expect(await screen.findByText("preferences unavailable")).toBeTruthy();
    expect(screen.getByRole("dialog")).toBeTruthy();
    expect(props.onSaved).not.toHaveBeenCalled();
    expect(props.onClose).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Save guardian" })).toBeEnabled();
  });

  it("prevents a fourth non-blocked guardian from being added", () => {
    const three: GuardianLink[] = [
      { studentGuardianLinkId: "1", guardianProfileId: "g1", guardianName: "A", guardianMobile: "+911111111111", relationship: "FATHER", isPrimary: true, status: "ACTIVE" },
      { studentGuardianLinkId: "2", guardianProfileId: "g2", guardianName: "B", guardianMobile: "+912222222222", relationship: "MOTHER", isPrimary: false, status: "ACTIVE" },
      { studentGuardianLinkId: "3", guardianProfileId: "g3", guardianName: "C", guardianMobile: "+913333333333", relationship: "OTHER", isPrimary: false, status: "PENDING" },
    ];
    render(<GuardianManagerModal {...props} guardians={three} />);
    expect(screen.getByRole("button", { name: "Save guardian" })).toBeDisabled();
    expect(screen.getByText(/up to three non-blocked guardians/i)).toBeTruthy();
  });
});
