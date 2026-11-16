import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  SUPPORT_SESSION_EXPIRY_KEY,
  SUPPORT_SESSION_PLATFORM_ORIGIN_KEY,
  SUPPORT_SESSION_TICKET_KEY,
  SUPPORT_SESSION_TOKEN_KEY,
} from "../../services/axios";
import { SupportSessionBanner } from "./SupportSessionBanner";

function seedSupportSession() {
  const payload = btoa(JSON.stringify({ tenant_id: "tenant-1" }));
  sessionStorage.setItem(SUPPORT_SESSION_TOKEN_KEY, `e30.${payload}.signature`);
  sessionStorage.setItem(SUPPORT_SESSION_EXPIRY_KEY, new Date(Date.now() + 60 * 60 * 1000).toISOString());
  sessionStorage.setItem(SUPPORT_SESSION_TICKET_KEY, "SUP-2026-ABCDE");
  sessionStorage.setItem(SUPPORT_SESSION_PLATFORM_ORIGIN_KEY, "https://platform.edu360.local");
}

afterEach(() => {
  sessionStorage.clear();
  Object.defineProperty(window, "opener", { configurable: true, value: null });
});

describe("SupportSessionBanner", () => {
  it("shows the read-only ticket and expiry banner", () => {
    seedSupportSession();
    render(<SupportSessionBanner />);
    expect(screen.getByRole("alert")).toHaveTextContent("READ-ONLY SUPPORT SESSION");
    expect(screen.getByRole("alert")).toHaveTextContent("SUP-2026-ABCDE");
    expect(screen.getByRole("button", { name: "End session" })).toBeInTheDocument();
  });

  it("ends the local token and asks the platform tab to close the server session", () => {
    seedSupportSession();
    const postMessage = vi.fn();
    Object.defineProperty(window, "opener", { configurable: true, value: { postMessage } });
    render(<SupportSessionBanner />);

    fireEvent.click(screen.getByRole("button", { name: "End session" }));

    expect(postMessage).toHaveBeenCalledWith(
      { type: "EDU360_SUPPORT_SESSION_END" },
      "https://platform.edu360.local",
    );
    expect(sessionStorage.getItem(SUPPORT_SESSION_TOKEN_KEY)).toBeNull();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
