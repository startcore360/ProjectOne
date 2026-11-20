import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";

export interface PromotionRunModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: {
    institutionId: string;
    fromSessionId: string;
    toSessionId: string;
  }) => Promise<void>;
  saving: boolean;
}

export function PromotionRunModal({
  isOpen,
  onClose,
  onSubmit,
  saving,
}: PromotionRunModalProps) {
  const [promInstId, setPromInstId] = useState("");
  const [promFromSessionId, setPromFromSessionId] = useState("");
  const [promToSessionId, setPromToSessionId] = useState("");
  const [modalError, setModalError] = useState("");

  const handleClose = () => {
    setModalError("");
    onClose();
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setModalError("");
    try {
      await onSubmit({
        institutionId: promInstId.trim(),
        fromSessionId: promFromSessionId.trim(),
        toSessionId: promToSessionId.trim(),
      });
      setPromFromSessionId("");
      setPromToSessionId("");
      handleClose();
    } catch (err: unknown) {
      setModalError(getApiErrorMessage(err));
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      kicker="YEAR-END PROMOTION"
      title="Initiate Promotion Run"
      titleId="prom-modal-title"
      size="sm"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }} noValidate>
        {modalError && (
          <div style={{ marginBottom: "var(--space-3)" }}>
            <Notice tone="danger">{modalError}</Notice>
          </div>
        )}
        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="prom-inst-id"
            label="Institution UUID *"
            type="text"
            value={promInstId}
            onChange={(e) => setPromInstId(e.target.value)}
            required
            style={{ fontFamily: "monospace" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="prom-from-session-id"
            label="From Session UUID *"
            type="text"
            value={promFromSessionId}
            onChange={(e) => setPromFromSessionId(e.target.value)}
            required
            style={{ fontFamily: "monospace" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-4)" }}>
          <TextField
            id="prom-to-session-id"
            label="To Session UUID *"
            type="text"
            value={promToSessionId}
            onChange={(e) => setPromToSessionId(e.target.value)}
            required
            style={{ fontFamily: "monospace" }}
          />
        </div>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button type="button" variant="secondary" onClick={handleClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? "Saving..." : "Create Run"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default PromotionRunModal;
