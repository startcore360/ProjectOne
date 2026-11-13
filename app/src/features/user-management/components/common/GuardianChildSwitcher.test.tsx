import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { GuardianChildSwitcher } from "./GuardianChildSwitcher";

const get = vi.hoisted(() => vi.fn());
vi.mock("../../../../services/axios", () => ({ default: { get } }));

describe("GuardianChildSwitcher", () => {
  beforeEach(() => vi.clearAllMocks());

  it("loads only active linked children from the tenant-scoped guardian endpoint", async () => {
    get.mockResolvedValue({ data: [
      { studentProfileId: "active-child", institutionId: "i1", studentCode: "STU-1", status: "ACTIVE" },
      { studentProfileId: "blocked-child", institutionId: "i2", studentCode: "STU-2", status: "BLOCKED" },
    ] });
    render(<GuardianChildSwitcher tenantId="tenant-1" value="active-child" onChange={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("option", { name: "STU-1" })).toBeInTheDocument());
    expect(screen.queryByRole("option", { name: "STU-2" })).not.toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Selected child details" })).toHaveTextContent("STU-1");
    expect(screen.getByText(/Academic progress, attendance/i)).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/tenants/tenant-1/guardian/children");
  });

  it("offers retry when the guardian child request fails", async () => {
    get.mockRejectedValueOnce(new Error("network")).mockResolvedValueOnce({ data: [
      { studentProfileId: "active-child", institutionId: "i1", studentCode: "STU-1", status: "ACTIVE" },
    ] });
    render(<GuardianChildSwitcher tenantId="tenant-1" value="active-child" onChange={vi.fn()} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Linked children could not be loaded");
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(screen.getByRole("option", { name: "STU-1" })).toBeInTheDocument());
    expect(get).toHaveBeenCalledTimes(2);
  });
});
