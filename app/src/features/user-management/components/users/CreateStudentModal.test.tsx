import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { userManagementService, type StudentProfileResponse } from "../../api/userManagementService";
import { CreateStudentModal } from "./CreateStudentModal";

vi.mock("../../api/userManagementService", () => ({
  userManagementService: { createStudentProfile: vi.fn() },
}));

const tenantId = "11111111-1111-1111-1111-111111111111";
const institutionId = "22222222-2222-2222-2222-222222222222";
const sectionId = "33333333-3333-3333-3333-333333333333";
const academicSessionId = "44444444-4444-4444-4444-444444444444";

describe("CreateStudentModal", () => {
  beforeEach(() => vi.clearAllMocks());

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

    render(<CreateStudentModal tenantId={tenantId} isOpen onClose={vi.fn()} onCreated={onCreated} />);
    fireEvent.change(screen.getByLabelText(/Student full name/i), { target: { value: "Asha Rao" } });
    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: institutionId } });
    fireEvent.change(screen.getByLabelText(/Section ID/i), { target: { value: sectionId } });
    fireEvent.change(screen.getByLabelText(/Academic session ID/i), { target: { value: academicSessionId } });
    fireEvent.click(screen.getByRole("button", { name: "Create student profile" }));

    await waitFor(() => {
      expect(userManagementService.createStudentProfile).toHaveBeenCalledWith(tenantId, {
        institutionId,
        fullName: "Asha Rao",
        sectionId,
        academicSessionId,
        dateOfBirth: undefined,
        admissionDate: undefined,
      });
      expect(onCreated).toHaveBeenCalledWith(student);
    });
  });

  it("rejects malformed UUIDs before sending a request", () => {
    render(<CreateStudentModal tenantId={tenantId} isOpen onClose={vi.fn()} onCreated={vi.fn()} />);
    fireEvent.change(screen.getByLabelText(/Student full name/i), { target: { value: "Asha Rao" } });
    fireEvent.change(screen.getByLabelText(/Institution ID/i), { target: { value: "not-a-uuid" } });
    fireEvent.change(screen.getByLabelText(/Section ID/i), { target: { value: sectionId } });
    fireEvent.change(screen.getByLabelText(/Academic session ID/i), { target: { value: academicSessionId } });
    fireEvent.click(screen.getByRole("button", { name: "Create student profile" }));

    expect(screen.getByText("Enter a valid UUID.")).toBeInTheDocument();
    expect(userManagementService.createStudentProfile).not.toHaveBeenCalled();
  });
});
