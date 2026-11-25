import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextAreaField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import { institutionService, type InstitutionItem } from "../../api/institutionService";

export interface BlockRequestModalProps {
  tenantId: string;
  institution: InstitutionItem | null;
  onClose: () => void;
  onSubmitted: (message: string) => void;
}

export function BlockRequestModal({
  tenantId,
  institution,
  onClose,
  onSubmitted,
}: BlockRequestModalProps) {
  const [reason, setReason] = useState("");
  const [blocking, setBlocking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!institution) return null;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!reason.trim()) {
      setError("Enter a reason for the block request.");
      return;
    }
    setBlocking(true);
    setError(null);
    try {
      const res = await institutionService.requestBlock(tenantId, institution.institutionId, reason.trim());
      onSubmitted(res.message);
      setReason("");
      onClose();
    } catch (err: unknown) {
      setError(getApiErrorMessage(err));
    } finally {
      setBlocking(false);
    }
  };

  return (
    <Modal
      isOpen={!!institution}
      onClose={() => {
        setReason("");
        setError(null);
        onClose();
      }}
      title={`Request to block ${institution.institutionName}`}
      titleId="block-modal-title"
      description="Submit a formal institution block request for Vice Chancellor review."
      size="sm"
    >
      {error && (
        <div style={{ marginBottom: "var(--space-3)" }}>
          <Notice tone="danger">{error}</Notice>
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: "var(--space-4)" }}>
          <TextAreaField
            id="block-reason"
            label="Reason for blocking *"
            required
            rows={4}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. Repeated regulatory non-compliance during state board inspection."
          />
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button
            variant="secondary"
            onClick={() => {
              setReason("");
              setError(null);
              onClose();
            }}
            disabled={blocking}
          >
            Cancel
          </Button>
          <Button type="submit" variant="danger" busy={blocking}>
            Submit block request
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default BlockRequestModal;
