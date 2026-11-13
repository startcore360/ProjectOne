import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import ActionGate from "./ActionGate";

const access = vi.hoisted(() => ({ canAny: vi.fn(), loading: false }));
vi.mock("../features/profile/hooks/useTenantAccess", () => ({
  useTenantAccess: () => access,
}));

describe("ActionGate", () => {
  it("shows the design-system loading state while permissions are loading", () => {
    access.loading = true;
    render(<ActionGate anyOf={["TIMETABLE_VIEW"]}><p>Protected page</p></ActionGate>);
    expect(screen.getByRole("status")).toHaveTextContent("Checking access…");
    expect(screen.queryByText("Protected page")).not.toBeInTheDocument();
    access.loading = false;
  });

  it("shows a neutral design-system denied state when no required action is granted", () => {
    access.loading = false;
    access.canAny.mockReturnValue(false);
    render(<ActionGate anyOf={["TIMETABLE_VIEW"]}><p>Protected page</p></ActionGate>);
    expect(screen.getByRole("heading", { name: "Access unavailable" })).toBeInTheDocument();
    expect(screen.getByText(/current role does not have permission/i)).toBeInTheDocument();
    expect(screen.queryByText("Protected page")).not.toBeInTheDocument();
  });

  it("renders content when an allowed action is present", () => {
    access.loading = false;
    access.canAny.mockReturnValue(true);
    render(<ActionGate anyOf={["TIMETABLE_VIEW"]}><p>Protected page</p></ActionGate>);
    expect(screen.getByText("Protected page")).toBeInTheDocument();
  });
});
