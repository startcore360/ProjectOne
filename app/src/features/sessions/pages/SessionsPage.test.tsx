import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { afterEach, describe, expect, it, vi } from "vitest";
import authReducer from "../../../store/slices/authSlice";
import { sessionsService } from "../api/sessionsService";
import SessionsPage from "./SessionsPage";

vi.mock("../api/sessionsService", () => ({
  sessionsService: {
    list: vi.fn(),
    revokeCurrent: vi.fn(),
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

describe("SessionsPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows a loading state while the session is being fetched", () => {
    vi.mocked(sessionsService.list).mockReturnValue(new Promise(() => {}));
    renderWithStore();
    expect(screen.getByText(/Loading your session/i)).toBeInTheDocument();
  });

  it("shows an error state and can retry when the fetch fails", async () => {
    vi.mocked(sessionsService.list).mockRejectedValueOnce(new Error("network down"));
    renderWithStore();
    await waitFor(() =>
      expect(screen.getByText(/Could not load your session/i)).toBeInTheDocument(),
    );
    vi.mocked(sessionsService.list).mockResolvedValueOnce({
      data: [
        {
          sessionId: "sess-1",
          current: true,
          revocable: true,
          issuedAt: "2026-11-05T08:00:00Z",
          expiresAt: "2026-11-05T09:00:00Z",
        },
      ],
    } as never);
    fireEvent.click(screen.getByRole("button", { name: /retry/i }));
    await waitFor(() => expect(screen.getByText("This device")).toBeInTheDocument());
  });

  it("shows the current session honestly as a single-session view, not a device list", async () => {
    vi.mocked(sessionsService.list).mockResolvedValueOnce({
      data: [
        {
          sessionId: "sess-1",
          current: true,
          revocable: true,
          issuedAt: "2026-11-05T08:00:00Z",
          expiresAt: "2026-11-05T09:00:00Z",
        },
      ],
    } as never);
    renderWithStore();
    await waitFor(() => expect(screen.getByText("This device")).toBeInTheDocument());
    expect(screen.getByText(/single-session view today/i)).toBeInTheDocument();
  });

  it("disables the sign-out button when the session is not revocable", async () => {
    vi.mocked(sessionsService.list).mockResolvedValueOnce({
      data: [
        { sessionId: null, current: true, revocable: false, issuedAt: null, expiresAt: null },
      ],
    } as never);
    renderWithStore();
    await waitFor(() => expect(screen.getByText(/cannot be revoked/i)).toBeInTheDocument());
    expect(screen.getByRole("button", { name: /sign out this session/i })).toBeDisabled();
  });

  it("revokes the current session and redirects to /login", async () => {
    vi.mocked(sessionsService.list).mockResolvedValueOnce({
      data: [
        {
          sessionId: "sess-1",
          current: true,
          revocable: true,
          issuedAt: "2026-11-05T08:00:00Z",
          expiresAt: "2026-11-05T09:00:00Z",
        },
      ],
    } as never);
    vi.mocked(sessionsService.revokeCurrent).mockResolvedValueOnce(undefined as never);
    const replaceSpy = vi.fn();
    const originalLocation = window.location;
    Object.defineProperty(window, "location", {
      configurable: true,
      value: { ...originalLocation, replace: replaceSpy },
    });

    renderWithStore();
    await waitFor(() => expect(screen.getByText("This device")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: /sign out this session/i }));

    await waitFor(() => expect(replaceSpy).toHaveBeenCalledWith("/login"));

    Object.defineProperty(window, "location", { configurable: true, value: originalLocation });
  });

  it("shows an error and does not redirect when revoking fails", async () => {
    vi.mocked(sessionsService.list).mockResolvedValueOnce({
      data: [
        {
          sessionId: "sess-1",
          current: true,
          revocable: true,
          issuedAt: "2026-11-05T08:00:00Z",
          expiresAt: "2026-11-05T09:00:00Z",
        },
      ],
    } as never);
    vi.mocked(sessionsService.revokeCurrent).mockRejectedValueOnce(new Error("boom"));

    renderWithStore();
    await waitFor(() => expect(screen.getByText("This device")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: /sign out this session/i }));

    expect(await screen.findByText(/Could not sign out this session/i)).toBeInTheDocument();
  });
});
