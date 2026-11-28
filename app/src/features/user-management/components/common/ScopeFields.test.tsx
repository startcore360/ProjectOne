import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ScopeFields } from "./ScopeFields";
import type { ActingScope } from "../../api/userManagementService";
import { institutionService } from "../../../institution-admin/api/institutionService";

vi.mock("../../../institution-admin/api/institutionService", () => ({
  institutionService: {
    listInstitutions: vi.fn(),
    listDepartments: vi.fn(),
  },
}));

const tenantId = "tenant-1";

describe("ScopeFields", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(institutionService.listInstitutions).mockResolvedValue({
      items: [{
        institutionId: "inst-1",
        institutionName: "Northbridge College",
        institutionCode: "NBC",
      }],
    } as never);
    vi.mocked(institutionService.listDepartments).mockResolvedValue([{
      departmentId: "dept-1",
      departmentName: "Physics",
      departmentCode: "PHY",
      institutionId: "inst-1",
    }] as never);
  });

  it("uses the accessible design-system scope selector and clears stale identifiers when scope changes", () => {
    const scope: ActingScope = { scopeType: "TENANT" };
    const onChange = vi.fn();
    render(<ScopeFields tenantId={tenantId} scope={scope} onChange={onChange} idPrefix="role-editor" />);

    const select = screen.getByRole("combobox", { name: "Acting scope" });
    expect(select).toHaveAttribute("id", "role-editor-scope-type");
    expect(select).toHaveValue("TENANT");

    fireEvent.change(select, { target: { value: "INSTITUTION" } });
    expect(onChange).toHaveBeenCalledWith({ scopeType: "INSTITUTION" });
  });

  it("offers institution and department names while retaining IDs as option values", async () => {
    const scope: ActingScope = { scopeType: "DEPARTMENT", institutionId: "inst-1" };
    render(<ScopeFields tenantId={tenantId} scope={scope} onChange={vi.fn()} idPrefix="role-editor" />);

    await waitFor(() => {
      expect(institutionService.listInstitutions).toHaveBeenCalledWith(tenantId);
      expect(institutionService.listDepartments).toHaveBeenCalledWith(tenantId, "inst-1");
    });
    expect(screen.getByRole("option", { name: "Northbridge College · NBC" })).toHaveValue("inst-1");
    expect(screen.getByRole("option", { name: "Physics · PHY" })).toHaveValue("dept-1");
    expect(screen.queryByText(/UUID|Institution ID|Department ID/i)).not.toBeInTheDocument();
  });
});
