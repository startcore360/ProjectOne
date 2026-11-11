import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import ActionGate from "./ActionGate";

const access = vi.hoisted(() => ({ canAny: vi.fn(), loading: false }));
vi.mock("../features/profile/hooks/useTenantAccess", () => ({
  useTenantAccess: () => access,
}));

describe("ActionGate", () => {
  it("shows a neutral denied state when no required action is granted", () => {
    access.loading = false;
    access.canAny.mockReturnValue(false);
    render(<ActionGate anyOf={["TIMETABLE_VIEW"]}><p>Protected page</p></ActionGate>);
    expect(screen.getByText("You don't have access to this.")).toBeInTheDocument();
    expect(screen.queryByText("Protected page")).not.toBeInTheDocument();
  });

  it("renders content when an allowed action is present", () => {
    access.loading = false;
    access.canAny.mockReturnValue(true);
    render(<ActionGate anyOf={["TIMETABLE_VIEW"]}><p>Protected page</p></ActionGate>);
    expect(screen.getByText("Protected page")).toBeInTheDocument();
  });
});
