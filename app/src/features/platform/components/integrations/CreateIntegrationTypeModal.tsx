import type { FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";

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
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="NEW INTEGRATION TYPE"
      title="Add integration type"
      titleId="integration-create-title"
      description="Adds a new allowed category to the platform catalogue."
      size="sm"
    >
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
    </Modal>
  );
}
