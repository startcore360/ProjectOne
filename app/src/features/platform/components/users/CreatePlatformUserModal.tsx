import type { FormEvent } from "react";
import { Button, Kicker, TextField, Notice } from "../../../../design-system/components";
import { Icon } from "../../../../components/ui/Icon";
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
  if (!isOpen) return null;

  return (
    <div
      className="platform-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="platform-user-create-title">
        <header>
          <div>
            <Kicker>NEW PLATFORM USER</Kicker>
            <h2 id="platform-user-create-title">Invite platform user</h2>
            <p>Creates a two-step-login account in the platform realm. The temporary password is shown once after creation.</p>
          </div>
          <button className="platform-icon-button" onClick={onClose} aria-label="Close">
            <Icon name="x-circle" size={18} />
          </button>
        </header>
        <form onSubmit={onSubmit}>
          <TextField
            id="platform-user-name"
            label="Full name"
            required
            value={draft.fullName}
            onChange={(e) => setDraft({ ...draft, fullName: e.target.value })}
            placeholder="e.g. Priya Nair"
          />
          <TextField
            id="platform-user-email"
            label="Email"
            type="email"
            required
            value={draft.email}
            onChange={(e) => setDraft({ ...draft, email: e.target.value })}
            placeholder="priya.nair@edu360.com"
          />
          <label className="platform-form-field">
            Platform role
            <select
              value={draft.roleChoice}
              onChange={(e) => setDraft({ ...draft, roleChoice: e.target.value })}
            >
              <RoleOptions customRoles={customRoles} />
            </select>
          </label>
          {createError && <Notice tone="danger">{createError}</Notice>}
          <footer>
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" busy={creating}>
              Create platform user
            </Button>
          </footer>
        </form>
      </section>
    </div>
  );
}
