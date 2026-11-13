import { useState, type FormEvent } from "react";
import { Button, CheckboxField, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";
import { useFormErrors } from "../../../../hooks";
import {
  institutionService,
  type InstitutionItem,
  type InstitutionType,
} from "../../api/institutionService";

export interface CreateInstitutionModalProps {
  tenantId: string;
  isOpen: boolean;
  onClose: () => void;
  onCreated: (newInst: InstitutionItem) => void;
}

export function CreateInstitutionModal({
  tenantId,
  isOpen,
  onClose,
  onCreated,
}: CreateInstitutionModalProps) {
  const [instType, setInstType] = useState<InstitutionType>("COLLEGE");
  const [instCode, setInstCode] = useState("");
  const [instName, setInstName] = useState("");
  const [externalUni, setExternalUni] = useState(false);
  const [addressLine, setAddressLine] = useState("");
  const [city, setCity] = useState("");
  const [stateName, setStateName] = useState("");
  const [postalCode, setPostalCode] = useState("");
  const [principalEmail, setPrincipalEmail] = useState("");
  const [studentIdFormat, setStudentIdFormat] = useState("");
  const [staffIdFormat, setStaffIdFormat] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const {
    fieldErrors,
    formError,
    handleApiError,
    clearFieldError,
    clearAll,
    setFormError,
  } = useFormErrors();

  const resetForm = () => {
    setInstType("COLLEGE");
    setInstCode("");
    setInstName("");
    setExternalUni(false);
    setAddressLine("");
    setCity("");
    setStateName("");
    setPostalCode("");
    setPrincipalEmail("");
    setStudentIdFormat("");
    setStaffIdFormat("");
    clearAll();
  };

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault();
    // Client-side required check
    if (!instCode.trim() || !instName.trim() || !studentIdFormat.trim()) {
      setFormError("Institution Code, Name and Student ID format are required.");
      return;
    }
    setSubmitting(true);
    clearAll();
    try {
      const res = await institutionService.createDraftInstitution(tenantId, {
        institutionCode: instCode.trim(),
        institutionName: instName.trim(),
        institutionType: instType,
        externalUniversityMode: externalUni,
        addressLine: addressLine.trim() || undefined,
        city: city.trim() || undefined,
        stateName: stateName.trim() || undefined,
        postalCode: postalCode.trim() || undefined,
        principalInviteEmail: principalEmail.trim() || undefined,
        studentIdFormat: studentIdFormat.trim(),
        staffIdFormat: staffIdFormat.trim() || undefined,
      });
      resetForm();
      onCreated(res);
      onClose();
    } catch (err: unknown) {
      handleApiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={() => {
        resetForm();
        onClose();
      }}
      title="Register institution (Draft)"
      titleId="modal-title"
      description="Institutions are created in Draft state and go live upon Vice Chancellor approval [WF-16]."
      size="md"
    >
      {formError && (
        <div style={{ marginBottom: "var(--space-4)" }}>
          <Notice tone="danger">{formError}</Notice>
        </div>
      )}

      <form onSubmit={handleCreate}>
        <div className="platform-form-grid" style={{ marginBottom: "var(--space-3)" }}>
          <SelectField
            id="inst-type"
            label="Institution Kind *"
            required
            value={instType}
            onChange={(e) => setInstType(e.target.value as InstitutionType)}
            options={[
              { value: "COLLEGE", label: "College" },
              { value: "SCHOOL", label: "School" },
            ]}
            error={fieldErrors.institutionType}
          />

          <TextField
            id="inst-code"
            label="Institution Code *"
            required
            value={instCode}
            onChange={(e) => { setInstCode(e.target.value); clearFieldError("institutionCode"); }}
            placeholder="e.g. NBC-01"
            error={fieldErrors.institutionCode}
          />
        </div>

        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="inst-name"
            label="Institution Name *"
            required
            value={instName}
            onChange={(e) => { setInstName(e.target.value); clearFieldError("institutionName"); }}
            placeholder="e.g. Northbridge College of Engineering"
            error={fieldErrors.institutionName}
          />
        </div>


        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="inst-student-id-format"
            label="Student ID format *"
            required
            value={studentIdFormat}
            onChange={(event) => { setStudentIdFormat(event.target.value.toUpperCase()); clearFieldError("studentIdFormat"); }}
            placeholder="{INST}-{YYYY}-{SEQ:4}"
            hint="Use {YYYY}, {YY}, {INST}, and exactly one {SEQ:n} token (n = 3–10). Literals: A–Z, 0–9, _ and -."
            error={fieldErrors.studentIdFormat}
          />
          <p className="platform-modal-note" aria-live="polite">
            Preview only: {studentIdFormat ? studentIdFormat.replaceAll("{INST}", instCode.trim() || "INST").replaceAll("{YYYY}", "2026").replaceAll("{YY}", "26").replace(/\{SEQ:(\d+)\}/g, (_match, width: string) => "1".padStart(Number(width), "0")) : "Enter the format to preview the generated student code."}
          </p>
        </div>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="inst-staff-id-format"
            label="Staff ID format (optional)"
            value={staffIdFormat}
            onChange={(event) => { setStaffIdFormat(event.target.value.toUpperCase()); clearFieldError("staffIdFormat"); }}
            placeholder="{INST}-ST-{YY}-{SEQ:4}"
            hint="If omitted, HR must supply a unique staff code when creating each staff profile."
            error={fieldErrors.staffIdFormat}
          />
          {staffIdFormat && (
            <p className="platform-modal-note" aria-live="polite">
              Preview only: {staffIdFormat.replaceAll("{INST}", instCode.trim() || "INST").replaceAll("{YYYY}", "2026").replaceAll("{YY}", "26").replace(/\{SEQ:(\d+)\}/g, (_match, width: string) => "1".padStart(Number(width), "0"))}
            </p>
          )}
        </div>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="inst-address"
            label="Primary Address"
            value={addressLine}
            onChange={(e) => { setAddressLine(e.target.value); clearFieldError("addressLine"); }}
            placeholder="Campus address"
            error={fieldErrors.addressLine}
          />
        </div>

        <div
          style={{
            display: "grid",
            gridTemplateColumns: "1fr 1fr 1fr",
            gap: "var(--space-3)",
            marginBottom: "var(--space-3)",
          }}
        >
          <TextField
            id="inst-city"
            label="City"
            value={city}
            onChange={(e) => { setCity(e.target.value); clearFieldError("city"); }}
            placeholder="Bengaluru"
            error={fieldErrors.city}
          />
          <TextField
            id="inst-state"
            label="State"
            value={stateName}
            onChange={(e) => { setStateName(e.target.value); clearFieldError("stateName"); }}
            placeholder="Karnataka"
            error={fieldErrors.stateName}
          />
          <TextField
            id="inst-postal"
            label="Postal Code"
            value={postalCode}
            onChange={(e) => { setPostalCode(e.target.value); clearFieldError("postalCode"); }}
            placeholder="560001"
            error={fieldErrors.postalCode}
          />
        </div>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <TextField
            id="inst-principal"
            label="Principal / Head Email"
            type="email"
            value={principalEmail}
            onChange={(e) => { setPrincipalEmail(e.target.value); clearFieldError("principalInviteEmail"); }}
            placeholder="principal@northbridge.edu.in"
            error={fieldErrors.principalInviteEmail}
          />
        </div>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <CheckboxField
            id="inst-external-uni"
            checked={externalUni}
            onChange={(e) => setExternalUni(e.target.checked)}
            label="External University Affiliation Mode (Operates under outside syllabus body)"
          />
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button
            variant="secondary"
            onClick={() => {
              resetForm();
              onClose();
            }}
            disabled={submitting}
          >
            Cancel
          </Button>
          <Button type="submit" busy={submitting}>
            Submit &amp; create draft
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default CreateInstitutionModal;
