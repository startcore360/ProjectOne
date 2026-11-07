import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import {
  leaveApproverService,
  type ApproverKind,
} from "../../api/leaveApproverService";
import type { InstitutionItem } from "../../api/institutionService";

export interface PrincipalLeaveApproverModalProps {
  tenantId: string;
  institution: InstitutionItem | null;
  onClose: () => void;
  onSaved: (message: string) => void;
}

export function PrincipalLeaveApproverModal({
  tenantId,
  institution,
  onClose,
  onSaved,
}: PrincipalLeaveApproverModalProps) {
  const [approverKind, setApproverKind] = useState<ApproverKind>("MANAGEMENT");
  const [approverUserId, setApproverUserId] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!institution) return null;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!approverUserId.trim()) {
      setError("Approver User ID is required [Rule C7].");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await leaveApproverService.setPrincipalLeaveApprover(tenantId, institution.institutionId, {
        approverKind,
        approverUserId: approverUserId.trim(),
      });
      onSaved(`Leave approver configured successfully for '${institution.institutionName}' [Rule C7].`);
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
      isOpen={!!institution}
      onClose={() => {
        setApproverUserId("");
        setError(null);
        onClose();
      }}
      title={`Configure Leave Approver: ${institution.institutionName}`}
      titleId="approver-modal-title"
      description="Designate who approves leaves submitted by the head/principal of this institution [Rule C7]."
      size="sm"
    >
      {error && (
        <div style={{ marginBottom: "var(--space-3)" }}>
          <Notice tone="danger">{error}</Notice>
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <label
            htmlFor="approver-kind"
            style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}
          >
            Approver Role / Kind *
          </label>
          <select
            id="approver-kind"
            value={approverKind}
            onChange={(e) => setApproverKind(e.target.value as ApproverKind)}
            style={{
              width: "100%",
              padding: "var(--space-2)",
              borderRadius: "var(--radius-md, 4px)",
              border: "1px solid var(--color-border, #ccc)",
            }}
          >
            <option value="MANAGEMENT">MANAGEMENT (Management / Trustees)</option>
            <option value="VICE_CHANCELLOR">VICE_CHANCELLOR (University VC)</option>
            <option value="GOVERNING_BODY_APPROVER">GOVERNING_BODY_APPROVER (Governing Body)</option>
          </select>
        </div>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <TextField
            id="approver-user-id"
            label="Approver User Account ID (UUID) *"
            required
            value={approverUserId}
            onChange={(e) => setApproverUserId(e.target.value)}
            placeholder="00000000-0000-0000-0000-000000000009"
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
            Save Approver
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default PrincipalLeaveApproverModal;
