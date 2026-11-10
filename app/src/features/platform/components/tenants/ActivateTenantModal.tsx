import type { FormEvent } from "react";
import { Button, Kicker, TextField } from "../../../../design-system/components";
import { Icon } from "../../../../components/ui/Icon";
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
  if (!isOpen || !tenant) return null;

  return (
    <div
      className="platform-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="tenant-activate-title">
        <header>
          <div>
            <Kicker>TENANT GO-LIVE</Kicker>
            <h2 id="tenant-activate-title">Activate Tenant: {tenant.name}</h2>
            <p>Verify payment and confirm statutory go-live readiness [TENANT_ACTIVATE, Action #203].</p>
          </div>
          <button className="platform-icon-button" onClick={onClose} aria-label="Close">
            <Icon name="x-circle" size={18} />
          </button>
        </header>
        <form onSubmit={onSubmit}>
          {activateError && (
            <div className="platform-inline-error" style={{ marginBottom: "1rem" }}>
              {activateError}
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
      </section>
    </div>
  );
}
