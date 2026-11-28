import { useEffect, useState, type FormEvent } from "react";
import { Button, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import { useFormErrors } from "../../../../hooks";
import {
  institutionService,
  type AcademicSessionItem,
  type InstitutionItem,
  type SectionItem,
} from "../../../institution-admin/api/institutionService";
import {
  userManagementService,
  type StudentCreatePayload,
  type StudentProfileResponse,
} from "../../api/userManagementService";

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
  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [institutionsLoading, setInstitutionsLoading] = useState(false);
  const [institutionsError, setInstitutionsError] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [academicSessionId, setAcademicSessionId] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [admissionDate, setAdmissionDate] = useState("");
  const [rollNumber, setRollNumber] = useState("");
  const [sections, setSections] = useState<SectionItem[]>([]);
  const [sessions, setSessions] = useState<AcademicSessionItem[]>([]);
  const [optionsLoadedFor, setOptionsLoadedFor] = useState("");
  const [optionsLoading, setOptionsLoading] = useState(false);
  const [optionsError, setOptionsError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const { fieldErrors, formError, handleApiError, clearFieldError, clearAll, setFieldError } = useFormErrors();

  useEffect(() => {
    if (!isOpen) return;
    let active = true;
    setInstitutionsLoading(true);
    setInstitutionsError("");
    institutionService.listInstitutions(tenantId)
      .then((response) => {
        if (active) setInstitutions(response.items ?? []);
      })
      .catch((error) => {
        if (active) {
          setInstitutions([]);
          setInstitutionsError(getApiErrorMessage(error) || "Institutions could not be loaded.");
        }
      })
      .finally(() => {
        if (active) setInstitutionsLoading(false);
      });
    return () => { active = false; };
  }, [tenantId, isOpen]);

  const activeSessions = sessions.filter((session) =>
    isActive(session.status) && Boolean(session.startDate) && Boolean(session.endDate)
  );
  const activeSectionsForSession = sections.filter((section) =>
    isActive(section.status) && section.academicSessionId === academicSessionId
  );
  const hasCurrentOptions = optionsLoadedFor === institutionId.trim();

  const loadAcademicOptions = async (selectedInstitutionId: string) => {
    setOptionsError("");
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
        setOptionsError("No active academic sessions with start and end dates are available for this institution.");
      } else if (!sectionRows.some((section) => isActive(section.status))) {
        setOptionsError("No active sections are available for this institution.");
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
    if (value) void loadAcademicOptions(value);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    clearAll();
    let invalid = false;
    if (!institutions.some((institution) => institution.institutionId === institutionId)) {
      setFieldError("institutionId", "Choose an institution from the list.");
      invalid = true;
    }
    if (!academicSessionId || !activeSessions.some((session) => session.academicSessionId === academicSessionId)) {
      setFieldError("academicSessionId", "Choose an active academic session.");
      invalid = true;
    }
    if (!sectionId || !activeSectionsForSession.some((section) => section.sectionId === sectionId)) {
      setFieldError("sectionId", "Choose an active section in the selected session.");
      invalid = true;
    }
    if (!fullName.trim()) {
      setFieldError("fullName", "Enter the student's full name.");
      invalid = true;
    }
    if (!hasCurrentOptions || optionsLoading || optionsError) {
      setFieldError("institutionId", "The institution's academic options must load successfully before continuing.");
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
      rollNumber: rollNumber.trim() || undefined,
    };

    setSubmitting(true);
    try {
      const { data } = await userManagementService.createStudentProfile(tenantId, payload);
      setFullName("");
      setInstitutionId("");
      setInstitutions([]);
      setSectionId("");
      setAcademicSessionId("");
      setDateOfBirth("");
      setAdmissionDate("");
      setRollNumber("");
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
        <div className="app-form-grid">
          <TextField
            id="student-full-name"
            label="Student full name"
            required
            maxLength={150}
            value={fullName}
            onChange={(event) => { setFullName(event.target.value); clearFieldError("fullName"); }}
            placeholder="Enter the student's legal name"
            fieldName="fullName"
            error={fieldErrors.fullName}
            setFieldError={setFieldError}
            clearFieldError={clearFieldError}
          />
          <SelectField
            id="student-institution"
            label="Institution"
            required
            value={institutionId}
            disabled={institutionsLoading || institutions.length === 0}
            onChange={(event) => handleInstitutionChange(event.target.value)}
            error={fieldErrors.institutionId}
          >
            <option value="">{institutionsLoading ? "Loading institutions…" : "Choose an institution"}</option>
            {institutions.map((institution) => (
              <option key={institution.institutionId} value={institution.institutionId}>
                {institution.institutionName} · {institution.institutionCode}
              </option>
            ))}
          </SelectField>

          <SelectField
            id="student-session-id"
            label="Academic session"
            required
            value={academicSessionId}
            disabled={!hasCurrentOptions || optionsLoading || activeSessions.length === 0}
            onChange={(event) => {
              setAcademicSessionId(event.target.value);
              setSectionId("");
              clearFieldError("academicSessionId");
              clearFieldError("sectionId");
            }}
            error={fieldErrors.academicSessionId}
          >
            <option value="">{optionsLoading ? "Loading sessions…" : "Choose an active session"}</option>
            {activeSessions.map((session) => (
              <option key={session.academicSessionId} value={session.academicSessionId}>
                {session.sessionLabel} ({session.startDate} – {session.endDate})
              </option>
            ))}
          </SelectField>

          <SelectField
            id="app-section-id"
            label="Section"
            required
            value={sectionId}
            disabled={!academicSessionId || optionsLoading || activeSectionsForSession.length === 0}
            onChange={(event) => { setSectionId(event.target.value); clearFieldError("sectionId"); }}
            error={fieldErrors.sectionId}
          >
            <option value="">{optionsLoading ? "Loading sections…" : "Choose an active section"}</option>
            {activeSectionsForSession.map((section) => (
              <option key={section.sectionId} value={section.sectionId}>
                Section {section.sectionName}
              </option>
            ))}
          </SelectField>

          <TextField
            id="student-roll-number"
            label="Roll number (optional)"
            maxLength={30}
            value={rollNumber}
            onChange={(event) => setRollNumber(event.target.value)}
            hint="Must be unique within the section."
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
        {institutionsError && (
          <Notice tone="warning">
            <div className="app-feedback-row">
              <span>{institutionsError}</span>
              <Button type="button" variant="secondary" size="sm" onClick={() => {
                setInstitutionsError("");
                setInstitutionsLoading(true);
                institutionService.listInstitutions(tenantId)
                  .then((response) => setInstitutions(response.items ?? []))
                  .catch((error) => setInstitutionsError(getApiErrorMessage(error) || "Institutions could not be loaded."))
                  .finally(() => setInstitutionsLoading(false));
              }} disabled={institutionsLoading} busy={institutionsLoading}>Retry</Button>
            </div>
          </Notice>
        )}
        {optionsError && (
          <Notice tone="warning">
            <div className="app-feedback-row">
              <span>{optionsError} Choose an institution with an active session and section, or ask your academic administrator to configure them.</span>
              <Button type="button" variant="secondary" size="sm" onClick={() => void loadAcademicOptions(institutionId)} disabled={optionsLoading || !institutionId} busy={optionsLoading}>Retry</Button>
            </div>
          </Notice>
        )}
        {optionsLoadedFor && !optionsError && activeSessions.length > 0 && activeSectionsForSession.length === 0 && academicSessionId && (
          <Notice tone="warning">No active sections are available in the selected session.</Notice>
        )}
        <p className="app-note">
          The institution and academic session must be active, and the section must belong to both. Student profiles are created separately from general user accounts; login provisioning follows institution and class settings.
        </p>
        {formError && <Notice tone="danger">{formError}</Notice>}
        <footer>
          <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>Cancel</Button>
          <Button type="submit" busy={submitting}>Create student profile</Button>
        </footer>
      </form>
    </Modal>
  );
}

export default CreateStudentModal;
