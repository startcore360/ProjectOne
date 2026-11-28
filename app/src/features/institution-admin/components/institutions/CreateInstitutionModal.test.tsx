import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CreateInstitutionModal } from "./CreateInstitutionModal";
import { institutionService } from "../../api/institutionService";

vi.mock("../../api/institutionService", () => ({
  institutionService: {
    getHierarchy: vi.fn(),
    createDraftInstitution: vi.fn(),
  },
}));

describe("CreateInstitutionModal", () => {
  beforeEach(() => {
    vi.mocked(institutionService.getHierarchy).mockResolvedValue({
      tenantId: "tenant-1",
      tenantName: "Test Tenant",
      tenantType: "UNIVERSITY_GROUP",
      institutions: [],
    });
  });

  it("fires blur-triggered validation for required fields and invalid email", async () => {
    render(
      <CreateInstitutionModal
        tenantId="tenant-1"
        isOpen={true}
        onClose={vi.fn()}
        onCreated={vi.fn()}
      />
    );

    const codeInput = screen.getByLabelText(/Institution Code \*/i);
    fireEvent.focus(codeInput);
    fireEvent.blur(codeInput);

    await waitFor(() => {
      expect(codeInput).toHaveAttribute("aria-invalid", "true");
      expect(document.getElementById("inst-code-error")).toHaveTextContent("Institution Code is required.");
    });

    const emailInput = screen.getByLabelText("Principal / Head Email");
    fireEvent.change(emailInput, { target: { value: "invalid-email" } });
    fireEvent.blur(emailInput);

    await waitFor(() => {
      expect(emailInput).toHaveAttribute("aria-invalid", "true");
      expect(document.getElementById("inst-principal-error")).toHaveTextContent("Enter a valid email address.");
    });
  });

  it("locks the Institution Kind to School for a STANDALONE_SCHOOL tenant", async () => {
    vi.mocked(institutionService.getHierarchy).mockResolvedValue({
      tenantId: "tenant-1",
      tenantName: "Lakeside World School",
      tenantType: "STANDALONE_SCHOOL",
      institutions: [],
    });

    render(
      <CreateInstitutionModal tenantId="tenant-1" isOpen={true} onClose={vi.fn()} onCreated={vi.fn()} />
    );

    const kindSelect = screen.getByLabelText(/Institution Kind \*/i) as HTMLSelectElement;
    await waitFor(() => {
      expect(kindSelect).toBeDisabled();
      expect(kindSelect.value).toBe("SCHOOL");
      expect(screen.getAllByRole("option", { hidden: true }).map((o) => (o as HTMLOptionElement).value)).toEqual(["SCHOOL"]);
    });
    expect(screen.getByText(/only School institutions can be created/i)).toBeInTheDocument();
  });

  it("locks the Institution Kind to College for a STANDALONE_COLLEGE tenant", async () => {
    vi.mocked(institutionService.getHierarchy).mockResolvedValue({
      tenantId: "tenant-1",
      tenantName: "Northbridge College",
      tenantType: "STANDALONE_COLLEGE",
      institutions: [],
    });

    render(
      <CreateInstitutionModal tenantId="tenant-1" isOpen={true} onClose={vi.fn()} onCreated={vi.fn()} />
    );

    const kindSelect = screen.getByLabelText(/Institution Kind \*/i) as HTMLSelectElement;
    await waitFor(() => {
      expect(kindSelect).toBeDisabled();
      expect(kindSelect.value).toBe("COLLEGE");
    });
    expect(screen.getByText(/only College institutions can be created/i)).toBeInTheDocument();
  });

  it("offers both kinds for a UNIVERSITY_GROUP tenant", async () => {
    render(
      <CreateInstitutionModal tenantId="tenant-1" isOpen={true} onClose={vi.fn()} onCreated={vi.fn()} />
    );

    const kindSelect = screen.getByLabelText(/Institution Kind \*/i) as HTMLSelectElement;
    await waitFor(() => {
      expect(institutionService.getHierarchy).toHaveBeenCalledWith("tenant-1");
    });
    expect(kindSelect).not.toBeDisabled();
    expect(screen.getAllByRole("option", { hidden: true }).map((o) => (o as HTMLOptionElement).value)).toEqual(["COLLEGE", "SCHOOL"]);
  });
});
