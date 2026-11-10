import { useState, type FormEvent } from "react";
import { Button, Modal } from "../../../../design-system/components";

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

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    await onSubmit({
      institutionId: promInstId.trim(),
      fromSessionId: promFromSessionId.trim(),
      toSessionId: promToSessionId.trim(),
    });
    setPromFromSessionId("");
    setPromToSessionId("");
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="PROMOTION_RUN • Action #39"
      title="Initiate Promotion Run"
      titleId="prom-modal-title"
      size="sm"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }}>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <label htmlFor="prom-inst-id" style={{ display: "block", fontSize: "0.875rem", fontWeight: 600 }}>
            Institution UUID *
          </label>
          <input
            id="prom-inst-id"
            type="text"
            value={promInstId}
            onChange={(e) => setPromInstId(e.target.value)}
            required
            style={{ width: "100%", padding: "var(--space-2)", fontFamily: "monospace" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <label htmlFor="prom-from-session-id" style={{ display: "block", fontSize: "0.875rem", fontWeight: 600 }}>
            From Session UUID *
          </label>
          <input
            id="prom-from-session-id"
            type="text"
            value={promFromSessionId}
            onChange={(e) => setPromFromSessionId(e.target.value)}
            required
            style={{ width: "100%", padding: "var(--space-2)", fontFamily: "monospace" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-4)" }}>
          <label htmlFor="prom-to-session-id" style={{ display: "block", fontSize: "0.875rem", fontWeight: 600 }}>
            To Session UUID *
          </label>
          <input
            id="prom-to-session-id"
            type="text"
            value={promToSessionId}
            onChange={(e) => setPromToSessionId(e.target.value)}
            required
            style={{ width: "100%", padding: "var(--space-2)", fontFamily: "monospace" }}
          />
        </div>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>
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
