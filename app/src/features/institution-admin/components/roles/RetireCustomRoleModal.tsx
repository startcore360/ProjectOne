import { useState } from "react";
import { Button, Modal, Notice } from "../../../../design-system/components";
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
  const [error, setError] = useState<string | null>(null);

  if (!role) return null;

  const handleRetire = async () => {
    setRetiring(true);
    setError(null);
    try {
      if (!role.roleDefinitionId) return;
      const res = await roleAssignmentService.retireCustomRole(tenantId, role.roleDefinitionId);
      onRetired(res);
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to retire this role. Please try again.");
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
      <div className="app-form">
        <p className="app-panel__status-label">
          Per statutory audit Rule 17, custom roles cannot be deleted. Retiring marks the role as{" "}
          <code>RETIRED</code> and prevents new assignments, while preserving all historical user
          assignments.
        </p>

        {error && <Notice tone="danger">{error}</Notice>}

        <div className="app-form__actions">
          <Button type="button" variant="secondary" onClick={onClose} disabled={retiring}>
            Cancel
          </Button>
          <Button type="button" variant="danger" onClick={handleRetire} busy={retiring}>
            Confirm Retirement
          </Button>
        </div>
      </div>
    </Modal>
  );
}

export default RetireCustomRoleModal;
