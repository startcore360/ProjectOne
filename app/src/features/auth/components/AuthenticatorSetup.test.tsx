import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { AuthenticatorSetup } from "./AuthenticatorSetup";
import { authenticatorLink } from "./authenticatorLink";

const enrolment = { enrolmentId: "e1", qrCode: "iVBORw0KGgo=", secret: "gezd gnbv gy3t qojq" };

describe("authenticatorLink", () => {
  it("builds the standard otpauth link with the realm's code settings", () => {
    const link = authenticatorLink("gezd gnbv gy3t qojq", "owner@edu360-platform.local");
    expect(link.startsWith("otpauth://totp/EDU%20360%3Aowner%40edu360-platform.local?")).toBe(true);
    const params = new URL(link.replace("otpauth://", "https://")).searchParams;
    expect(params.get("secret")).toBe("GEZDGNBVGY3TQOJQ");
    expect(params.get("issuer")).toBe("EDU 360");
    expect(params.get("algorithm")).toBe("SHA1");
    expect(params.get("digits")).toBe("6");
    expect(params.get("period")).toBe("30");
    expect(link).not.toContain("+");
  });

  it("works without an account name", () => {
    expect(authenticatorLink("ABC").startsWith("otpauth://totp/EDU%20360?secret=ABC")).toBe(true);
  });
});

describe("AuthenticatorSetup", () => {
  it("offers a one-tap link into the authenticator app next to the QR code and key", () => {
    render(<AuthenticatorSetup enrolment={enrolment} accountName="a@b.test" busy={false} onConfirm={vi.fn()} onCancel={vi.fn()} />);

    // hidden: true - the button is shown on touch screens only (CSS), and the test browser is not one.
    const link = screen.getByRole("link", { name: "Open in authenticator app", hidden: true });
    expect(link.getAttribute("href")).toBe(authenticatorLink(enrolment.secret, "a@b.test"));
    expect(screen.getByAltText("QR code to add EDU 360 to your authenticator app")).toBeInTheDocument();
    expect(screen.getByLabelText("Set-up key")).toHaveTextContent("gezd gnbv gy3t qojq");
  });
});
