import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ScopeFields } from "./ScopeFields";
import type { ActingScope } from "../../api/userManagementService";

describe("ScopeFields", () => {
  it("uses the accessible design-system scope selector", () => {
    const scope: ActingScope = { scopeType: "TENANT" };
    const onChange = vi.fn();
    render(<ScopeFields scope={scope} onChange={onChange} idPrefix="role-editor" />);

    const select = screen.getByRole("combobox", { name: "Acting scope" });
    expect(select).toHaveAttribute("id", "role-editor-scope-type");
    expect(select).toHaveValue("TENANT");

    fireEvent.change(select, { target: { value: "INSTITUTION" } });
    expect(onChange).toHaveBeenCalledWith({ ...scope, scopeType: "INSTITUTION" });
  });

  it("shows the scope-specific identifiers when required", () => {
    const scope: ActingScope = { scopeType: "DEPARTMENT" };
    render(<ScopeFields scope={scope} onChange={vi.fn()} idPrefix="role-editor" />);

    expect(screen.getByLabelText("Institution ID (Required)")).toBeInTheDocument();
    expect(screen.getByLabelText("Department ID (Required)")).toBeInTheDocument();
  });
});
