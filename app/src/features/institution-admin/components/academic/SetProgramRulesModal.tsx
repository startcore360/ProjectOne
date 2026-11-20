import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextAreaField, DateField } from "../../../../design-system/components";
import { getLocalDateString } from "../../../../utils/dateUtils";
import { getApiErrorMessage } from "../../../../services/apiHelper";

export interface SetProgramRulesModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (ruleJson: string, effectiveFrom: string) => Promise<void>;
  saving: boolean;
}

export function SetProgramRulesModal({
  isOpen,
  onClose,
  onSubmit,
  saving,
}: SetProgramRulesModalProps) {
  const [ruleJson, setRuleJson] = useState('{\n  "minCredits": 160,\n  "maxYears": 6\n}');
  const [ruleEffectiveFrom, setRuleEffectiveFrom] = useState(getLocalDateString());
  const [modalError, setModalError] = useState("");

  const handleClose = () => {
    setModalError("");
    onClose();
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setModalError("");
    try {
      await onSubmit(ruleJson, ruleEffectiveFrom);
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
      title="Set Curriculum Rules"
      titleId="rules-modal-title"
      size="sm"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }} noValidate>
        {modalError && (
          <div style={{ marginBottom: "var(--space-3)" }}>
            <Notice tone="danger">{modalError}</Notice>
          </div>
        )}
        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextAreaField
            id="rule-json"
            label="Rules JSON"
            rows={4}
            value={ruleJson}
            onChange={(e) => setRuleJson(e.target.value)}
            required
            style={{ fontFamily: "monospace" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-4)" }}>
          <DateField
            id="rule-effective-from"
            label="Effective From"
            value={ruleEffectiveFrom}
            onChange={(e) => setRuleEffectiveFrom(e.target.value)}
            required
          />
        </div>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button type="button" variant="secondary" onClick={handleClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? "Saving..." : "Save Rules"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default SetProgramRulesModal;
