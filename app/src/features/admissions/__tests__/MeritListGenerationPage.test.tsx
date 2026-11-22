import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("../../../store/hooks", () => ({
  useAppSelector: (selector: (state: unknown) => unknown) =>
    selector({ auth: { user: { tenantId: "tenant-1" } } }),
}));
vi.mock("../../../services/axios", () => ({
  default: { get: vi.fn().mockResolvedValue({ data: [] }), post: vi.fn() },
}));

import MeritListGenerationPage from "../pages/MeritListGenerationPage";

describe("MeritListGenerationPage", () => {
  it("renders merit generation controls and an empty state", () => {
    render(<MeritListGenerationPage />);
    expect(screen.getByRole("heading", { name: "Merit lists and seat allocation" })).toBeInTheDocument();
    expect(screen.getByLabelText("Admission cycle ID")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Generate merit list" })).toBeDisabled();
  });
});
