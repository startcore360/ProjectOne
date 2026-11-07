import { useState, type FormEvent } from "react";
import { Button, Modal, Notice } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import { institutionService, type InstitutionItem } from "../../api/institutionService";

export interface GovernanceDecisionModalProps {
  tenantId: string;
  institution: InstitutionItem | null;
  onClose: () => void;
  onDecided: (message: string) => void;
}

export function GovernanceDecisionModal({
  tenantId,
  institution,
  onClose,
  onDecided,
}: GovernanceDecisionModalProps) {
  const [approved, setApproved] = useState(true);
  const [notes, setNotes] = useState("");
  const [deciding, setDeciding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!institution) return null;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setDeciding(true);
    setError(null);
    try {
      const res = await institutionService.decideBlock(
        tenantId,
        institution.institutionId,
        approved,
        notes.trim() || undefined
      );
      onDecided(res.message);
      setNotes("");
      onClose();
    } catch (err: unknown) {
      setError(getApiErrorMessage(err));
    } finally {
      setDeciding(false);
    }
  };

  return (
    <Modal
      isOpen={!!institution}
      onClose={() => {
        setNotes("");
        setError(null);
        onClose();
      }}
      title={`Review Block Decision: ${institution.institutionName}`}
      titleId="decide-modal-title"
      description="Executive governance decision on blocking this institution [Story #28: INSTITUTION_BLOCK_APPROVE]."
      size="sm"
    >
      {error && (
        <div style={{ marginBottom: "var(--space-3)" }}>
          <Notice tone="danger">{error}</Notice>
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-2)" }}>
            Executive Decision
          </label>
          <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
            <label style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", fontSize: "0.875rem", cursor: "pointer" }}>
              <input
                type="radio"
                name="decision-choice"
                checked={approved}
                onChange={() => setApproved(true)}
              />
              Approve Block (Institution access blocked immediately)
            </label>
            <label style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", fontSize: "0.875rem", cursor: "pointer" }}>
              <input
                type="radio"
                name="decision-choice"
                checked={!approved}
                onChange={() => setApproved(false)}
              />
              Reject Block / Keep Active
            </label>
          </div>
        </div>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <label
            htmlFor="decision-notes"
            style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}
          >
            Decision Notes
          </label>
          <textarea
            id="decision-notes"
            rows={3}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Official executive findings and notes..."
            style={{
              width: "100%",
              padding: "var(--space-2)",
              borderRadius: "var(--radius-md, 4px)",
              border: "1px solid var(--color-border, #ccc)",
            }}
          />
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button
            variant="secondary"
            onClick={() => {
              setNotes("");
              setError(null);
              onClose();
            }}
            disabled={deciding}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant={approved ? "danger" : "primary"}
            busy={deciding}
          >
            {approved ? "Approve Block" : "Reject Block"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default GovernanceDecisionModal;
