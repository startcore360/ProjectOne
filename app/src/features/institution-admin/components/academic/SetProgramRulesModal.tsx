import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, DateField } from "../../../../design-system/components";
import { JsonDocumentBuilder, type DocumentValue } from "../../../../design-system/components/JsonDocumentBuilder";
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
  const [ruleDocument, setRuleDocument] = useState<Record<string, DocumentValue>>({ minCredits: 160 });
  const [ruleEffectiveFrom, setRuleEffectiveFrom] = useState(getLocalDateString());
  const [modalError, setModalError] = useState("");

  const handleClose = () => {
    setModalError("");
    onClose();
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setModalError("");
    if (Object.keys(ruleDocument).length === 0) {
      setModalError("Add at least one programme requirement before saving.");
      return;
    }
    if (!ruleEffectiveFrom) {
      setModalError("Choose when these requirements take effect.");
      return;
    }
    try {
      await onSubmit(JSON.stringify(ruleDocument), ruleEffectiveFrom);
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
      size="lg"
    >
      <form onSubmit={handleSubmit} className="app-form" noValidate>
        {modalError && <Notice tone="danger">{modalError}</Notice>}
        <div>
          <JsonDocumentBuilder
            title="Programme requirements"
            description="Add the requirements that apply to this programme. You can use numbers, text, yes/no values, groups and lists."
            value={ruleDocument}
            onChange={setRuleDocument}
          />
        </div>
        <div>
          <DateField
            id="rule-effective-from"
            label="Effective from"
            value={ruleEffectiveFrom}
            onChange={(e) => setRuleEffectiveFrom(e.target.value)}
            required
          />
        </div>
        <footer className="app-modal__footer">
          <Button type="button" variant="secondary" onClick={handleClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" busy={saving}>
            {saving ? "Saving..." : "Save Rules"}
          </Button>
        </footer>
      </form>
    </Modal>
  );
}

export default SetProgramRulesModal;
