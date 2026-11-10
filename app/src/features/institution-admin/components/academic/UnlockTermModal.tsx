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
    if (!reason.trim()) return;
    await onSubmit(reason.trim(), hours);
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
      kicker="TERM_UNLOCK • Action #44 / Rule AT-09"
      title="Temporary Term Unlock"
      titleId="unlock-modal-title"
      description="Per statutory Rule AT-09, unlock requires an explicit reason, lasts at most 3 days (72 hours), and automatically relocks. HOD and Admin are notified."
      size="sm"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }}>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <label
            htmlFor="unlock-reason"
            style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}
          >
            Statutory Justification / Reason *
          </label>
          <textarea
            id="unlock-reason"
            rows={3}
            required
            placeholder="e.g. Grievance committee approved re-evaluation for cohort"
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
        </div>

        <div style={{ marginBottom: "var(--space-4)" }}>
          <label
            htmlFor="unlock-duration"
            style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}
          >
            Duration in Hours (Max 72 hours / 3 days)
          </label>
          <input
            id="unlock-duration"
            type="number"
            min={1}
            max={72}
            value={hours}
            onChange={(e) => setHours(Number(e.target.value))}
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
          <Button type="submit" disabled={unlocking || !reason.trim()} busy={unlocking}>
            Confirm Unlock
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default UnlockTermModal;
