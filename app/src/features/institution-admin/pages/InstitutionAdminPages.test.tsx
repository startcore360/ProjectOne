import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  InstitutionUsersPage,
  InstitutionsPage,
  MissingRolesPage,
  RolesPermissionsPage,
} from "./InstitutionAdminPages";

describe("Sprint 1 university administration screens", () => {
  it("shows the institutions in the prototype register", () => {
    render(<InstitutionsPage />);
    expect(screen.getByText("Northbridge College of Science")).toBeInTheDocument();
    expect(screen.getByText("Northbridge School of Commerce")).toBeInTheDocument();
  });

  it("makes the unavailable university user records explicit", () => {
    render(<InstitutionUsersPage />);
    expect(screen.getByText(/No Northbridge University user records are loaded/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Create user/i })).toBeDisabled();
  });

  it("labels the role list as representative, not a complete catalogue", () => {
    render(<RolesPermissionsPage />);
    expect(screen.getByText(/not the complete 71-role catalogue/i)).toBeInTheDocument();
    expect(screen.getByText("University Admin")).toBeInTheDocument();
  });

  it("explains the approved V4.0 role roll-down rules", () => {
    render(<MissingRolesPage />);
    expect(screen.getByText("Decision V4-PD-01")).toBeInTheDocument();
    expect(screen.getByText("Next-level leader")).toBeInTheDocument();
    expect(screen.getByText("Next-level administrator")).toBeInTheDocument();
  });
});
