import type { FormEvent } from "react";
import { Button, Modal, TextField } from "../../../../design-system/components";
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
  return (
    <Modal
      isOpen={isOpen && !!tenant}
      onClose={onClose}
      kicker="TENANT SUSPENSION"
      title={tenant ? `Suspend Tenant: ${tenant.name}` : "Suspend Tenant"}
      titleId="tenant-suspend-title"
      description="Suspends login access for the tenant's institutions immediately."
      size="sm"
    >
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
    </Modal>
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
  return (
    <Modal
      isOpen={isOpen && !!tenant}
      onClose={onClose}
      kicker="TENANT ARCHIVAL"
      title={tenant ? `Archive Tenant: ${tenant.name}` : "Archive Tenant"}
      titleId="tenant-archive-title"
      description="Archives tenant records and prevents operational logins."
      size="sm"
    >
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
    </Modal>
  );
}
