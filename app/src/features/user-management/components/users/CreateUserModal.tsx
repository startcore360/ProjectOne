import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, PhoneField, SelectField, TextField, validateNationalIdLast4 } from "../../../../design-system/components";
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
    setFieldError,
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
      kicker="USER ACCOUNT"
      title="Create user account"
      titleId="create-user-title"
      description="Create a sign-in account and choose the organisation access for this person."
      size="lg"
    >
      <form onSubmit={handleSubmit} className="app-form">
        <section className="app-form-single-column" aria-label="Account details">
          <TextField
            id="create-display-name"
            label="Full name"
            required
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            placeholder="e.g. Dr. Anita Sharma"
            error={fieldErrors.displayName}
            fieldName="displayName"
            setFieldError={setFieldError}
            clearFieldError={clearFieldError}
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
            onChange={(e) => setEmail(e.target.value)}
            placeholder="e.g. anita.sharma@northbridge.edu.in"
            error={fieldErrors.email}
            fieldName="email"
            setFieldError={setFieldError}
            clearFieldError={clearFieldError}
          />
          <PhoneField
            id="create-mobile"
            label="Mobile number"
            required
            value={mobile}
            onChange={(e) => { setMobile(e.target.value); clearFieldError("mobile"); }}
            error={fieldErrors.mobile}
            fieldName="mobile"
            setFieldError={setFieldError}
            clearFieldError={clearFieldError}
          />
          <TextField
            id="create-national-id"
            label="Aadhaar / National ID (Last 4 digits only)"
            value={nationalIdLast4}
            maxLength={4}
            onChange={(e) => setNationalIdLast4(e.target.value.replace(/\D/g, ""))}
            placeholder="e.g. 5678"
            error={fieldErrors.nationalIdLast4}
            fieldName="nationalIdLast4"
            validator={(val) => validateNationalIdLast4(val, false)}
            setFieldError={setFieldError}
            clearFieldError={clearFieldError}
          />
          <TextField
            id="create-initial-role"
            label="Initial role (optional)"
            hint="Enter a role code supplied by your institution administrator."
            value={initialRoleCode}
            onChange={(e) => { setInitialRoleCode(e.target.value); clearFieldError("initialRoleCode"); }}
            placeholder="e.g. TEACHER, STUDENT, REGISTRAR"
            error={fieldErrors.initialRoleCode}
          />
        </section>
        <p className="app-note">
          This form provisions a general user account. For admissions, student profile creation and section enrolment, use the separate Create student profile workflow.
        </p>

        <section className="app-form-section" aria-labelledby="create-user-scope-title">
          <h3 id="create-user-scope-title">Access scope</h3>
          <ScopeFields tenantId={tenantId} scope={scope} onChange={setScope} idPrefix="create-user" />
        </section>

        <div className="app-note">
          Statutory Notice: Indian DPDP Act 2023 &amp; UIDAI Guidelines [R2-34] prohibit storing full
          Aadhaar numbers. Only the last 4 digits are accepted for identity masking.
        </div>

        {formError && <Notice tone="danger">{formError}</Notice>}

        <footer className="app-modal__footer">
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
