import { useState, type FormEvent } from "react";
import { Button, CheckboxField, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
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
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

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
    setFormError(null);
  };

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault();
    if (!instCode.trim() || !instName.trim()) {
      setFormError("Institution Code and Name are required.");
      return;
    }
    setSubmitting(true);
    setFormError(null);
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
      });
      resetForm();
      onCreated(res);
      onClose();
    } catch (err: unknown) {
      setFormError(getApiErrorMessage(err));
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
          />

          <TextField
            id="inst-code"
            label="Institution Code *"
            required
            value={instCode}
            onChange={(e) => setInstCode(e.target.value)}
            placeholder="e.g. NBC-01"
          />
        </div>

        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="inst-name"
            label="Institution Name *"
            required
            value={instName}
            onChange={(e) => setInstName(e.target.value)}
            placeholder="e.g. Northbridge College of Engineering"
          />
        </div>

        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="inst-address"
            label="Primary Address"
            value={addressLine}
            onChange={(e) => setAddressLine(e.target.value)}
            placeholder="Campus address"
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
            onChange={(e) => setCity(e.target.value)}
            placeholder="Bengaluru"
          />
          <TextField
            id="inst-state"
            label="State"
            value={stateName}
            onChange={(e) => setStateName(e.target.value)}
            placeholder="Karnataka"
          />
          <TextField
            id="inst-postal"
            label="Postal Code"
            value={postalCode}
            onChange={(e) => setPostalCode(e.target.value)}
            placeholder="560001"
          />
        </div>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <TextField
            id="inst-principal"
            label="Principal / Head Email"
            type="email"
            value={principalEmail}
            onChange={(e) => setPrincipalEmail(e.target.value)}
            placeholder="principal@northbridge.edu.in"
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
            Submit & create draft
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default CreateInstitutionModal;
