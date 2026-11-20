import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import ActionGate from "./ActionGate";

const access = vi.hoisted(() => ({ canAny: vi.fn(), loading: false, error: null as string | null }));
vi.mock("../features/profile/hooks/useTenantAccess", () => ({
  useTenantAccess: () => access,
}));
vi.mock("../features/profile/api/tenantPermissionsService", () => ({
  tenantPermissionsService: { reload: vi.fn() },
}));

describe("ActionGate", () => {
  it("shows the design-system loading state while permissions are loading", () => {
    access.loading = true;
    render(<ActionGate anyOf={["TIMETABLE_VIEW"]}><p>Protected page</p></ActionGate>);
    expect(screen.getByRole("status")).toHaveTextContent("Checking access…");
    expect(screen.queryByText("Protected page")).not.toBeInTheDocument();
    access.loading = false;
  });

  it("renders a retryable error state when permission fetch fails (EDU-UX-005, EDU-UX-026)", () => {
    access.loading = false;
    access.error = "Failed to load permissions";
    render(<ActionGate anyOf={["TIMETABLE_VIEW"]}><p>Protected page</p></ActionGate>);
    expect(screen.getByRole("heading", { name: "Could not verify access" })).toBeInTheDocument();
    expect(screen.getByText("Failed to load permissions")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
    expect(screen.queryByText("Protected page")).not.toBeInTheDocument();
    access.error = null;
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
