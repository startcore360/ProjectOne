import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { afterEach, describe, expect, it, vi } from "vitest";
import authReducer from "../../../store/slices/authSlice";
import { sessionsService, type SessionResponse } from "../api/sessionsService";
import SessionsPage from "./SessionsPage";

vi.mock("../api/sessionsService", () => ({
  sessionsService: {
    list: vi.fn(),
    revokeCurrent: vi.fn(),
    revoke: vi.fn(),
  },
}));

function renderWithStore() {
  const store = configureStore({ reducer: { auth: authReducer } });
  return render(
    <Provider store={store}>
      <SessionsPage />
    </Provider>,
  );
}

const laptop: SessionResponse = {
  sessionId: "sess-laptop",
  current: true,
  revocable: true,
  issuedAt: "2026-11-09T08:00:00Z",
  expiresAt: "2026-11-09T09:00:00Z",
  deviceType: "COMPUTER",
  deviceName: "Chrome on Windows",
  ipAddress: "203.0.113.7",
  lastActiveAt: "2026-11-09T08:30:00Z",
};
const phone: SessionResponse = {
  sessionId: "sess-phone",
  current: false,
  revocable: true,
  issuedAt: "2026-11-08T18:00:00Z",
  expiresAt: null,
  deviceType: "PHONE",
  deviceName: "Safari on iPhone",
  ipAddress: "198.51.100.4",
  lastActiveAt: "2026-11-09T07:00:00Z",
};
const legacy: SessionResponse = {
  sessionId: "sess-old",
  current: false,
  revocable: true,
  issuedAt: "2026-11-07T10:00:00Z",
  expiresAt: null,
  deviceType: "UNKNOWN",
  deviceName: null,
  ipAddress: null,
  lastActiveAt: "2026-11-07T11:00:00Z",
};

