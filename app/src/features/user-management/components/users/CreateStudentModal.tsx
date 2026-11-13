import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import { useFormErrors } from "../../../../hooks";
import {
  institutionService,
  type AcademicSessionItem,
  type SectionItem,
} from "../../../institution-admin/api/institutionService";
import {
  userManagementService,
  type StudentCreatePayload,
  type StudentProfileResponse,
} from "../../api/userManagementService";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isUuid(value: string): boolean {
  return UUID_PATTERN.test(value.trim());
}

function isActive(value: string): boolean {
  return value.toUpperCase() === "ACTIVE";
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
  const [sections, setSections] = useState<SectionItem[]>([]);
  const [sessions, setSessions] = useState<AcademicSessionItem[]>([]);
  const [optionsLoadedFor, setOptionsLoadedFor] = useState("");
  const [optionsLoading, setOptionsLoading] = useState(false);
  const [optionsError, setOptionsError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const { fieldErrors, formError, handleApiError, clearFieldError, clearAll, setFieldError } = useFormErrors();

  const activeSessions = sessions.filter((session) =>
    isActive(session.status) && Boolean(session.startDate) && Boolean(session.endDate)
  );
  const activeSectionsForSession = sections.filter((section) =>
    isActive(section.status) && section.academicSessionId === academicSessionId
  );
  const hasCurrentOptions = optionsLoadedFor === institutionId.trim();
  const canChooseSession = hasCurrentOptions && activeSessions.length > 0;
  const canChooseSection = hasCurrentOptions && activeSectionsForSession.length > 0;

  const loadAcademicOptions = async () => {
    const selectedInstitutionId = institutionId.trim();
    setOptionsError("");
    if (!isUuid(selectedInstitutionId)) {
      setFieldError("institutionId", "Enter a valid institution UUID before loading academic options.");
      setOptionsLoadedFor("");
      return;
    }
    setOptionsLoading(true);
    setOptionsLoadedFor("");
    setSections([]);
    setSessions([]);
    setSectionId("");
    setAcademicSessionId("");
    clearFieldError("institutionId");
    try {
      const [sectionRows, sessionRows] = await Promise.all([
        institutionService.listSections(tenantId, selectedInstitutionId),
        institutionService.listAcademicSessions(tenantId, selectedInstitutionId),
      ]);
      setSections(Array.isArray(sectionRows) ? sectionRows : []);
      setSessions(Array.isArray(sessionRows) ? sessionRows : []);
      setOptionsLoadedFor(selectedInstitutionId);
      if (!sessionRows.some((session) => isActive(session.status) && session.startDate && session.endDate)) {
        setOptionsError("No active academic sessions with start and end dates were returned. You can enter a known session UUID manually.");
      } else if (!sectionRows.some((section) => isActive(section.status))) {
        setOptionsError("No active sections were returned for this institution. You can enter a known section UUID manually.");
      }
    } catch (error) {
      setOptionsLoadedFor(selectedInstitutionId);
      setOptionsError(getApiErrorMessage(error) || "Academic options could not be loaded.");
    } finally {
      setOptionsLoading(false);
    }
  };

  const handleInstitutionChange = (value: string) => {
    setInstitutionId(value);
    setSections([]);
    setSessions([]);
    setSectionId("");
    setAcademicSessionId("");
    setOptionsLoadedFor("");
    setOptionsError("");
    clearFieldError("institutionId");
    clearFieldError("sectionId");
    clearFieldError("academicSessionId");
  };

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
    if (isUuid(institutionId) && optionsLoadedFor !== institutionId.trim() && !optionsError) {
      setFieldError("institutionId", "Load the institution's active sessions and sections before continuing.");
      invalid = true;
    }
    if (canChooseSession && !activeSessions.some((session) => session.academicSessionId === academicSessionId)) {
      setFieldError("academicSessionId", "Choose an active academic session.");
      invalid = true;
    }
    if (canChooseSection && !activeSectionsForSession.some((section) => section.sectionId === sectionId)) {
      setFieldError("sectionId", "Choose an active section in the selected session.");
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
      setSections([]);
      setSessions([]);
      setOptionsLoadedFor("");
      setOptionsError("");
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
          <div className="student-academic-lookup">
            <TextField
              id="student-institution-id"
              label="Institution ID (UUID)"
              required
              value={institutionId}
              onChange={(event) => handleInstitutionChange(event.target.value)}
              placeholder="Paste institution UUID"
              hint="Enter the institution ID supplied by your academic administrator."
              error={fieldErrors.institutionId}
            />
            <div className="platform-inline-actions">
              <Button type="button" variant="secondary" size="sm" onClick={() => void loadAcademicOptions()} busy={optionsLoading} disabled={optionsLoading || !isUuid(institutionId)}>
                {optionsLoading ? "Loading options…" : "Load sessions and sections"}
              </Button>
            </div>
          </div>

          {canChooseSession ? (
            <SelectField
              id="student-session-id"
              label="Academic session"
              required
              value={academicSessionId}
              onChange={(event) => {
                setAcademicSessionId(event.target.value);
                setSectionId("");
                clearFieldError("academicSessionId");
                clearFieldError("sectionId");
              }}
              error={fieldErrors.academicSessionId}
            >
              <option value="">Choose an active session</option>
              {activeSessions.map((session) => (
                <option key={session.academicSessionId} value={session.academicSessionId}>
                  {session.sessionLabel} ({session.startDate} – {session.endDate})
                </option>
              ))}
            </SelectField>
          ) : (
            <TextField
              id="student-session-id"
              label="Academic session ID (UUID)"
              required
              value={academicSessionId}
              onChange={(event) => { setAcademicSessionId(event.target.value); clearFieldError("academicSessionId"); }}
              placeholder="Paste active academic session UUID"
              hint="Use a session ID from your academic calendar."
              error={fieldErrors.academicSessionId}
            />
          )}

          {canChooseSection ? (
            <SelectField
              id="student-section-id"
              label="Section"
              required
              value={sectionId}
              onChange={(event) => { setSectionId(event.target.value); clearFieldError("sectionId"); }}
              error={fieldErrors.sectionId}
            >
              <option value="">Choose an active section</option>
              {activeSectionsForSession.map((section) => (
                <option key={section.sectionId} value={section.sectionId}>
                  Section {section.sectionName}
                </option>
              ))}
            </SelectField>
          ) : (
            <TextField
              id="student-section-id"
              label="Section ID (UUID)"
              required
              value={sectionId}
              onChange={(event) => { setSectionId(event.target.value); clearFieldError("sectionId"); }}
              placeholder="Paste active section UUID"
              hint="Use a section ID from the selected academic session."
              error={fieldErrors.sectionId}
            />
          )}

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
        {optionsError && (
          <Notice tone="warning">
            <div className="platform-feedback-row">
              <span>{optionsError} You may enter known UUIDs manually, but the server will verify the institution, session and section are active and belong together.</span>
              <Button type="button" variant="secondary" size="sm" onClick={() => void loadAcademicOptions()} disabled={optionsLoading} busy={optionsLoading}>Retry</Button>
            </div>
          </Notice>
        )}
        {optionsLoadedFor && !optionsError && activeSessions.length > 0 && activeSectionsForSession.length === 0 && academicSessionId && (
          <Notice tone="warning">No active sections were returned for this session. Enter a known section UUID if one has already been created.</Notice>
        )}
        <p className="platform-modal-note">
          The institution and academic session must be active, and the section must belong to both. Student profiles are created separately from general user accounts; login provisioning follows institution and class settings.
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
