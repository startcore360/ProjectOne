import { useEffect } from "react";

type Props = { open: boolean; reason: string; onClose: () => void };

export default function PublishGateBlockModal({ open, reason, onClose }: Props) {
  useEffect(() => {
    if (!open) return;
    function onKey(event: KeyboardEvent) { if (event.key === "Escape") onClose(); }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);
  if (!open) return null;
  return <div role="presentation" className="modal-backdrop">
    <section role="dialog" aria-modal="true" aria-labelledby="publish-gate-title" className="modal-panel">
      <h2 id="publish-gate-title">Report card publication blocked</h2>
      <p>{reason}</p>
      <p>Publication remains disabled until all required moderation and fee-clearance checks pass.</p>
      <button type="button" onClick={onClose}>Close</button>
    </section>
  </div>;
}
