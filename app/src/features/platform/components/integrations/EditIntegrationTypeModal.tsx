import type { FormEvent } from "react";
import { Button, Kicker, TextField, Notice } from "../../../../design-system/components";
import { Icon } from "../../../../components/ui/Icon";

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
  if (!isOpen || !editing) return null;

  return (
    <div
      className="platform-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="integration-edit-title">
        <header>
          <div>
            <Kicker>EDIT</Kicker>
            <h2 id="integration-edit-title">Edit {editing.integrationTypeCode}</h2>
            <p>Updates apply immediately to the platform catalogue.</p>
          </div>
          <button className="platform-icon-button" onClick={onClose} aria-label="Close">
            <Icon name="x" size={16} />
          </button>
        </header>
        <form onSubmit={onSubmit}>
          <TextField
            id="integration-edit-description"
            label="Description"
            required
            value={editDraft.description}
            onChange={(e) => setEditDraft({ ...editDraft, description: e.target.value })}
          />
          <div className="platform-form-grid">
            <label className="platform-form-field">
              Status
              <select
                value={editDraft.status}
                onChange={(e) =>
                  setEditDraft({ ...editDraft, status: e.target.value as IntegrationTypeStatusValue })
                }
              >
                <option value="ALLOWED">Allowed</option>
                <option value="DISALLOWED">Disallowed</option>
              </select>
            </label>
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
      </section>
    </div>
  );
}
