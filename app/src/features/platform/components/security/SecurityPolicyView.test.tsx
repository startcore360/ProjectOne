/** @vitest-environment jsdom */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SecurityPolicyView } from "./SecurityPolicyView";
import { platformOpsService } from "../../api/platformOpsService";

vi.mock("../../platformAccess", () => ({
  usePlatformAccess: () => ({
    can: () => true,
  }),
}));

vi.mock("../../api/platformOpsService", () => ({
  platformOpsService: {
    getSecurityPolicy: vi.fn(),
    listSecurityIncidents: vi.fn(),
    updateSecurityPolicy: vi.fn(),
    previewSecurityPolicyRealmSync: vi.fn(),
    applySecurityPolicyRealmSync: vi.fn(),
    retryFailedSecurityPolicyRealms: vi.fn(),
    createSecurityIncident: vi.fn(),
    updateSecurityIncident: vi.fn(),
  },
}));

describe("SecurityPolicyView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("displays ErrorState when policy fetch fails and does not render editable form defaults", async () => {
    vi.mocked(platformOpsService.getSecurityPolicy).mockRejectedValueOnce(
      new Error("Failed to load security policy configuration")
    );
    vi.mocked(platformOpsService.listSecurityIncidents).mockResolvedValueOnce([]);

    render(<SecurityPolicyView />);

    await waitFor(() => {
      expect(
        screen.getByText("Failed to load security policy configuration")
      ).toBeInTheDocument();
    });

    expect(screen.getByRole("button", { name: "Retry Policy Load" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Minimum Password Length")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Update Security Policy" })).not.toBeInTheDocument();
  });

  it("renders form when policy fetch succeeds", async () => {
    vi.mocked(platformOpsService.getSecurityPolicy).mockResolvedValueOnce({
      minPasswordLength: 14,
      requireSpecialChar: true,
      requireDigit: true,
      maxFailedLogins: 4,
      lockoutMinutes: 20,
      idleSessionTimeoutMinutes: 45,
      mfaRequiredForPrivileged: true,
      defaultBackupRetentionDays: 60,
    });
    vi.mocked(platformOpsService.listSecurityIncidents).mockResolvedValueOnce([]);

    render(<SecurityPolicyView />);

    await waitFor(() => {
      expect(screen.getByLabelText(/Minimum Password Length/i)).toBeInTheDocument();
    });

    const minLenInput = screen.getByLabelText(/Minimum Password Length/i) as HTMLInputElement;
    expect(minLenInput.value).toBe("14");
    expect(screen.getByRole("button", { name: "Update Security Policy" })).toBeInTheDocument();
  });

  it("displays in-modal error when creating an incident fails without closing modal", async () => {
    const user = userEvent.setup();
    vi.mocked(platformOpsService.getSecurityPolicy).mockResolvedValueOnce({
      minPasswordLength: 12,
      requireSpecialChar: true,
      requireDigit: true,
      maxFailedLogins: 5,
      lockoutMinutes: 15,
      idleSessionTimeoutMinutes: 30,
      mfaRequiredForPrivileged: false,
      defaultBackupRetentionDays: 90,
    });
    vi.mocked(platformOpsService.listSecurityIncidents).mockResolvedValueOnce([]);
    vi.mocked(platformOpsService.createSecurityIncident).mockRejectedValueOnce(
      new Error("Incident submission failed on remote broker")
    );

    render(<SecurityPolicyView />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Log Security Incident" })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Log Security Incident" }));

    expect(screen.getByRole("heading", { name: "Log Security Incident" })).toBeInTheDocument();

    const titleInput = screen.getByLabelText(/Incident Summary \/ Title/i);
    await user.type(titleInput, "Suspicious auth probe");

    await user.click(screen.getByRole("button", { name: "Submit Incident Report" }));

    await waitFor(() => {
      expect(screen.getByText("Incident submission failed on remote broker")).toBeInTheDocument();
    });

    // Modal stays open and preserves input
    expect(screen.getByRole("heading", { name: "Log Security Incident" })).toBeInTheDocument();
    expect((screen.getByLabelText(/Incident Summary \/ Title/i) as HTMLInputElement).value).toBe("Suspicious auth probe");
  });
});
