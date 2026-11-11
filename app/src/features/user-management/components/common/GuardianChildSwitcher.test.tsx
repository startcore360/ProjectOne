import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { GuardianChildSwitcher } from "./GuardianChildSwitcher";

const get = vi.hoisted(() => vi.fn());
vi.mock("../../../../services/axios", () => ({ default: { get } }));

describe("GuardianChildSwitcher", () => {
  it("S2-36 displays the active children returned by the server endpoint", async () => {
    get.mockResolvedValue({ data: [
      { studentProfileId: "active-child", institutionId: "i1", studentCode: "STU-1", status: "ACTIVE" },
    ] });
    render(<GuardianChildSwitcher value="active-child" onChange={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("option", { name: "STU-1" })).toBeInTheDocument());
    expect(get).toHaveBeenCalledWith("/guardian/children");
  });
});
