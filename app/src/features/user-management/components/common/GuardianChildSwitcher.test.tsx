import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { GuardianChildSwitcher } from "./GuardianChildSwitcher";

const get = vi.hoisted(() => vi.fn());
vi.mock("../../../../services/axios", () => ({ default: { get } }));

describe("GuardianChildSwitcher", () => {
  beforeEach(() => vi.clearAllMocks());

  it("displays active linked children and keeps the selected child read-only", async () => {
    get.mockResolvedValue({ data: [
      { studentProfileId: "active-child", institutionId: "i1", studentCode: "STU-1", status: "ACTIVE" },
      { studentProfileId: "blocked-child", institutionId: "i2", studentCode: "STU-2", status: "BLOCKED" },
    ] });
    render(<GuardianChildSwitcher value="active-child" onChange={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("option", { name: "STU-1" })).toBeInTheDocument());
    expect(screen.queryByRole("option", { name: "STU-2" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "STU-1" })).toBeInTheDocument();
    expect(screen.getByText(/Academic progress, attendance, fee details and notices are not yet available/i)).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/guardian/children");
  });

  it("offers retry when the guardian child request fails", async () => {
    get.mockRejectedValueOnce(new Error("network")).mockResolvedValueOnce({ data: [
      { studentProfileId: "active-child", institutionId: "i1", studentCode: "STU-1", status: "ACTIVE" },
    ] });
    render(<GuardianChildSwitcher value="active-child" onChange={vi.fn()} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Linked children could not be loaded");
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(screen.getByRole("option", { name: "STU-1" })).toBeInTheDocument());
    expect(get).toHaveBeenCalledTimes(2);
  });
});
