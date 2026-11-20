import type { FormEvent } from "react";
import { Button, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";

export type IntegrationTypeStatusValue = "ALLOWED" | "DISALLOWED";

export interface IntegrationTypeItem {
  integrationTypeId: string;
  integrationTypeCode: string;
  description: string;
  status: IntegrationTypeStatusValue;
  allowedProviders: string[];
  createdAt: string;
  updatedAt: string;
  rowVersion: number;
}

export interface EditIntegrationTypeDraft {
  description: string;
  status: IntegrationTypeStatusValue;
  allowedProviders: string;
}

export interface EditIntegrationTypeModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
  editing: IntegrationTypeItem | null;
  editDraft: EditIntegrationTypeDraft;
  setEditDraft: React.Dispatch<React.SetStateAction<EditIntegrationTypeDraft>>;
  saving: boolean;
  editError: string;
}

export function EditIntegrationTypeModal({
  isOpen,
  onClose,
  onSubmit,
  editing,
  editDraft,
  setEditDraft,
  saving,
  editError,
}: EditIntegrationTypeModalProps) {
  return (
    <Modal
      isOpen={isOpen && !!editing}
      onClose={onClose}
      kicker="EDIT"
      title={editing ? `Edit ${editing.integrationTypeCode}` : "Edit integration type"}
      titleId="integration-edit-title"
      description="Updates apply immediately to the platform catalogue."
      size="sm"
    >
      <form onSubmit={onSubmit}>
        <TextField
          id="integration-edit-description"
          label="Description"
          required
          value={editDraft.description}
          onChange={(e) => setEditDraft({ ...editDraft, description: e.target.value })}
        />
        <div className="app-form-grid">
          <SelectField
            id="integration-edit-status"
            label="Status"
            value={editDraft.status}
            onChange={(e) =>
              setEditDraft({ ...editDraft, status: e.target.value as IntegrationTypeStatusValue })
            }
            options={[
              { value: "ALLOWED", label: "Allowed" },
              { value: "DISALLOWED", label: "Disallowed" },
            ]}
          />
        </div>
        <TextField
          id="integration-edit-providers"
          label="Allowed providers (comma-separated)"
          value={editDraft.allowedProviders}
          onChange={(e) => setEditDraft({ ...editDraft, allowedProviders: e.target.value })}
        />
        {editError && <Notice tone="danger">{editError}</Notice>}
        <footer>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" busy={saving}>
            Save changes
          </Button>
        </footer>
      </form>
    </Modal>
  );
}
