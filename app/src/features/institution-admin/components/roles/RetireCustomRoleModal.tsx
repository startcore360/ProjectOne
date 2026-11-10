import { useState } from "react";
import { Button, Modal } from "../../../../design-system/components";
import {
  roleAssignmentService,
  type RoleDefinitionItem,
} from "../../api/roleAssignmentService";

export interface RetireCustomRoleModalProps {
  tenantId: string;
  role: RoleDefinitionItem | null;
  onClose: () => void;
  onRetired: (retiredRole: RoleDefinitionItem) => void;
}

export function RetireCustomRoleModal({
  tenantId,
  role,
  onClose,
  onRetired,
}: RetireCustomRoleModalProps) {
  const [retiring, setRetiring] = useState(false);

  if (!role) return null;

  const handleRetire = async () => {
    setRetiring(true);
    try {
      if (!role.roleDefinitionId) return;
      const res = await roleAssignmentService.retireCustomRole(tenantId, role.roleDefinitionId);
      onRetired(res);
      onClose();
    } catch {
      // handled by parent feedback
    } finally {
      setRetiring(false);
    }
  };

  return (
    <Modal
      isOpen={!!role}
      onClose={onClose}
      kicker="RETIRE ROLE · Rule 17"
      title={`Retire ${role.roleName}?`}
      titleId="retire-role-modal-title"
      size="sm"
    >
      <p style={{ fontSize: "0.875rem", color: "var(--color-muted)", marginBottom: "var(--space-4)" }}>
        Per statutory audit Rule 17, custom roles cannot be deleted. Retiring marks the role as{" "}
        <code>RETIRED</code> and prevents new assignments, while preserving all historical user
        assignments.
      </p>

      <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
        <Button type="button" variant="secondary" onClick={onClose} disabled={retiring}>
          Cancel
        </Button>
        <Button type="button" variant="danger" onClick={handleRetire} busy={retiring}>
          Confirm Retirement
        </Button>
      </div>
    </Modal>
  );
}

export default RetireCustomRoleModal;
