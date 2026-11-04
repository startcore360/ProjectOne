import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MemoryRouter } from "react-router-dom";
import {
  AuditLogPage,
  PlatformOverviewPage,
  PlatformUsersPage,
  PlansPage,
  SupportAccessPage,
  TenantsPage,
} from "./PlatformPages";

describe("Sprint 1 platform screens", () => {
  it("shows the platform overview and the sample tenant count", () => {
    render(<MemoryRouter><PlatformOverviewPage /></MemoryRouter>);
    expect(screen.getByRole("heading", { name: "Platform overview" })).toBeInTheDocument();
    expect(screen.getByText("Greenwood Public School")).toBeInTheDocument();
  });

  it("filters tenants by search text", () => {
    render(<TenantsPage />);
    fireEvent.change(screen.getByPlaceholderText("Search by institution or ID"), { target: { value: "Riverbank" } });
    expect(screen.getByText("Riverbank College")).toBeInTheDocument();
    expect(screen.queryByText("Greenwood Public School")).not.toBeInTheDocument();
  });

  it("opens the create trial tenant preview without claiming to create a record", () => {
    render(<TenantsPage />);
    fireEvent.click(screen.getByRole("button", { name: /Create trial tenant/i }));
    expect(screen.getByRole("dialog", { name: "Create trial tenant" })).toBeInTheDocument();
    expect(screen.getByText(/will not create a tenant/i)).toBeInTheDocument();
  });

  it("shows platform user management without inventing institution users", () => {
    render(<PlatformUsersPage />);
    expect(screen.getByRole("heading", { name: "Platform users" })).toBeInTheDocument();
    expect(screen.getByText("Aarav Mehta")).toBeInTheDocument();
  });

  it("shows the plan catalogue", () => {
    render(<PlansPage />);
    expect(screen.getByRole("heading", { name: "Plans & billing" })).toBeInTheDocument();
    expect(screen.getByText("Enterprise")).toBeInTheDocument();
  });

  it("shows the support-access policy and prototype session", () => {
    render(<SupportAccessPage />);
    expect(screen.getByText("Tenant data remains protected")).toBeInTheDocument();
    expect(screen.getByText("SUP-2026-00398")).toBeInTheDocument();
  });

  it("shows an empty audit state until audit records are connected", () => {
    render(<AuditLogPage />);
    expect(screen.getByText("No matching records.")).toBeInTheDocument();
  });
});
