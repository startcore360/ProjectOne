import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AxiosError, type AxiosResponse } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { login } from "../../../services/authManager";
import { IDLE_LOGOUT_MESSAGE } from "../../../services/authManager";
import { setLogoutReason } from "../../../services/logoutNotice";
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
});
