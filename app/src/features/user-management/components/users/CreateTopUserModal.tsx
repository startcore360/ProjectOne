import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, PhoneField, SelectField, TextField } from "../../../../design-system/components";
import { useFormErrors } from "../../../../hooks";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import {
  userManagementService,
  type TopUserResponse,
  type TopUserRoleCode,
} from "../../api/userManagementService";

export interface CreateTopUserModalProps {
  tenantId: string;
  isOpen: boolean;
  onClose: () => void;
  onCreated: (topUser: TopUserResponse) => void;
}

export function CreateTopUserModal({
  tenantId,
  isOpen,
  onClose,
  onCreated,
}: CreateTopUserModalProps) {
  const [roleCode, setRoleCode] = useState<TopUserRoleCode>("VICE_CHANCELLOR");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [mobile, setMobile] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { fieldErrors, setFieldError, clearFieldError, clearAll, handleApiError } = useFormErrors();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    clearAll();
    try {
      const { data } = await userManagementService.createTopUser(tenantId, {
        roleCode,
        displayName,
        email,
        mobile: mobile ? mobile : undefined,
      });
      onCreated(data);
    } catch (err) {
      handleApiError(err);
      setError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="GOVERNANCE ACCOUNT SETUP"
      title="Create governance / top user"
      titleId="governance-user-title"
      description="Create executive leadership and governance accounts for this tenant."
      size="md"
    >
      <form onSubmit={handleSubmit} noValidate style={{ padding: "var(--space-2) 0" }}>
        {error && (
          <div style={{ marginBottom: "var(--space-4)" }}>
            <Notice tone="danger">{error}</Notice>
          </div>
        )}

        <div style={{ marginBottom: "var(--space-4)" }}>
          <SelectField
            id="top-role-select"
            label="Governance role"
            value={roleCode}
            onChange={(e) => setRoleCode(e.target.value as TopUserRoleCode)}
            options={[
              { value: "VICE_CHANCELLOR", label: "Vice Chancellor (academic and executive head)" },
              { value: "GOVERNING_BODY_APPROVER", label: "Governing Body Approver" },
              { value: "MANAGEMENT", label: "Management or trustee" },
              { value: "DATA_PROTECTION_CONTACT", label: "Data Protection Officer" },
            ]}
          />
        </div>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <TextField
            id="top-user-name"
            label="Full Name"
            required
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            placeholder="e.g. Prof. Suresh Chandra"
            fieldName="displayName"
            error={fieldErrors.displayName}
            setFieldError={setFieldError}
            clearFieldError={clearFieldError}
          />
        </div>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <TextField
            id="top-user-email"
            label="Email Address"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="e.g. vc@northbridge.edu.in"
            fieldName="email"
            error={fieldErrors.email}
            setFieldError={setFieldError}
            clearFieldError={clearFieldError}
          />
        </div>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <PhoneField
            id="top-user-mobile"
            label="Mobile number"
            value={mobile}
            onChange={(e) => {
              setMobile(e.target.value);
              clearFieldError("mobile");
            }}
            fieldName="mobile"
            error={fieldErrors.mobile}
            setFieldError={setFieldError}
            clearFieldError={clearFieldError}
          />
        </div>

        <div
          style={{
            padding: "var(--space-3)",
            backgroundColor: "var(--color-bg-subtle)",
            borderRadius: "6px",
            fontSize: "0.8125rem",
            color: "var(--color-text-muted)",
            marginBottom: "var(--space-4)",
          }}
        >
          <strong>Governance Policies:</strong>
          <ul style={{ margin: "var(--space-1) 0 0 var(--space-5)", padding: 0 }}>
            <li>Two-step login (MFA) is required at first sign-in.</li>
            <li>You cannot create or assign an account to your own identity.</li>
            <li>Management and Governing Body Approver accounts are governance roles; Vice Chancellor and Data Protection Officer accounts are staff roles.</li>
          </ul>
        </div>

        <footer style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" busy={loading}>
            Create governance account
          </Button>
        </footer>
      </form>
    </Modal>
  );
}

export default CreateTopUserModal;
