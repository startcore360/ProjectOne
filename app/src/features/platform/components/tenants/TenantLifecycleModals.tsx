import type { FormEvent } from "react";
import { Button, Kicker, TextField } from "../../../../design-system/components";
import { Icon } from "../../../../components/ui/Icon";
import type { Tenant } from "../common/types";

export interface SuspendTenantModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
  tenant: Tenant | null;
  suspendReason: string;
  setSuspendReason: (val: string) => void;
  suspending: boolean;
}

export function SuspendTenantModal({
  isOpen,
  onClose,
  onSubmit,
  tenant,
  suspendReason,
  setSuspendReason,
  suspending,
}: SuspendTenantModalProps) {
  if (!isOpen || !tenant) return null;

  return (
    <div
      className="platform-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="tenant-suspend-title">
        <header>
          <div>
            <Kicker>TENANT SUSPENSION · Rule R2-24</Kicker>
            <h2 id="tenant-suspend-title">Suspend Tenant: {tenant.name}</h2>
            <p>Suspends institution login access immediately [TENANT_SUSPEND_ARCHIVE, Action #204].</p>
          </div>
          <button className="platform-icon-button" onClick={onClose} aria-label="Close">
            <Icon name="x" size={16} />
          </button>
        </header>
        <form onSubmit={onSubmit}>
          <div style={{ marginBottom: "1rem" }}>
            <TextField
              id="suspend-reason-input"
              label="Reason for suspension *"
              required
              value={suspendReason}
              onChange={(e) => setSuspendReason(e.target.value)}
              placeholder="e.g. Non-payment, regulatory investigation, policy violation"
            />
          </div>
          <footer style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
            <Button type="button" variant="secondary" onClick={onClose} disabled={suspending}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" busy={suspending}>
              Confirm Suspension
            </Button>
          </footer>
        </form>
      </section>
    </div>
  );
}

export interface ArchiveTenantModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
  tenant: Tenant | null;
  archiveReason: string;
  setArchiveReason: (val: string) => void;
  archiving: boolean;
}

export function ArchiveTenantModal({
  isOpen,
  onClose,
  onSubmit,
  tenant,
  archiveReason,
  setArchiveReason,
  archiving,
}: ArchiveTenantModalProps) {
  if (!isOpen || !tenant) return null;

  return (
    <div
      className="platform-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="tenant-archive-title">
        <header>
          <div>
            <Kicker>TENANT ARCHIVAL · Rule R2-24</Kicker>
            <h2 id="tenant-archive-title">Archive Tenant: {tenant.name}</h2>
            <p>Archives tenancy records and prevents all operational logins [TENANT_SUSPEND_ARCHIVE, Action #204].</p>
          </div>
          <button className="platform-icon-button" onClick={onClose} aria-label="Close">
            <Icon name="x" size={16} />
          </button>
        </header>
        <form onSubmit={onSubmit}>
          <div style={{ marginBottom: "1rem" }}>
            <TextField
              id="archive-reason-input"
              label="Reason for archival *"
              required
              value={archiveReason}
              onChange={(e) => setArchiveReason(e.target.value)}
              placeholder="e.g. Contract elapsed, customer offboarding completed"
            />
          </div>
          <footer style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
            <Button type="button" variant="secondary" onClick={onClose} disabled={archiving}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" busy={archiving}>
              Confirm Archival
            </Button>
          </footer>
        </form>
      </section>
    </div>
  );
}
