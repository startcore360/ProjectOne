import { useEffect, useState, type FormEvent } from "react";
import { Button, Modal, Notice, SelectField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import {
  leaveApproverService,
  type ApproverKind,
  type LeaveApproverCandidate,
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
  const [candidates, setCandidates] = useState<LeaveApproverCandidate[]>([]);
  const [loadingCandidates, setLoadingCandidates] = useState(false);
  const [candidateError, setCandidateError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    setLoadingCandidates(true);
    setCandidateError(null);
    setApproverUserId("");
    leaveApproverService.listVcApproverCandidates(tenantId, approverKind)
      .then((items) => { if (!cancelled) setCandidates(items); })
      .catch((err: unknown) => {
        if (!cancelled) {
          setCandidates([]);
          setCandidateError(getApiErrorMessage(err));
        }
      })
      .finally(() => { if (!cancelled) setLoadingCandidates(false); });
    return () => { cancelled = true; };
  }, [tenantId, isOpen, approverKind]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!approverUserId) {
      setError("Choose an eligible approver account.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await leaveApproverService.setVcLeaveApprover(tenantId, {
        approverKind,
        approverUserId,
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
          <SelectField
            id="vc-approver-user"
            label="Select Approver *"
            required
            value={approverUserId}
            disabled={loadingCandidates || candidates.length === 0}
            onChange={(e) => setApproverUserId(e.target.value)}
          >
            <option value="">{loadingCandidates ? "Loading eligible accounts…" : "Choose an approver"}</option>
            {candidates.map((candidate) => (
              <option key={candidate.userAccountId} value={candidate.userAccountId}>
                {candidate.displayName} · {candidate.email}
              </option>
            ))}
          </SelectField>
          {candidateError && (
            <Notice tone={candidateError.includes("INVALID_TENANT_TYPE") || candidateError.toLowerCase().includes("university group") ? "warning" : "danger"}>
              {candidateError.includes("INVALID_TENANT_TYPE") || candidateError.toLowerCase().includes("university group")
                ? "Vice-Chancellor leave approval only applies to University Group tenants [Rule C6]. Individual colleges and schools configure Principal leave approvers instead."
                : candidateError}
            </Notice>
          )}
          {!loadingCandidates && !candidateError && candidates.length === 0 && (
            <p className="app-empty-inline">No eligible accounts found. Create a Management or Governing Body Approver account first in Users &amp; Roles.</p>
          )}
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
          <Button type="submit" busy={saving} disabled={saving || loadingCandidates || !approverUserId}>
            Save VC Approver
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default ViceChancellorModal;
