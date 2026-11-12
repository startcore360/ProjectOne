import type { FormEvent } from "react";
import { Button, Kicker, TextField, Notice } from "../../../../design-system/components";
import { Icon } from "../../../../components/ui/Icon";

export interface CreateIntegrationTypeDraft {
  integrationTypeCode: string;
  description: string;
  allowedProviders: string;
}

export interface CreateIntegrationTypeModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
  createDraft: CreateIntegrationTypeDraft;
  setCreateDraft: React.Dispatch<React.SetStateAction<CreateIntegrationTypeDraft>>;
  creating: boolean;
  createError: string;
}

export function CreateIntegrationTypeModal({
  isOpen,
  onClose,
  onSubmit,
  createDraft,
  setCreateDraft,
  creating,
  createError,
}: CreateIntegrationTypeModalProps) {
  if (!isOpen) return null;

  return (
    <div
      className="platform-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="integration-create-title">
        <header>
          <div>
            <Kicker>NEW INTEGRATION TYPE</Kicker>
            <h2 id="integration-create-title">Add integration type</h2>
            <p>Adds a new allowed category to the platform catalogue.</p>
          </div>
          <button className="platform-icon-button" onClick={onClose} aria-label="Close">
            <Icon name="x" size={16} />
          </button>
        </header>
        <form onSubmit={onSubmit}>
          <TextField
            id="integration-code"
            label="Integration type code"
            required
            value={createDraft.integrationTypeCode}
            onChange={(e) => setCreateDraft({ ...createDraft, integrationTypeCode: e.target.value })}
            placeholder="e.g. SMS_DLT"
          />
          <TextField
            id="integration-description"
            label="Description"
            required
            value={createDraft.description}
            onChange={(e) => setCreateDraft({ ...createDraft, description: e.target.value })}
            placeholder="What this integration type covers"
          />
          <TextField
            id="integration-providers"
            label="Allowed providers (comma-separated)"
            value={createDraft.allowedProviders}
            onChange={(e) => setCreateDraft({ ...createDraft, allowedProviders: e.target.value })}
            placeholder="e.g. TWILIO, MSG91"
          />
          {createError && <Notice tone="danger">{createError}</Notice>}
          <footer>
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" busy={creating}>
              Create integration type
            </Button>
          </footer>
        </form>
      </section>
    </div>
  );
}
