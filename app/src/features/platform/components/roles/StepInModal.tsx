import { useState } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";
import { readErrorMessage } from "../common/types";
import { platformRoleService } from "../../api/platformRoleService";

export interface StepInModalProps {
  actionCode: string;
  actionLabel: string;
  onClose: () => void;
  onDone: (message: string) => void;
}

export function StepInModal({ actionCode, actionLabel, onClose, onDone }: StepInModalProps) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const submit = async () => {
    if (reason.trim().length < 5) {
      setError("Give a reason of at least 5 characters.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await platformRoleService.stepIn(actionCode, reason.trim());
      onDone(`You've stepped in on "${actionLabel}" for 30 minutes. This is recorded.`);
    } catch (err) {
      setError(readErrorMessage(err, "Could not step in."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      isOpen
      onClose={onClose}
      kicker="STEP IN"
      title={actionLabel}
      description="You handed this to someone else. You can do it yourself for the next 30 minutes without taking it back. Your reason is recorded and can't be changed."
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button onClick={submit} busy={busy}>
            Step in
          </Button>
        </>
      }
    >
      <TextField
        id="platform-step-in-reason"
        label="Reason"
        required
        maxLength={500}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder="e.g. Release Manager is on leave today"
      />
      {error && <Notice tone="danger">{error}</Notice>}
    </Modal>
  );
}
