import { useEffect, useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import {
  rolldownService,
  type RolldownOverride,
} from "../../api/rolldownService";
import { useTenantAccess } from "../../../profile/hooks/useTenantAccess";

export interface RolldownOverrideModalProps {
  tenantId: string;
  isOpen: boolean;
  initialMissingRoleCode?: string;
  initialPermissionKind?: string;
  onClose: () => void;
  onCreated: (override: RolldownOverride) => void;
}

function toRoleCode(value: string): string {
  return value.trim().toUpperCase().replace(/[^A-Z0-9]+/g, "_").replace(/^_+|_+$/g, "");
}

function formatRoleLabel(value: string): string {
  return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
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
  const { permissions } = useTenantAccess();
  const activeRoleCode = permissions?.activeRoleCode;

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
    if (!activeRoleCode) {
      setError("Choose an active role before saving this override.");
      return;
    }
    if (!reason.trim() || reason.trim().length < 5 || reason.trim().length > 500) {
      setError("Enter a reason between 5 and 500 characters.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const res = await rolldownService.createOverride(tenantId, activeRoleCode, {
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
      kicker="ROLE COVERAGE SETTINGS"
      title="Set role coverage override"
      titleId="override-modal-title"
      description="Approval responsibilities must always be assigned to an eligible role. Only eligible operational work can be withheld until a role is filled."
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
            label="Role without a current holder"
            required
            value={formatRoleLabel(missingRoleCode)}
            onChange={(e) => setMissingRoleCode(toRoleCode(e.target.value))}
          />
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--space-3)", marginBottom: "var(--space-3)" }}>
          <div>
            <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}>
              Work type
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
              <option value="APPROVE">Approval</option>
              <option value="OTHER">Operational work</option>
            </select>
          </div>
          <div>
            <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}>
              Handling mode
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
              <option value="ASSIGN">Assign to a stand-in role</option>
              <option value="WITHHOLD" disabled={permissionKind === "APPROVE"}>
                Withhold operational work
              </option>
            </select>
          </div>
        </div>

        {mode === "ASSIGN" && (
          <div style={{ marginBottom: "var(--space-3)" }}>
            <TextField
              id="target-standin-role"
              label="Stand-in role"
              required
              value={formatRoleLabel(targetRoleCode)}
              onChange={(e) => setTargetRoleCode(toRoleCode(e.target.value))}
            />
          </div>
        )}

        <div style={{ marginBottom: "var(--space-4)" }}>
          <label
            htmlFor="override-statutory-reason"
            style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "var(--space-1)" }}
          >
            Reason for this change (5–500 characters)
          </label>
          <textarea
            id="override-statutory-reason"
            rows={3}
            maxLength={500}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Explain why this temporary role coverage change is necessary."
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
