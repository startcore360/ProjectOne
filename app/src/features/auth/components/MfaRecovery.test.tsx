import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AxiosError, type AxiosResponse } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { authService } from "../../../services/modules/authService";
import { BackupCodesSection } from "./BackupCodesSection";
import { MfaResetDialog } from "./MfaResetDialog";

describe("MfaResetDialog (MFA-01)", () => {
  afterEach(cleanup);

  it("requires a reason before resetting, and says the password is not changed", () => {
    const onConfirm = vi.fn();
    render(<MfaResetDialog isOpen personName="Asha Rao" busy={false} onConfirm={onConfirm} onCancel={vi.fn()} />);

    expect(screen.getByText("Reset two-step login for Asha Rao?")).toBeTruthy();
    expect(screen.getByText(/Their password does not change/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Reset two-step login" }));
    expect(screen.getByText(/at least 5 characters/)).toBeTruthy();
    expect(onConfirm).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "  Lost phone, checked in person  " } });
    fireEvent.click(screen.getByRole("button", { name: "Reset two-step login" }));
    expect(onConfirm).toHaveBeenCalledWith("Lost phone, checked in person");
  });

  it("shows the server's error, e.g. when someone tries to reset their own", () => {
    render(
      <MfaResetDialog isOpen personName="Me" busy={false} error="You cannot reset your own two-step login."
        onConfirm={vi.fn()} onCancel={vi.fn()} />,
    );
    expect(screen.getByText("You cannot reset your own two-step login.")).toBeTruthy();
  });
});

describe("BackupCodesSection (MFA-01)", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("asks for the current password before issuing codes", async () => {
    const issue = vi.spyOn(authService, "issueRecoveryCodes");
    render(<BackupCodesSection />);
    fireEvent.click(screen.getByRole("button", { name: /Generate new codes/ }));
    fireEvent.click(screen.getByRole("button", { name: /Get new backup codes/ }));
    expect(await screen.findByText("Enter your current password.")).toBeTruthy();
    expect(issue).not.toHaveBeenCalled();
  });

  it("shows the new codes once, and confirms the old ones stopped working", async () => {
    vi.spyOn(authService, "issueRecoveryCodes").mockResolvedValueOnce({
      data: { recoveryCodes: ["AAAAA-BBBBB", "CCCCC-DDDDD"] },
    } as never);
    render(<BackupCodesSection />);
    fireEvent.click(screen.getByRole("button", { name: /Generate new codes/ }));
    fireEvent.change(screen.getByLabelText(/Current password/), { target: { value: "pw-123456" } });
    fireEvent.click(screen.getByRole("button", { name: /Get new backup codes/ }));

    expect(await screen.findByText("AAAAA-BBBBB")).toBeTruthy();
    fireEvent.click(screen.getByLabelText(/I have saved these codes/));
    fireEvent.click(screen.getByRole("button", { name: "Done" }));
    await waitFor(() => expect(screen.queryByText("AAAAA-BBBBB")).toBeNull());
    expect(screen.getByText(/old codes no longer work/)).toBeTruthy();
  });

  it("explains when there is no authenticator to back up", async () => {
    vi.spyOn(authService, "issueRecoveryCodes").mockRejectedValueOnce(
      new AxiosError("409", "ERR_BAD_REQUEST", undefined, undefined, {
        status: 409,
        data: { code: "NO_AUTHENTICATOR", error: "Set up two-step login first. Backup codes only work with an authenticator." },
      } as AxiosResponse),
    );
    render(<BackupCodesSection />);
    fireEvent.click(screen.getByRole("button", { name: /Generate new codes/ }));
    fireEvent.change(screen.getByLabelText(/Current password/), { target: { value: "pw-123456" } });
    fireEvent.click(screen.getByRole("button", { name: /Get new backup codes/ }));
    expect(await screen.findByText(/Set up two-step login first/)).toBeTruthy();
  });

  it("does not show technical 404 details when backup codes are unavailable", async () => {
    vi.spyOn(authService, "issueRecoveryCodes").mockRejectedValueOnce(
      new AxiosError("404", "ERR_BAD_REQUEST", undefined, undefined, {
        status: 404,
        data: { error: "Resource not found: api/auth/mfa/recovery-codes" },
      } as AxiosResponse),
    );
    render(<BackupCodesSection />);
    fireEvent.click(screen.getByRole("button", { name: /Generate new codes/ }));
    fireEvent.change(screen.getByLabelText(/Current password/), { target: { value: "pw-123456" } });
    fireEvent.click(screen.getByRole("button", { name: /Get new backup codes/ }));

    expect(await screen.findByText("Backup codes are not available right now. Please try again later.")).toBeTruthy();
    expect(screen.queryByText(/Resource not found/)).toBeNull();
  });
});
