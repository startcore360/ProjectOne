import { useState, type FormEvent } from "react";
import { Button, Kicker, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";
import { useFormErrors } from "../../../../hooks";
import {
  userManagementService,
  type ActingScope,
  type CreateUserResponse,
  type UserType,
} from "../../api/userManagementService";
import { ScopeFields } from "../common/ScopeFields";

export interface CreateUserModalProps {
  tenantId: string;
  isOpen: boolean;
  onClose: () => void;
  onCreated: (user: CreateUserResponse) => void;
}

export function CreateUserModal({
  tenantId,
  isOpen,
  onClose,
  onCreated,
}: CreateUserModalProps) {
  const [displayName, setDisplayName] = useState("");
  const [userType, setUserType] = useState<UserType>("STAFF");
  const [email, setEmail] = useState("");
  const [mobile, setMobile] = useState("+91");
  const [nationalIdLast4, setNationalIdLast4] = useState("");
  const [initialRoleCode, setInitialRoleCode] = useState("");
  const [scope, setScope] = useState<ActingScope>({ scopeType: "TENANT" });
  const [submitting, setSubmitting] = useState(false);

  const {
    fieldErrors,
    formError,
    handleApiError,
    clearFieldError,
    clearAll,
  } = useFormErrors();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    clearAll();
    try {
      const { data } = await userManagementService.createUser(tenantId, {
        displayName: displayName.trim(),
        userType,
        email: email.trim(),
        mobile: mobile.trim(),
        nationalIdLast4: nationalIdLast4.trim() || undefined,
        initialRoleCode: initialRoleCode.trim() || undefined,
        scopeType: scope.scopeType,
        institutionId: scope.scopeType !== "TENANT" ? scope.institutionId : undefined,
        departmentId: scope.scopeType === "DEPARTMENT" ? scope.departmentId : undefined,
      });
      onCreated(data);
    } catch (err) {
      handleApiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="USER_CREATE"
      title="Create user account"
      titleId="create-user-title"
      description="Provision an identity and create a tenant user account with Keycloak single sign-on."
      size="lg"
    >
      <form onSubmit={handleSubmit}>
        <div className="platform-form-grid">
          <TextField
            id="create-display-name"
            label="Full name"
            required
            value={displayName}
            onChange={(e) => { setDisplayName(e.target.value); clearFieldError("displayName"); }}
            placeholder="e.g. Dr. Anita Sharma"
            error={fieldErrors.displayName}
          />
          <SelectField
            id="create-user-type"
            label="User type"
            value={userType}
            onChange={(e) => setUserType(e.target.value as UserType)}
            options={[
              { value: "STAFF", label: "Staff (Faculty / Admin)" },
              { value: "GUARDIAN", label: "Guardian / Parent" },
              { value: "APPLICANT", label: "Applicant" },
              { value: "GOVERNANCE", label: "Governance / Board" },
            ]}
            error={fieldErrors.userType}
          />
          <TextField
            id="create-email"
            label="Email address"
            type="email"
            required
            value={email}
            onChange={(e) => { setEmail(e.target.value); clearFieldError("email"); }}
            placeholder="e.g. anita.sharma@northbridge.edu.in"
            error={fieldErrors.email}
          />
          <TextField
            id="create-mobile"
            label="Mobile number (E.164)"
            required
            value={mobile}
            onChange={(e) => { setMobile(e.target.value); clearFieldError("mobile"); }}
            placeholder="+919876543210"
            error={fieldErrors.mobile}
          />
          <TextField
            id="create-national-id"
            label="Aadhaar / National ID (Last 4 digits only)"
            value={nationalIdLast4}
            maxLength={4}
            onChange={(e) => { setNationalIdLast4(e.target.value.replace(/\D/g, "")); clearFieldError("nationalIdLast4"); }}
            placeholder="e.g. 5678"
            error={fieldErrors.nationalIdLast4}
          />
          <TextField
            id="create-initial-role"
            label="Initial role code (optional)"
            value={initialRoleCode}
            onChange={(e) => { setInitialRoleCode(e.target.value); clearFieldError("initialRoleCode"); }}
            placeholder="e.g. TEACHER, STUDENT, REGISTRAR"
            error={fieldErrors.initialRoleCode}
          />
        </div>
        <p className="platform-form-note">
          This form provisions a general user account. For admissions, student profile creation and section enrolment, use the separate Create student profile workflow.
        </p>

        <div style={{ marginTop: "1rem" }}>
          <Kicker>ACCESS SCOPE</Kicker>
          <ScopeFields scope={scope} onChange={setScope} idPrefix="create-user" />
        </div>

        <div
          className="platform-modal-note"
          style={{
            marginTop: "0.75rem",
            fontSize: "0.8rem",
            color: "var(--color-text-secondary, #64748b)",
          }}
        >
          Statutory Notice: Indian DPDP Act 2023 &amp; UIDAI Guidelines [R2-34] prohibit storing full
          Aadhaar numbers. Only the last 4 digits are accepted for identity masking.
        </div>

        {formError && (
          <div style={{ marginTop: "0.75rem" }}>
            <Notice tone="danger">{formError}</Notice>
          </div>
        )}

        <footer style={{ marginTop: "20px", display: "flex", justifyContent: "flex-end", gap: "8px" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" busy={submitting}>
            Create user
          </Button>
        </footer>
      </form>
    </Modal>
  );
}

export default CreateUserModal;
