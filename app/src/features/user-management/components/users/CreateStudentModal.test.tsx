import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { userManagementService, type StudentProfileResponse } from "../../api/userManagementService";
import { institutionService } from "../../../institution-admin/api/institutionService";
import { CreateStudentModal } from "./CreateStudentModal";

vi.mock("../../api/userManagementService", () => ({
  userManagementService: { createStudentProfile: vi.fn() },
}));

vi.mock("../../../institution-admin/api/institutionService", () => ({
  institutionService: { listInstitutions: vi.fn(), listSections: vi.fn(), listAcademicSessions: vi.fn() },
}));

const tenantId = "11111111-1111-1111-1111-111111111111";
const institutionId = "22222222-2222-2222-2222-222222222222";
const sectionId = "33333333-3333-3333-3333-333333333333";
const academicSessionId = "44444444-4444-4444-4444-444444444444";

describe("CreateStudentModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(institutionService.listInstitutions).mockResolvedValue({ items: [{
      institutionId, institutionName: "Northbridge College", institutionCode: "NBC",
    }] } as never);
    vi.mocked(institutionService.listSections).mockResolvedValue([]);
    vi.mocked(institutionService.listAcademicSessions).mockResolvedValue([]);
  });

  it("creates a student profile with its institution, section and session", async () => {
    const student: StudentProfileResponse = {
      id: "55555555-5555-5555-5555-555555555555",
      userAccountId: null,
      institutionId,
      studentCode: "STU-2026-001",
      fullName: "Asha Rao",
      status: "ACTIVE",
      temporaryPassword: null,
    };
    vi.mocked(userManagementService.createStudentProfile).mockResolvedValue({ data: student } as never);
    const onCreated = vi.fn();

    vi.mocked(institutionService.listSections).mockResolvedValue([
      { sectionId, institutionId, academicSessionId, semesterId: null, classId: null, sectionName: "A", status: "ACTIVE" },
      { sectionId: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", institutionId, academicSessionId, semesterId: null, classId: null, sectionName: "B", status: "INACTIVE" },
    ]);
    vi.mocked(institutionService.listAcademicSessions).mockResolvedValue([{
      academicSessionId, tenantId, institutionId, sessionLabel: "2026–27",
      startDate: "2026-06-01", endDate: "2027-05-31", academicYear: 2026, status: "ACTIVE", rowVersion: 1,
    }]);
    render(<CreateStudentModal tenantId={tenantId} isOpen onClose={vi.fn()} onCreated={onCreated} />);
    fireEvent.change(screen.getByLabelText(/Student full name/i), { target: { value: "Asha Rao" } });
    await waitFor(() => expect(institutionService.listInstitutions).toHaveBeenCalledWith(tenantId));
    fireEvent.change(screen.getByLabelText(/Institution/i), { target: { value: institutionId } });
    await waitFor(() => expect(institutionService.listSections).toHaveBeenCalledWith(tenantId, institutionId));
    fireEvent.change(screen.getByLabelText(/Academic session/i), { target: { value: academicSessionId } });
    fireEvent.change(screen.getByLabelText(/Section/i), { target: { value: sectionId } });
    fireEvent.change(screen.getByLabelText(/Roll number/i), { target: { value: "R-01" } });
    fireEvent.click(screen.getByRole("button", { name: "Create student profile" }));

    await waitFor(() => {
      expect(userManagementService.createStudentProfile).toHaveBeenCalledWith(tenantId, {
        institutionId,
        fullName: "Asha Rao",
        sectionId,
        academicSessionId,
        dateOfBirth: undefined,
        admissionDate: undefined,
        rollNumber: "R-01",
      });
      expect(onCreated).toHaveBeenCalledWith(student);
    });
  });

  it("does not expose manual identifiers when academic lookups fail", async () => {
    vi.mocked(institutionService.listSections).mockRejectedValue(new Error("Academic directory temporarily unavailable"));
    vi.mocked(institutionService.listAcademicSessions).mockRejectedValue(new Error("Academic directory temporarily unavailable"));
    const onCreated = vi.fn();
    render(<CreateStudentModal tenantId={tenantId} isOpen onClose={vi.fn()} onCreated={onCreated} />);
    await waitFor(() => expect(institutionService.listInstitutions).toHaveBeenCalledWith(tenantId));
    fireEvent.change(screen.getByLabelText(/Student full name/i), { target: { value: "Asha Rao" } });
    fireEvent.change(screen.getByLabelText(/Institution/i), { target: { value: institutionId } });

    expect(await screen.findByText(/Academic directory temporarily unavailable/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/UUID|Institution ID|Session ID|Section ID/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Create student profile" }));
    expect(userManagementService.createStudentProfile).not.toHaveBeenCalled();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it("requires selections from the authorized institution and academic lists", async () => {
    render(<CreateStudentModal tenantId={tenantId} isOpen onClose={vi.fn()} onCreated={vi.fn()} />);
    await waitFor(() => expect(institutionService.listInstitutions).toHaveBeenCalledWith(tenantId));
    fireEvent.change(screen.getByLabelText(/Student full name/i), { target: { value: "Asha Rao" } });
    fireEvent.click(screen.getByRole("button", { name: "Create student profile" }));

    expect(screen.getByText("Choose an institution from the list.")).toBeInTheDocument();
    expect(userManagementService.createStudentProfile).not.toHaveBeenCalled();
  });
});
