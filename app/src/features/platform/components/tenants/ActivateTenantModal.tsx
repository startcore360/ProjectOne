import type { FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";
import type { Tenant } from "../common/types";

export interface ActivateTenantModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
  tenant: Tenant | null;
  paymentRef: string;
  setPaymentRef: (val: string) => void;
  activateNote: string;
  setActivateNote: (val: string) => void;
  activating: boolean;
  activateError: string;
}

export function ActivateTenantModal({
  isOpen,
  onClose,
  onSubmit,
  tenant,
  paymentRef,
  setPaymentRef,
  activateNote,
  setActivateNote,
  activating,
  activateError,
}: ActivateTenantModalProps) {
  return (
    <Modal
      isOpen={isOpen && !!tenant}
      onClose={onClose}
      kicker="TENANT GO-LIVE"
      title={tenant ? `Activate Tenant: ${tenant.name}` : "Activate Tenant"}
      titleId="tenant-activate-title"
      description="Verify payment and confirm readiness before activating the tenant."
      size="sm"
    >
      <form onSubmit={onSubmit}>
        {activateError && (
          <div style={{ marginBottom: "1rem" }}>
            <Notice tone="danger">{activateError}</Notice>
          </div>
        )}
        <div style={{ marginBottom: "1rem" }}>
          <TextField
            id="payment-ref-input"
            label="Payment / Transaction Reference"
            value={paymentRef}
            onChange={(e) => setPaymentRef(e.target.value)}
            placeholder="e.g. PAY-2026-X8841"
          />
        </div>
        <div style={{ marginBottom: "1rem" }}>
          <TextField
            id="activate-note-input"
            label="Activation Note"
            value={activateNote}
            onChange={(e) => setActivateNote(e.target.value)}
            placeholder="e.g. Verified by finance desk"
          />
        </div>
        <footer style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={activating}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" busy={activating}>
            Confirm Activation
          </Button>
        </footer>
      </form>
    </Modal>
  );
}
