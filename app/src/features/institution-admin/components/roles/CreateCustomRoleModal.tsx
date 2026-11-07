import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import {
  roleAssignmentService,
  type RoleDefinitionItem,
} from "../../api/roleAssignmentService";

export interface CreateCustomRoleModalProps {
  tenantId: string;
  isOpen: boolean;
  onClose: () => void;
  onCreated: (newRole: RoleDefinitionItem, inputRoleCode: string) => void;
}

export function CreateCustomRoleModal({
  tenantId,
  isOpen,
  onClose,
  onCreated,
}: CreateCustomRoleModalProps) {
  const [roleCode, setRoleCode] = useState("");
  const [roleName, setRoleName] = useState("");
  const [roleLevel, setRoleLevel] = useState("COLLEGE");
  const [customScope, setCustomScope] = useState("INSTITUTION");
  const [actionCodes, setActionCodes] = useState("USER_VIEW");
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const resetForm = () => {
    setRoleCode("");
    setRoleName("");
    setRoleLevel("COLLEGE");
    setCustomScope("INSTITUTION");
    setActionCodes("USER_VIEW");
    setError(null);
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!roleCode.trim() || !roleName.trim()) {
      setError("Role Code and Role Name are required.");
      return;
    }
    setCreating(true);
    setError(null);
    try {
      const parsedActions = actionCodes
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);

      const res = await roleAssignmentService.createCustomRole(tenantId, {
        roleCode: roleCode.trim().toUpperCase(),
        roleName: roleName.trim(),
        roleLevel,
        customScope,
        actionCodes: parsedActions.length > 0 ? parsedActions : ["USER_VIEW"],
      });
      const code = roleCode.trim().toUpperCase();
      resetForm();
      onCreated(res, code);
      onClose();
    } catch (err: unknown) {
      setError(getApiErrorMessage(err));
    } finally {
      setCreating(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={() => {
        resetForm();
        onClose();
      }}
      kicker="ROLE_CUSTOM_MANAGE • Action #16"
      title="Create Custom Role"
      titleId="custom-role-modal-title"
      description="Define a tenant-specific custom role extending the institution catalog [Rule 16]."
      size="md"
    >
      {error && (
        <div style={{ marginBottom: "var(--space-3)" }}>
          <Notice tone="danger">{error}</Notice>
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="custom-role-code"
            label="Role Code *"
            required
            value={roleCode}
            onChange={(e) => setRoleCode(e.target.value.toUpperCase())}
            placeholder="e.g. CUSTOM_LAB_COORD"
          />
        </div>

        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="custom-role-name"
            label="Role Name *"
            required
            value={roleName}
            onChange={(e) => setRoleName(e.target.value)}
            placeholder="e.g. Lab Coordinator"
          />
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--space-3)", marginBottom: "var(--space-3)" }}>
          <div>
            <label htmlFor="custom-role-level" style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}>
              Role Level
            </label>
            <select
              id="custom-role-level"
              value={roleLevel}
              onChange={(e) => setRoleLevel(e.target.value)}
              style={{
                width: "100%",
                padding: "var(--space-2)",
                borderRadius: "var(--radius-md, 4px)",
                border: "1px solid var(--color-border, #ccc)",
              }}
            >
              <option value="UNIVERSITY">University</option>
              <option value="COLLEGE">College</option>
              <option value="SCHOOL">School</option>
            </select>
          </div>
          <div>
            <label htmlFor="custom-role-scope" style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}>
              Scope
            </label>
            <select
              id="custom-role-scope"
              value={customScope}
              onChange={(e) => setCustomScope(e.target.value)}
              style={{
                width: "100%",
                padding: "var(--space-2)",
                borderRadius: "var(--radius-md, 4px)",
                border: "1px solid var(--color-border, #ccc)",
              }}
            >
              <option value="TENANT">Tenant wide</option>
              <option value="INSTITUTION">Institution</option>
              <option value="DEPARTMENT">Department</option>
            </select>
          </div>
        </div>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <TextField
            id="custom-action-codes"
            label="Action Catalog Codes (comma separated)"
            value={actionCodes}
            onChange={(e) => setActionCodes(e.target.value)}
            placeholder="USER_VIEW, COURSE_VIEW, ATTENDANCE_RECORD"
          />
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              resetForm();
              onClose();
            }}
            disabled={creating}
          >
            Cancel
          </Button>
          <Button type="submit" busy={creating}>
            Create Role
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default CreateCustomRoleModal;
