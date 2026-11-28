import { useState } from "react";
import Button from "../../../design-system/components/Button";
import Modal from "../../../design-system/components/Modal";

type HearingMinutesModalProps = {
  isOpen: boolean; onClose: () => void; onSave: (notes: string) => void;
  busy?: boolean; title?: string;
};

export default function HearingMinutesModal({ isOpen, onClose, onSave, busy = false,
  title = "Record hearing minutes" }: HearingMinutesModalProps) {
  const [notes, setNotes] = useState("");
  function close() { setNotes(""); onClose(); }
  return <Modal isOpen={isOpen} onClose={close} title={title} kicker="Confidential governance record"
    description="Record factual minutes only. The entry is append-only and visible only to authorized PO-T05 roles."
    size="lg" footer={<><Button variant="secondary" onClick={close} disabled={busy}>Cancel</Button>
      <Button onClick={() => onSave(notes.trim())} busy={busy} disabled={!notes.trim()}>Save minutes</Button></>}>
    <label className="block space-y-1"><span className="text-sm font-medium text-slate-700">Minutes</span>
      <textarea className="min-h-48 w-full rounded-lg border border-slate-300 p-3 text-sm"
        maxLength={5000} value={notes} onChange={event => setNotes(event.target.value)} />
      <span className="text-xs text-slate-500">Maximum 5,000 characters. Avoid unnecessary personal details.</span>
    </label>
  </Modal>;
}
