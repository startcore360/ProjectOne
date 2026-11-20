import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextField, DateField } from "../../../../design-system/components";
import { getLocalDateString } from "../../../../utils/dateUtils";
import { getApiErrorMessage } from "../../../../services/apiHelper";

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
  const [schemeEffectiveFrom, setSchemeEffectiveFrom] = useState(getLocalDateString());
  const [modalError, setModalError] = useState("");

  const handleClose = () => {
    setModalError("");
    onClose();
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setModalError("");
    try {
      await onSubmit(schemeName, schemeEffectiveFrom);
      handleClose();
    } catch (err: unknown) {
      setModalError(getApiErrorMessage(err));
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      kicker="ACADEMIC GOVERNANCE"
      title="Set Grading Scheme"
      titleId="scheme-modal-title"
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
            id="scheme-name"
            label="Scheme Name"
            type="text"
            value={schemeName}
            onChange={(e) => setSchemeName(e.target.value)}
            required
          />
        </div>
        <div style={{ marginBottom: "var(--space-4)" }}>
          <DateField
            id="scheme-effective-from"
            label="Effective From"
            value={schemeEffectiveFrom}
            onChange={(e) => setSchemeEffectiveFrom(e.target.value)}
            required
          />
        </div>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button type="button" variant="secondary" onClick={handleClose} disabled={saving}>
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
