import { useState, type FormEvent } from "react";
import { Button, Modal } from "../../../../design-system/components";

export interface UnlockTermModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (reason: string, hours: number) => Promise<void>;
  unlocking: boolean;
}

export function UnlockTermModal({
  isOpen,
  onClose,
  onSubmit,
  unlocking,
}: UnlockTermModalProps) {
  const [reason, setReason] = useState("");
  const [hours, setHours] = useState(72);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (reason.trim().length < 20) return;
    await onSubmit(reason.trim(), 72);
    setReason("");
    setHours(72);
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={() => {
        setReason("");
        setHours(72);
        onClose();
      }}
      kicker="TEMPORARY ACCESS"
      title="Temporary Term Unlock"
      titleId="unlock-modal-title"
      description="Enter at least 20 characters explaining the emergency. Access is restored for exactly 72 hours, then the term locks automatically."
      size="sm"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }}>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <label
            htmlFor="unlock-reason"
            style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}
          >
            Statutory Justification / Reason * (minimum 20 characters)
          </label>
          <textarea
            id="unlock-reason"
            rows={3}
            required
            minLength={20}
            maxLength={500}
            placeholder="Explain the emergency and the reason for temporary access"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            style={{
              width: "100%",
              padding: "var(--space-2)",
              borderRadius: "var(--radius-md, 4px)",
              border: "1px solid var(--color-border, #ccc)",
              fontFamily: "inherit",
              fontSize: "0.875rem",
            }}
          />
          <small aria-live="polite">{reason.trim().length}/20 minimum characters</small>
        </div>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <label
            htmlFor="unlock-duration"
            style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}
          >
            Unlock Duration (fixed at 72 hours)
          </label>
          <input
            id="unlock-duration"
            type="number"
            value={72}
            disabled
            readOnly
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
            type="button"
            variant="secondary"
            onClick={() => {
              setReason("");
              setHours(72);
              onClose();
            }}
            disabled={unlocking}
          >
            Cancel
          </Button>
          <Button type="submit" disabled={unlocking || reason.trim().length < 20} busy={unlocking}>
            Confirm Unlock
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default UnlockTermModal;
