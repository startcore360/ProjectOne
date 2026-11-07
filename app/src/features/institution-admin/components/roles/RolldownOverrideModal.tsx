import { useEffect, useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import {
  rolldownService,
  type RolldownOverride,
} from "../../api/rolldownService";

export interface RolldownOverrideModalProps {
  tenantId: string;
  isOpen: boolean;
  initialMissingRoleCode?: string;
  initialPermissionKind?: string;
  onClose: () => void;
  onCreated: (override: RolldownOverride) => void;
}

export function RolldownOverrideModal({
  tenantId,
  isOpen,
  initialMissingRoleCode,
  initialPermissionKind,
  onClose,
  onCreated,
}: RolldownOverrideModalProps) {
  const [missingRoleCode, setMissingRoleCode] = useState("VICE_CHANCELLOR");
  const [permissionKind, setPermissionKind] = useState("APPROVE");
  const [mode, setMode] = useState("ASSIGN");
  const [targetRoleCode, setTargetRoleCode] = useState("CONTROLLER_OF_EXAMINATIONS");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      if (initialMissingRoleCode) setMissingRoleCode(initialMissingRoleCode);
      if (initialPermissionKind) setPermissionKind(initialPermissionKind);
    }
  }, [isOpen, initialMissingRoleCode, initialPermissionKind]);

  const resetForm = () => {
    setMissingRoleCode("VICE_CHANCELLOR");
    setPermissionKind("APPROVE");
    setMode("ASSIGN");
    setTargetRoleCode("CONTROLLER_OF_EXAMINATIONS");
    setReason("");
    setError(null);
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!reason.trim() || reason.trim().length < 5) {
      setError("Statutory justification must be between 5 and 500 characters.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const res = await rolldownService.createOverride(tenantId, "UNIVERSITY_ADMIN", {
        missingRoleCode: missingRoleCode.trim().toUpperCase(),
        permissionKind,
        mode,
        targetRoleCode: mode === "ASSIGN" ? targetRoleCode.trim().toUpperCase() : undefined,
        reason: reason.trim(),
      });
      resetForm();
      onCreated(res);
      onClose();
    } catch (err: unknown) {
      setError(getApiErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={() => {
        resetForm();
        onClose();
      }}
      kicker="ROLLDOWN_OVERRIDE_SET • Action #24"
      title="Set Statutory Rolldown Override"
      titleId="override-modal-title"
      description="Per Guard RD-G2, approval actions can only be assigned to an active holder, never withheld. Non-approval actions may be withheld until the role is filled."
      size="md"
    >
      {error && (
        <div style={{ marginBottom: "var(--space-3)" }}>
          <Notice tone="danger">{error}</Notice>
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="missing-role-code"
            label="Missing Role Code *"
            required
            value={missingRoleCode}
            onChange={(e) => setMissingRoleCode(e.target.value.toUpperCase())}
          />
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--space-3)", marginBottom: "var(--space-3)" }}>
          <div>
            <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}>
              Kind
            </label>
            <select
              value={permissionKind}
              onChange={(e) => setPermissionKind(e.target.value)}
              style={{
                width: "100%",
                padding: "var(--space-2)",
                borderRadius: "var(--radius-md, 4px)",
                border: "1px solid var(--color-border, #ccc)",
              }}
            >
              <option value="APPROVE">APPROVE</option>
              <option value="OTHER">OTHER (Non-approval)</option>
            </select>
          </div>
          <div>
            <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}>
              Mode
            </label>
            <select
              value={mode}
              onChange={(e) => setMode(e.target.value)}
              style={{
                width: "100%",
                padding: "var(--space-2)",
                borderRadius: "var(--radius-md, 4px)",
                border: "1px solid var(--color-border, #ccc)",
              }}
            >
              <option value="ASSIGN">ASSIGN (Stand-in role)</option>
              <option value="WITHHOLD" disabled={permissionKind === "APPROVE"}>
                WITHHOLD (Non-approval only)
              </option>
            </select>
          </div>
        </div>

        {mode === "ASSIGN" && (
          <div style={{ marginBottom: "var(--space-3)" }}>
            <TextField
              id="target-standin-role"
              label="Target Stand-In Role *"
              required
              value={targetRoleCode}
              onChange={(e) => setTargetRoleCode(e.target.value.toUpperCase())}
            />
          </div>
        )}

        <div style={{ marginBottom: "var(--space-4)" }}>
          <label
            htmlFor="override-statutory-reason"
            style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}
          >
            Statutory Justification (5-500 chars) *
          </label>
          <textarea
            id="override-statutory-reason"
            rows={3}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Mandatory administrative justification for this roll-down override..."
            style={{
              width: "100%",
              padding: "var(--space-2)",
              borderRadius: "var(--radius-md, 4px)",
              border: "1px solid var(--color-border, #ccc)",
            }}
            required
          />
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              resetForm();
              onClose();
            }}
            disabled={submitting}
          >
            Cancel
          </Button>
          <Button type="submit" busy={submitting}>
            Save Override
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default RolldownOverrideModal;
