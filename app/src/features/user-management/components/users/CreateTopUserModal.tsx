import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";
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

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const { data } = await userManagementService.createTopUser(tenantId, {
        roleCode,
        displayName,
        email,
        mobile: mobile ? mobile : undefined,
      });
      onCreated(data);
    } catch (err) {
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
      <form onSubmit={handleSubmit} noValidate style={{ padding: "0.5rem 0" }}>
        {error && (
          <div style={{ marginBottom: "1rem" }}>
            <Notice tone="danger">{error}</Notice>
          </div>
        )}

        <div style={{ marginBottom: "1rem" }}>
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

        <div style={{ marginBottom: "1rem" }}>
          <TextField
            id="top-user-name"
            label="Full Name"
            required
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            placeholder="e.g. Prof. Suresh Chandra"
          />
        </div>

        <div style={{ marginBottom: "1rem" }}>
          <TextField
            id="top-user-email"
            label="Email Address"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="e.g. vc@northbridge.edu.in"
          />
        </div>

        <div style={{ marginBottom: "1rem" }}>
          <TextField
            id="top-user-mobile"
            label="Mobile Number (Optional, E.164)"
            value={mobile}
            onChange={(e) => setMobile(e.target.value)}
            placeholder="+919876543210"
          />
        </div>

        <div
          style={{
            padding: "0.75rem",
            backgroundColor: "var(--color-surface-subtle, #f3f4f6)",
            borderRadius: "6px",
            fontSize: "0.8125rem",
            color: "var(--color-muted)",
            marginBottom: "1rem",
          }}
        >
          <strong>Governance Policies:</strong>
          <ul style={{ margin: "0.25rem 0 0 1.25rem", padding: 0 }}>
            <li>Two-step login (MFA) is required at first sign-in.</li>
            <li>You cannot create or assign an account to your own identity.</li>
            <li>Management and Governing Body Approver accounts are governance roles; Vice Chancellor and Data Protection Officer accounts are staff roles.</li>
          </ul>
        </div>

        <footer style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
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
