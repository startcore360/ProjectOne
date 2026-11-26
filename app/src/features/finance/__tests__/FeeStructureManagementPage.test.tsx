import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FeeStructureManagementPage from "../pages/FeeStructureManagementPage";

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  useAppSelector: vi.fn(),
}));

vi.mock("../../../services/axios", () => ({ default: { get: mocks.get, post: mocks.post } }));
vi.mock("../../../store/hooks", () => ({ useAppSelector: mocks.useAppSelector }));

describe("FeeStructureManagementPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.useAppSelector.mockReturnValue("tenant-123");
    mocks.get.mockResolvedValue({ data: [] });
  });

  it("shows the empty state after loading fee structures", async () => {
    render(<FeeStructureManagementPage />);
    expect(await screen.findByText("No fee structures found for this scope.")).toBeInTheDocument();
    expect(mocks.get).toHaveBeenCalledWith("/tenants/tenant-123/fee-structures", { params: undefined });
  });

  it("validates fee lines before sending a create request", async () => {
    const user = userEvent.setup();
    render(<FeeStructureManagementPage />);

    await user.type(screen.getByLabelText("Institution ID"), "institution-123");
    await user.type(screen.getByLabelText("Academic session ID"), "session-123");
    await user.type(screen.getByLabelText("Program year ID"), "program-year-123");
    await user.type(screen.getByLabelText("Fee policy ID"), "policy-123");
    await user.click(screen.getByRole("button", { name: "Create draft" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Fee lines must contain a fee-head ID");
    expect(mocks.post).not.toHaveBeenCalled();
  });
});
