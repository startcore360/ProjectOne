import { useEffect, useState, type FormEvent } from "react";
import { Button, Modal, Notice, SelectField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import {
  leaveApproverService,
  type ApproverKind,\n  type LeaveApproverCandidate,
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
  const [approverUserId, setApproverUserId] = useState("");\n  const [candidates, setCandidates] = useState<LeaveApproverCandidate[]>([]);\n  const [loadingCandidates, setLoadingCandidates] = useState(false);\n  const [candidateError, setCandidateError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!institution) return;
    let cancelled = false;
    setLoadingCandidates(true);
    setCandidateError(null);
    setCandidates([]);
    setApproverUserId("");
    leaveApproverService.listPrincipalApproverCandidates(tenantId, institution.institutionId, approverKind)
      .then((items) => { if (!cancelled) setCandidates(items); })
      .catch((err: unknown) => {
        if (!cancelled) {
          setCandidates([]);
          setCandidateError(getApiErrorMessage(err));
        }
      })
      .finally(() => { if (!cancelled) setLoadingCandidates(false); });
    return () => { cancelled = true; };
  }, [tenantId, institution?.institutionId, approverKind]);

  if (!institution) return null;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!approverUserId) {
      setError("Choose an eligible approver account.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await leaveApproverService.setPrincipalLeaveApprover(tenantId, institution.institutionId, {
        approverKind,
        approverUserId,
      });
      onSaved(`Leave approver configured successfully for '${institution.institutionName}'.`);
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
      description="Choose who can approve leave requests submitted by the head or principal of this institution."
      size="sm"
    >
      {error && (
        <div style={{ marginBottom: "var(--space-3)" }}>
          <Notice tone="danger">{error}</Notice>
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <SelectField
          id="approver-kind"
          label="Approver Role / Kind *"
          required
          value={approverKind}
          onChange={(e) => setApproverKind(e.target.value as ApproverKind)}
        >
          <option value="MANAGEMENT">Management / Trustees</option>
          <option value="VICE_CHANCELLOR">Vice Chancellor</option>
          <option value="GOVERNING_BODY_APPROVER">Governing Body Approver</option>
        </SelectField>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <SelectField
            id="approver-user"
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
          {candidateError && <Notice tone="danger">{candidateError}</Notice>}
          {!loadingCandidates && !candidateError && candidates.length === 0 && (
            <p className="app-empty-inline">No eligible accounts found for this role. Create or assign the appropriate account in Users &amp; Roles first.</p>
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
            Save Approver
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default PrincipalLeaveApproverModal;
