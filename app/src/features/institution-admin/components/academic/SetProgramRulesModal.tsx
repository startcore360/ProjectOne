import { useState, type FormEvent } from "react";
import { Button, Modal } from "../../../../design-system/components";

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
  const [ruleEffectiveFrom, setRuleEffectiveFrom] = useState(
    new Date().toISOString().substring(0, 10),
  );

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    await onSubmit(ruleJson, ruleEffectiveFrom);
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="PROGRAM_RULES_SET • Action #30"
      title="Set Curriculum Rules"
      titleId="rules-modal-title"
      size="sm"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }}>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <label htmlFor="rule-json" style={{ display: "block", fontSize: "0.875rem", fontWeight: 600 }}>
            Rules JSON *
          </label>
          <textarea
            id="rule-json"
            rows={4}
            value={ruleJson}
            onChange={(e) => setRuleJson(e.target.value)}
            required
            style={{ width: "100%", padding: "var(--space-2)", fontFamily: "monospace" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-4)" }}>
          <label htmlFor="rule-effective-from" style={{ display: "block", fontSize: "0.875rem", fontWeight: 600 }}>
            Effective From *
          </label>
          <input
            id="rule-effective-from"
            type="date"
            value={ruleEffectiveFrom}
            onChange={(e) => setRuleEffectiveFrom(e.target.value)}
            required
            style={{ width: "100%", padding: "var(--space-2)" }}
          />
        </div>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>
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
