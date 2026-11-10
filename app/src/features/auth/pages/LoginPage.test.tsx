import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AxiosError, type AxiosResponse } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { login } from "../../../services/authManager";
import { IDLE_LOGOUT_MESSAGE } from "../../../services/authManager";
import { setLogoutReason } from "../../../services/logoutNotice";
import { authService } from "../../../services/modules/authService";
import LoginPage from "./LoginPage";

vi.mock("../../../services/authManager", () => ({
  login: vi.fn(),
  IDLE_LOGOUT_MESSAGE: "You were signed out after inactivity. Please sign in again.",
}));
const mockedLogin = vi.mocked(login);

const challenge = (code: string, extra: Record<string, unknown> = {}) =>
  new AxiosError("401", "ERR_BAD_REQUEST", undefined, undefined, {
    status: 401,
    data: { code, error: "challenge", ...extra },
  } as AxiosResponse);

const renderPage = () => render(<MemoryRouter><LoginPage /></MemoryRouter>);

describe("LoginPage", () => {
  afterEach(cleanup);
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it("REG-I10-2: shows the signed-out-after-inactivity message once", () => {
    setLogoutReason(IDLE_LOGOUT_MESSAGE);
    renderPage();
    expect(screen.getByText(/signed out after inactivity/i)).toBeTruthy();
    expect(sessionStorage.getItem("logoutReason")).toBeNull();
  });

  it("REG-I01-1: MFA_REQUIRED opens the authenticator code step and resends with the code", async () => {
    mockedLogin.mockRejectedValueOnce(challenge("MFA_REQUIRED"));
    renderPage();
    fireEvent.change(document.getElementById("login-email")!, { target: { value: "a@b.test" } });
    fireEvent.change(document.getElementById("login-password")!, { target: { value: "pw-123456" } });
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));
    const codeField = await screen.findByLabelText(/Authenticator code/);

    mockedLogin.mockResolvedValueOnce({ mustChangePassword: false } as never);
    fireEvent.change(codeField, { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));
    await waitFor(() => expect(mockedLogin).toHaveBeenLastCalledWith("a@b.test", "pw-123456", "123456"));
  });

  it("REG-I01-5: MFA_ENROLMENT_REQUIRED shows the set-up link", async () => {
    mockedLogin.mockRejectedValueOnce(challenge("MFA_ENROLMENT_REQUIRED", { enrolmentUrl: "https://kc.test/realms/r/account" }));
    renderPage();
    fireEvent.change(document.getElementById("login-email")!, { target: { value: "a@b.test" } });
    fireEvent.change(document.getElementById("login-password")!, { target: { value: "pw-123456" } });
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));
    const link = await screen.findByRole("link", { name: /authenticator set-up page/i });
    expect(link.getAttribute("href")).toBe("https://kc.test/realms/r/account");
  });

  // ------------------------------------------------------------------ MFA-ENROL: in-app set-up

  const enrolmentChallenge = () =>
    challenge("MFA_ENROLMENT_REQUIRED", { enrolmentId: "a1b2c3d4e5f60718", qrCode: "iVBORw0KGgo=", secret: "ABCD EFGH" });

  const signInToSetUp = async () => {
    mockedLogin.mockRejectedValueOnce(enrolmentChallenge());
    renderPage();
    fireEvent.change(document.getElementById("login-email")!, { target: { value: "a@b.test" } });
    fireEvent.change(document.getElementById("login-password")!, { target: { value: "pw-123456" } });
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));
    await screen.findByText("Set up two-step login.");
  };

  it("MFA-ENROL-FE-1: shows the QR code and typed key in the app, not a Keycloak link", async () => {
    await signInToSetUp();
    const qr = screen.getByRole("img", { name: /QR code/i });
    expect(qr.getAttribute("src")).toBe("data:image/png;base64,iVBORw0KGgo=");
    expect(screen.getByText("ABCD EFGH")).toBeTruthy();
    expect(screen.queryByRole("link", { name: /authenticator set-up page/i })).toBeNull();
  });

  it("MFA-ENROL-FE-2: a confirmed code moves to the sign-in code step", async () => {
    const enrol = vi.spyOn(authService, "enrolMfa").mockResolvedValueOnce({
      data: { status: "ENROLLED", message: "Authenticator added. Enter the next code from your app to sign in." },
    } as never);
    await signInToSetUp();
    fireEvent.change(screen.getByLabelText(/Enter the 6-digit code/), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm and continue/i }));

    await screen.findByLabelText(/Authenticator code/);
    expect(enrol).toHaveBeenCalledWith({ email: "a@b.test", password: "pw-123456", enrolmentId: "a1b2c3d4e5f60718", code: "123456" });
    expect(screen.getByText(/Authenticator added/)).toBeTruthy();
  });

  it("MFA-ENROL-FE-3: a wrong code stays on the set-up step with the server's message", async () => {
    vi.spyOn(authService, "enrolMfa").mockRejectedValueOnce(
      new AxiosError("400", "ERR_BAD_REQUEST", undefined, undefined, {
        status: 400,
        data: { code: "INVALID_ENROLMENT_CODE", error: "That code didn't match (4 attempts left)." },
      } as AxiosResponse),
    );
    await signInToSetUp();
    fireEvent.change(screen.getByLabelText(/Enter the 6-digit code/), { target: { value: "000000" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm and continue/i }));

    await screen.findByText(/4 attempts left/);
    expect(screen.getByText("Set up two-step login.")).toBeTruthy();
  });

  it("MFA-ENROL-FE-4: an expired set-up goes back to the password step", async () => {
    vi.spyOn(authService, "enrolMfa").mockRejectedValueOnce(
      new AxiosError("410", "ERR_BAD_REQUEST", undefined, undefined, {
        status: 410,
        data: { code: "ENROLMENT_EXPIRED", error: "This set-up has expired. Sign in again to start over." },
      } as AxiosResponse),
    );
    await signInToSetUp();
    fireEvent.change(screen.getByLabelText(/Enter the 6-digit code/), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm and continue/i }));

    await screen.findByText(/has expired/);
    expect(screen.getByText("Welcome back.")).toBeTruthy();
  });

  it("MFA-ENROL-FE-5: rejects a code that is not 6 digits without calling the server", async () => {
    const enrol = vi.spyOn(authService, "enrolMfa");
    await signInToSetUp();
    fireEvent.change(screen.getByLabelText(/Enter the 6-digit code/), { target: { value: "12" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm and continue/i }));

    expect(await screen.findByText(/Enter the 6-digit code shown/)).toBeTruthy();
    expect(enrol).not.toHaveBeenCalled();
  });
});
