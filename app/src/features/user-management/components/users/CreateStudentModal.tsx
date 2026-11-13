import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";
import { useFormErrors } from "../../../../hooks";
import {
  userManagementService,
  type StudentCreatePayload,
  type StudentProfileResponse,
} from "../../api/userManagementService";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isUuid(value: string): boolean {
  return UUID_PATTERN.test(value.trim());
}

export interface CreateStudentModalProps {
  tenantId: string;
  isOpen: boolean;
  onClose: () => void;
  onCreated: (student: StudentProfileResponse) => void;
}

export function CreateStudentModal({ tenantId, isOpen, onClose, onCreated }: CreateStudentModalProps) {
  const [fullName, setFullName] = useState("");
  const [institutionId, setInstitutionId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [academicSessionId, setAcademicSessionId] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [admissionDate, setAdmissionDate] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const { fieldErrors, formError, handleApiError, clearFieldError, clearAll, setFieldError } = useFormErrors();

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    clearAll();
    const requiredIds: Array<[keyof StudentCreatePayload, string]> = [
      ["institutionId", institutionId],
      ["sectionId", sectionId],
      ["academicSessionId", academicSessionId],
    ];
    let invalid = false;
    for (const [field, value] of requiredIds) {
      if (!isUuid(value)) {
        setFieldError(field, "Enter a valid UUID.");
        invalid = true;
      }
    }
    if (!fullName.trim()) {
      setFieldError("fullName", "Enter the student's full name.");
      invalid = true;
    }
    if (invalid) return;

    const payload: StudentCreatePayload = {
      institutionId: institutionId.trim(),
      fullName: fullName.trim(),
      sectionId: sectionId.trim(),
      academicSessionId: academicSessionId.trim(),
      dateOfBirth: dateOfBirth || undefined,
      admissionDate: admissionDate || undefined,
    };

    setSubmitting(true);
    try {
      const { data } = await userManagementService.createStudentProfile(tenantId, payload);
      setFullName("");
      setInstitutionId("");
      setSectionId("");
      setAcademicSessionId("");
      setDateOfBirth("");
      setAdmissionDate("");
      clearAll();
      onCreated(data);
    } catch (error) {
      handleApiError(error);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="STUDENT ADMISSION"
      title="Create student profile"
      titleId="create-student-profile-title"
      description="Create a student profile and enrolment in an active institution, section and academic session. Login provisioning follows the institution and class settings."
      size="lg"
    >
      <form onSubmit={handleSubmit} noValidate>
        <div className="platform-form-grid">
          <TextField
            id="student-full-name"
            label="Student full name"
            required
            maxLength={150}
            value={fullName}
            onChange={(event) => { setFullName(event.target.value); clearFieldError("fullName"); }}
            placeholder="Enter the student's legal name"
            error={fieldErrors.fullName}
          />
          <TextField
            id="student-institution-id"
            label="Institution ID (UUID)"
            required
            value={institutionId}
            onChange={(event) => { setInstitutionId(event.target.value); clearFieldError("institutionId"); }}
            placeholder="Paste institution UUID"
            error={fieldErrors.institutionId}
          />
          <TextField
            id="student-section-id"
            label="Section ID (UUID)"
            required
            value={sectionId}
            onChange={(event) => { setSectionId(event.target.value); clearFieldError("sectionId"); }}
            placeholder="Paste active section UUID"
            error={fieldErrors.sectionId}
          />
          <TextField
            id="student-session-id"
            label="Academic session ID (UUID)"
            required
            value={academicSessionId}
            onChange={(event) => { setAcademicSessionId(event.target.value); clearFieldError("academicSessionId"); }}
            placeholder="Paste active academic session UUID"
            error={fieldErrors.academicSessionId}
          />
          <TextField
            id="student-date-of-birth"
            label="Date of birth (optional)"
            type="date"
            value={dateOfBirth}
            onChange={(event) => { setDateOfBirth(event.target.value); clearFieldError("dateOfBirth"); }}
            error={fieldErrors.dateOfBirth}
          />
          <TextField
            id="student-admission-date"
            label="Admission date (optional)"
            type="date"
            value={admissionDate}
            onChange={(event) => { setAdmissionDate(event.target.value); clearFieldError("admissionDate"); }}
            hint="If omitted, the server uses the current date."
            error={fieldErrors.admissionDate}
          />
        </div>
        <p className="platform-form-note">
          The institution and academic session must be active, and the section must belong to both. Use the UUIDs supplied by your institution setup records.
        </p>
        {formError && <Notice tone="danger">{formError}</Notice>}
        <footer style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>Cancel</Button>
          <Button type="submit" busy={submitting}>Create student profile</Button>
        </footer>
      </form>
    </Modal>
  );
}

export default CreateStudentModal;
