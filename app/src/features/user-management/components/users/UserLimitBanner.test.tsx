import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { userManagementService } from "../../api/userManagementService";
import { UserLimitBanner } from "./UserLimitBanner";

const usage = (active: number, limit: number | null) => ({
  data: { limit, active, percent: limit ? Math.floor((active * 100) / limit) : 0 },
});

describe("UserLimitBanner (E1-S5)", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("shows nothing below 90%", async () => {
    const get = vi.spyOn(userManagementService, "getUserLimit").mockResolvedValue(usage(800, 1000) as never);
    const { container } = render(<UserLimitBanner tenantId="t1" />);
    await vi.waitFor(() => expect(get).toHaveBeenCalled());
    expect(container.textContent).toBe("");
  });

  it("warns at 90% and sends an upgrade request", async () => {
    vi.spyOn(userManagementService, "getUserLimit").mockResolvedValue(usage(900, 1000) as never);
    const request = vi.spyOn(userManagementService, "requestUpgrade").mockResolvedValue({ data: {} } as never);
    render(<UserLimitBanner tenantId="t1" />);

    expect(await screen.findByText(/900 of 1000 accounts \(90%\)/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: /Request upgrade/ }));
    expect(await screen.findByText(/upgrade request was sent/)).toBeTruthy();
    expect(request).toHaveBeenCalledWith("t1");
  });

  it("says plainly when the limit is full", async () => {
    vi.spyOn(userManagementService, "getUserLimit").mockResolvedValue(usage(1000, 1000) as never);
    render(<UserLimitBanner tenantId="t1" />);
    expect(await screen.findByText(/User limit reached: 1000 of 1000/)).toBeTruthy();
  });

  it("shows nothing when the tenant has no limit", async () => {
    const get = vi.spyOn(userManagementService, "getUserLimit").mockResolvedValue(usage(5000, null) as never);
    const { container } = render(<UserLimitBanner tenantId="t1" />);
    await vi.waitFor(() => expect(get).toHaveBeenCalled());
    expect(container.textContent).toBe("");
  });
});
