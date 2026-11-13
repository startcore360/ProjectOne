import { useState, type FormEvent } from "react";
import { Button, Modal } from "../../../../design-system/components";

export interface SetGradingSchemeModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (schemeName: string, effectiveFrom: string) => Promise<void>;
  saving: boolean;
}

export function SetGradingSchemeModal({
  isOpen,
  onClose,
  onSubmit,
  saving,
}: SetGradingSchemeModalProps) {
  const [schemeName, setSchemeName] = useState("Standard 10-Point CGPA Scale");
  const [schemeEffectiveFrom, setSchemeEffectiveFrom] = useState(
    new Date().toISOString().substring(0, 10),
  );

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    await onSubmit(schemeName, schemeEffectiveFrom);
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="ACADEMIC GOVERNANCE"
      title="Set Grading Scheme"
      titleId="scheme-modal-title"
      size="sm"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }}>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <label htmlFor="scheme-name" style={{ display: "block", fontSize: "0.875rem", fontWeight: 600 }}>
            Scheme Name *
          </label>
          <input
            id="scheme-name"
            type="text"
            value={schemeName}
            onChange={(e) => setSchemeName(e.target.value)}
            required
            style={{ width: "100%", padding: "var(--space-2)" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-4)" }}>
          <label htmlFor="scheme-effective-from" style={{ display: "block", fontSize: "0.875rem", fontWeight: 600 }}>
            Effective From *
          </label>
          <input
            id="scheme-effective-from"
            type="date"
            value={schemeEffectiveFrom}
            onChange={(e) => setSchemeEffectiveFrom(e.target.value)}
            required
            style={{ width: "100%", padding: "var(--space-2)" }}
          />
        </div>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? "Saving..." : "Save Scheme"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default SetGradingSchemeModal;
