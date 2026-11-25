import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import {
  leaveApproverService,
  type ApproverKind,
} from "../../api/leaveApproverService";

export interface ViceChancellorModalProps {
  tenantId: string;
  isOpen: boolean;
  onClose: () => void;
  onSaved: (message: string) => void;
}

export function ViceChancellorModal({
  tenantId,
  isOpen,
  onClose,
  onSaved,
}: ViceChancellorModalProps) {
  const [approverKind, setApproverKind] = useState<ApproverKind>("GOVERNING_BODY_APPROVER");
  const [approverUserId, setApproverUserId] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!approverUserId.trim()) {
      setError("Enter the approver user account ID.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await leaveApproverService.setVcLeaveApprover(tenantId, {
        approverKind,
        approverUserId: approverUserId.trim(),
      });
      onSaved("Vice-Chancellor leave approver configured successfully.");
      setApproverUserId("");
      onClose();
    } catch (err: unknown) {
      setError(getApiErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={() => {
        setApproverUserId("");
        setError(null);
        onClose();
      }}
      title="Configure Vice-Chancellor's Leave Approver"
      titleId="vc-approver-modal-title"
      description="Choose who can approve leave requests submitted by the Vice-Chancellor."
      size="sm"
    >
      {error && (
        <div style={{ marginBottom: "var(--space-3)" }}>
          <Notice tone="danger">{error}</Notice>
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <SelectField
          id="vc-approver-kind"
          label="Approver Role / Kind *"
          required
          value={approverKind}
          onChange={(e) => setApproverKind(e.target.value as ApproverKind)}
        >
          <option value="GOVERNING_BODY_APPROVER">Governing Body Approver</option>
          <option value="MANAGEMENT">Management / Trustees</option>
        </SelectField>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <TextField
            id="vc-approver-user-id"
            label="Approver User Account ID (UUID) *"
            required
            value={approverUserId}
            onChange={(e) => setApproverUserId(e.target.value)}
            placeholder="00000000-0000-0000-0000-000000000010"
          />
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button
            variant="secondary"
            onClick={() => {
              setApproverUserId("");
              setError(null);
              onClose();
            }}
            disabled={saving}
          >
            Cancel
          </Button>
          <Button type="submit" busy={saving}>
            Save VC Approver
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default ViceChancellorModal;
