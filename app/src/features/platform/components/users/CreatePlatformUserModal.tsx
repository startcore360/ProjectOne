import type { FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";
import { PLATFORM_ROLE_LABELS } from "../common/types";
import type { PlatformRoleItem } from "../../api/platformRoleService";

export interface CreatePlatformUserDraft {
  fullName: string;
  email: string;
  roleChoice: string; // "std:<role>" or "custom:<id>"
}

export function RoleOptions({ customRoles }: { customRoles: PlatformRoleItem[] }) {
  const active = customRoles.filter((role) => role.status === "ACTIVE");
  return (
    <>
      <optgroup label="Standard roles">
        {Object.entries(PLATFORM_ROLE_LABELS).map(([value, label]) => (
          <option key={value} value={`std:${value}`}>
            {label}
          </option>
        ))}
      </optgroup>
      {active.length > 0 && (
        <optgroup label="Your roles">
          {active.map((role) => (
            <option key={role.platformRoleId} value={`custom:${role.platformRoleId}`}>
              {role.roleName}
            </option>
          ))}
        </optgroup>
      )}
    </>
  );
}

export interface CreatePlatformUserModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
  draft: CreatePlatformUserDraft;
  setDraft: React.Dispatch<React.SetStateAction<CreatePlatformUserDraft>>;
  customRoles: PlatformRoleItem[];
  creating: boolean;
  createError: string;
}

export function CreatePlatformUserModal({
  isOpen,
  onClose,
  onSubmit,
  draft,
  setDraft,
  customRoles,
  creating,
  createError,
}: CreatePlatformUserModalProps) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="NEW PLATFORM USER"
      title="Invite platform user"
      titleId="app-user-create-title"
      description="Creates a two-step-login account in the platform realm. The temporary password is shown once after creation."
      size="sm"
    >
      <form onSubmit={onSubmit}>
        {createError && <Notice popup tone="danger">{createError}</Notice>}
        <TextField
          id="app-user-name"
          label="Full name"
          required
          value={draft.fullName}
          onChange={(e) => setDraft({ ...draft, fullName: e.target.value })}
          placeholder="e.g. Priya Nair"
        />
        <TextField
          id="app-user-email"
          label="Email"
          type="email"
          required
          value={draft.email}
          onChange={(e) => setDraft({ ...draft, email: e.target.value })}
          placeholder="priya.nair@edu360.com"
        />
        {/* Raw <select> kept because optgroup is not supported by DS SelectField */}
        <label className="app-form-field">
          Platform role
          <select
            value={draft.roleChoice}
            onChange={(e) => setDraft({ ...draft, roleChoice: e.target.value })}
          >
            <RoleOptions customRoles={customRoles} />
          </select>
        </label>

        <footer>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" busy={creating}>
            Create platform user
          </Button>
        </footer>
      </form>
    </Modal>
  );
}
