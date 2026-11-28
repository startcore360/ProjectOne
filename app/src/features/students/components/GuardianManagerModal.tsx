import { useState, type FormEvent } from "react";
import { Button, CheckboxField, Modal, Notice, PhoneField, SelectField, TextField } from "../../../design-system/components";
import { useFormErrors } from "../../../hooks";
import { studentApi, type GuardianLink } from "../api/studentApi";

export function GuardianManagerModal({
  tenantId,
  studentId,
  guardians,
  onClose,
  onSaved,
}: {
  tenantId: string;
  studentId: string;
  guardians: GuardianLink[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const activeGuardianCount = guardians.filter((g) => g.status !== "BLOCKED").length;
  const [fullName, setFullName] = useState("");
  const [mobile, setMobile] = useState("");
  const [email, setEmail] = useState("");
  const [relationship, setRelationship] = useState("FATHER");
  const [primary, setPrimary] = useState(activeGuardianCount === 0);
  const [contactAuthorized, setContactAuthorized] = useState(true);
  const [emergencyContact, setEmergencyContact] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const { fieldErrors, handleApiError, setFieldError, clearFieldError, clearAll } = useFormErrors();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    clearAll();
    try {
      const { data } = await studentApi.linkGuardian(tenantId, studentId, {
        fullName,
        mobile,
        email,
        relationship,
        isPrimary: primary,
      });
      await studentApi.guardianPreferences(tenantId, studentId, data.studentGuardianLinkId, {
        contactAuthorized,
        emergencyContact,
        primary,
      });
      onSaved();
      onClose();
    } catch (x) {
      handleApiError(x);
      setError(x instanceof Error ? x.message : "Could not save guardian link");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      isOpen={true}
      onClose={onClose}
      title="Manage student guardian"
      description="Up to three non-blocked guardians. Exactly one must be primary."
      size="md"
    >
      {error && <Notice tone="danger">{error}</Notice>}
      <form className="app-form-grid" onSubmit={handleSubmit}>
        <TextField
          id="guardian-name"
          label="Full name"
          required
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          fieldName="fullName"
          error={fieldErrors.fullName}
          setFieldError={setFieldError}
          clearFieldError={clearFieldError}
        />
        <PhoneField
          id="guardian-mobile"
          label="Mobile number"
          required
          value={mobile}
          onChange={(e) => setMobile(e.target.value)}
          fieldName="mobile"
          error={fieldErrors.mobile}
          setFieldError={setFieldError}
          clearFieldError={clearFieldError}
        />
        <TextField
          id="guardian-email"
          label="Email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          fieldName="email"
          error={fieldErrors.email}
          setFieldError={setFieldError}
          clearFieldError={clearFieldError}
        />
        <SelectField
          id="guardian-relationship"
          label="Relationship"
          value={relationship}
          onChange={(e) => setRelationship(e.target.value)}
        >
          {["FATHER", "MOTHER", "LEGAL_GUARDIAN", "LOCAL_SPONSOR"].map((x) => (
            <option key={x} value={x}>
              {x}
            </option>
          ))}
        </SelectField>
        <CheckboxField
          id="guardian-primary"
          label="Primary guardian"
          checked={primary}
          onChange={(e) => setPrimary(e.target.checked)}
        />
        <CheckboxField
          id="guardian-contact-auth"
          label="Authorized for contact"
          checked={contactAuthorized}
          onChange={(e) => setContactAuthorized(e.target.checked)}
        />
        <CheckboxField
          id="guardian-emergency"
          label="Emergency contact"
          checked={emergencyContact}
          onChange={(e) => setEmergencyContact(e.target.checked)}
        />
        <div className="app-toolbar" style={{ display: "flex", gap: "var(--space-2)", marginTop: "var(--space-4)" }}>
          <Button
            type="submit"
            busy={busy}
            disabled={!fullName.trim() || !mobile.trim() || activeGuardianCount >= 3}
          >
            Save guardian
          </Button>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
        </div>
      </form>
    </Modal>
  );
}
