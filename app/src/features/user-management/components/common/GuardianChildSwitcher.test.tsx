import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { GuardianChildSwitcher } from "./GuardianChildSwitcher";

const get = vi.hoisted(() => vi.fn());
vi.mock("../../../../services/axios", () => ({ default: { get } }));

describe("GuardianChildSwitcher", () => {
  it("S2-36 displays active linked children and keeps the selected child read-only", async () => {
    get.mockResolvedValue({ data: [
      { studentProfileId: "active-child", institutionId: "i1", studentCode: "STU-1", status: "ACTIVE" },
      { studentProfileId: "blocked-child", institutionId: "i2", studentCode: "STU-2", status: "BLOCKED" },
    ] });
    render(<GuardianChildSwitcher value="active-child" onChange={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("option", { name: "STU-1" })).toBeInTheDocument());
    expect(screen.queryByRole("option", { name: "STU-2" })).not.toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Selected child details" })).toHaveTextContent("STU-1");
    expect(screen.getByText("Child information is read-only.")).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/guardian/children");
  });
});