describe("SessionsPage (AUTH_DEVICES_MANAGE, SES-01)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows a loading state while sessions are being fetched", () => {
    vi.mocked(sessionsService.list).mockReturnValue(new Promise(() => {}));
    renderWithStore();
    expect(screen.getByText(/Loading your sessions/i)).toBeInTheDocument();
  });

  it("shows an error state and can retry when the fetch fails", async () => {
    vi.mocked(sessionsService.list).mockRejectedValueOnce(new Error("network down"));
    renderWithStore();
    await waitFor(() => expect(screen.getByText(/Could not load your sessions/i)).toBeInTheDocument());
    vi.mocked(sessionsService.list).mockResolvedValueOnce({ data: [laptop] } as never);
    fireEvent.click(screen.getByRole("button", { name: /retry/i }));
    await waitFor(() => expect(screen.getByText("Chrome on Windows")).toBeInTheDocument());
  });

  it("lists every signed-in device, marking this device", async () => {
    vi.mocked(sessionsService.list).mockResolvedValueOnce({ data: [laptop, phone, legacy] } as never);
    renderWithStore();
    await waitFor(() => expect(screen.getByText("Signed-in devices")).toBeInTheDocument());
    expect(screen.getByRole("heading", { name: /Other active devices/ })).toHaveTextContent("2");
    expect(screen.getByText("Chrome on Windows")).toBeInTheDocument();
    expect(screen.getByText("Safari on iPhone")).toBeInTheDocument();
    expect(screen.getByText("Unknown device")).toBeInTheDocument();
    expect(screen.getByText("This device")).toBeInTheDocument();
    expect(screen.getByText("198.51.100.4")).toBeInTheDocument();
    expect(screen.getByText(/before device details were recorded/i)).toBeInTheDocument();
  });

  it("says so when this is the only device", async () => {
    vi.mocked(sessionsService.list).mockResolvedValueOnce({ data: [laptop] } as never);
    renderWithStore();
    await waitFor(() => expect(screen.getByText("Signed-in device")).toBeInTheDocument());
    expect(screen.getByText(/not signed in anywhere else/i)).toBeInTheDocument();
  });

  it("signs out another device after confirmation and removes it from the list", async () => {
    vi.mocked(sessionsService.list).mockResolvedValueOnce({ data: [laptop, phone] } as never);
    vi.mocked(sessionsService.revoke).mockResolvedValueOnce(undefined as never);
    renderWithStore();
    await waitFor(() => expect(screen.getByText("Safari on iPhone")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "Sign out Safari on iPhone" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/This device stays signed in/i)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole("button", { name: "Sign out" }));

    await waitFor(() => expect(sessionsService.revoke).toHaveBeenCalledWith("sess-phone"));
    await waitFor(() => expect(screen.queryByText("Safari on iPhone", { selector: "h3" })).not.toBeInTheDocument());
    expect(screen.getByText(/has been signed out/i)).toBeInTheDocument();
    expect(sessionsService.revokeCurrent).not.toHaveBeenCalled();
  });

  it("shows an error and keeps the device when signing out another device fails", async () => {
    vi.mocked(sessionsService.list).mockResolvedValueOnce({ data: [laptop, phone] } as never);
    vi.mocked(sessionsService.revoke).mockRejectedValueOnce(new Error("boom"));
    renderWithStore();
    await waitFor(() => expect(screen.getByText("Safari on iPhone")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "Sign out Safari on iPhone" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Sign out" }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/Could not sign out that device/i));
    expect(screen.getByText("Safari on iPhone", { selector: "h3" })).toBeInTheDocument();
  });

  it("signs out this device and redirects to /login", async () => {
    vi.mocked(sessionsService.list).mockResolvedValueOnce({ data: [laptop, phone] } as never);
    vi.mocked(sessionsService.revokeCurrent).mockResolvedValueOnce(undefined as never);
    const replaceSpy = vi.fn();
    const originalLocation = window.location;
    Object.defineProperty(window, "location", {
      configurable: true,
      value: { ...originalLocation, replace: replaceSpy },
    });

    renderWithStore();
    await waitFor(() => expect(screen.getByText("This device")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Sign out this device" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Sign out" }));

    await waitFor(() => expect(replaceSpy).toHaveBeenCalledWith("/login"));
    expect(sessionsService.revoke).not.toHaveBeenCalled();

    Object.defineProperty(window, "location", { configurable: true, value: originalLocation });
  });

  it("shows the laptop and the phone both as active now when both are in use", async () => {
    const phoneInUse = { ...phone, lastActiveAt: new Date().toISOString() };
    vi.mocked(sessionsService.list).mockResolvedValueOnce({ data: [laptop, phoneInUse, legacy] } as never);
    renderWithStore();
    await waitFor(() => expect(screen.getByText("Safari on iPhone")).toBeInTheDocument());

    const phoneCard = screen.getByRole("article", { name: "Safari on iPhone" });
    expect(within(phoneCard).getByText("Active now")).toBeInTheDocument();
    const legacyCard = screen.getByRole("article", { name: "Unknown device" });
    expect(within(legacyCard).getByText("Idle")).toBeInTheDocument();
    // Summary: 2 of the 3 signed-in devices are active now.
    const summary = screen.getByRole("region", { name: "Active devices summary" });
    const activeKpi = within(summary).getByText("Active now").closest<HTMLElement>(".kpi");
    expect(activeKpi ? within(activeKpi).getByText("2") : within(summary).getByText("Active now").previousElementSibling).toHaveTextContent("2");
  });

  it("signs out all other devices at once and keeps this device", async () => {
    vi.mocked(sessionsService.list).mockResolvedValueOnce({ data: [laptop, phone, legacy] } as never);
    vi.mocked(sessionsService.revoke).mockResolvedValue(undefined as never);
    renderWithStore();
    await waitFor(() => expect(screen.getByText("Safari on iPhone")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "Sign out all other devices" }));
    fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Sign out" }));

    await waitFor(() => expect(screen.getByText(/Signed out of 2 other devices/i)).toBeInTheDocument());
    expect(sessionsService.revoke).toHaveBeenCalledWith("sess-phone");
    expect(sessionsService.revoke).toHaveBeenCalledWith("sess-old");
    expect(sessionsService.revokeCurrent).not.toHaveBeenCalled();
    expect(screen.getByText("Chrome on Windows")).toBeInTheDocument();
    expect(screen.getByText(/not signed in anywhere else/i)).toBeInTheDocument();
  });

  it("disables sign-out when a session has no session id", async () => {
    vi.mocked(sessionsService.list).mockResolvedValueOnce({
      data: [{ ...laptop, sessionId: null, revocable: false }],
    } as never);
    renderWithStore();
    await waitFor(() => expect(screen.getByText(/can't be signed out from here/i)).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Sign out this device" })).toBeDisabled();
  });
});
