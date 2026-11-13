import { useState } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";
import { PLATFORM_ROLE_LABELS, readErrorMessage } from "../common/types";
import type { PlatformRole } from "../../api/platformUserService";
import {
  CUSTOMISABLE_ROLES,
  baseLimitReason,
  platformRoleService,
  type AccessMode,
  type ActionCatalogItem,
  type PlatformRoleItem,
  type RoleTemplateItem,
} from "../../api/platformRoleService";
import { PermissionPicker } from "./PermissionPicker";

export interface RoleEditorModalProps {
  role: PlatformRoleItem | null; // null creates a new role
  catalog: ActionCatalogItem[];
  templates: RoleTemplateItem[];
  onClose: () => void;
  onSaved: (message: string) => void;
}

function toSelected(role: PlatformRoleItem | null): Record<string, AccessMode> {
  return Object.fromEntries((role?.actions ?? []).map((a) => [a.actionCode, a.accessMode]));
}

export function RoleEditorModal({ role, catalog, templates, onClose, onSaved }: RoleEditorModalProps) {
  const [name, setName] = useState(role?.roleName ?? "");
  const [base, setBase] = useState<PlatformRole>(role?.basePlatformRole ?? "PLATFORM_ADMIN");
  const [templateId, setTemplateId] = useState("");
  const [selected, setSelected] = useState<Record<string, AccessMode>>(toSelected(role));
  const [updateTemplate, setUpdateTemplate] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const usableTemplates = templates.filter((t) => !t.locked && t.status === "ACTIVE");
  const count = Object.keys(selected).length;

  const chooseTemplate = (id: string) => {
    setTemplateId(id);
    setUpdateTemplate(false);
    const template = usableTemplates.find((t) => t.platformRoleTemplateId === id);
    if (!template) return;
    setBase(template.basePlatformRole);
    setSelected(Object.fromEntries(template.actionCodes.map((code) => [code, "SHARED" as AccessMode])));
  };

  // Changing the base can make a ticked permission forbidden, so those come off straight away.
  const chooseBase = (next: PlatformRole) => {
    setBase(next);
    setSelected(Object.fromEntries(Object.entries(selected).filter(([code]) => !baseLimitReason(code, next))));
  };

  const save = async (activate: boolean) => {
    if (name.trim().length < 2) {
      setError("Enter a role name of at least 2 characters.");
      return;
    }
    if (activate && count === 0) {
      setError("Choose at least one permission before activating this role.");
      return;
    }
    setBusy(true);
    setError("");
    const actions = Object.entries(selected).map(([actionCode, accessMode]) => ({ actionCode, accessMode }));
    try {
      if (role) {
        await platformRoleService.updateRole(role.platformRoleId, { roleName: name.trim(), actions });
        onSaved(`Saved ${name.trim()}.`);
      } else {
        const created = await platformRoleService.createRole({
          roleName: name.trim(),
          basePlatformRole: base,
          sourceTemplateId: templateId || null,
          actions,
          updateTemplate,
        });
        if (activate) await platformRoleService.activateRole(created.data.platformRoleId);
        onSaved(
          activate
            ? `${name.trim()} is active. Assign it to someone on the Platform users page.`
            : `${name.trim()} is saved as a draft. Activate it before assigning it.`,
        );
      }
    } catch (err) {
      setError(readErrorMessage(err, "Could not save this role."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      isOpen
      onClose={onClose}
      size="lg"
      kicker={role ? "EDIT ROLE" : "NEW ROLE"}
      title={role ? `Edit ${role.roleName}` : "Create platform role"}
      description={
        role
          ? "Changes take effect at once for everyone holding this role."
          : "Start from a template and change anything you like. Edits here apply only to this role."
      }
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          {role ? (
            <Button onClick={() => save(false)} busy={busy}>
              Save changes
            </Button>
          ) : (
            <>
              <Button variant="secondary" onClick={() => save(false)} busy={busy}>
                Save draft
              </Button>
              <Button onClick={() => save(true)} busy={busy}>
                Save and activate
              </Button>
            </>
          )}
        </>
      }
    >
      {error && <Notice tone="danger">{error}</Notice>}
      <TextField
        id="platform-role-name"
        label="Role name"
        required
        maxLength={100}
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="e.g. Billing Assistant"
      />
      {!role && (
        <div className="platform-form-grid">
          <label className="platform-form-field">
            Start from template
            <select value={templateId} onChange={(e) => chooseTemplate(e.target.value)}>
              <option value="">Blank</option>
              {usableTemplates.map((t) => (
                <option key={t.platformRoleTemplateId} value={t.platformRoleTemplateId}>
                  {t.templateName}
                </option>
              ))}
            </select>
          </label>
          <label className="platform-form-field">
            Based on
            <select value={base} onChange={(e) => chooseBase(e.target.value as PlatformRole)}>
              {CUSTOMISABLE_ROLES.map((value) => (
                <option key={value} value={value}>
                  {PLATFORM_ROLE_LABELS[value]}
                </option>
              ))}
            </select>
          </label>
        </div>
      )}
      <PermissionPicker catalog={catalog} base={base} selected={selected} onChange={setSelected} />
      <p className="platform-field-hint" aria-live="polite">
        {count} {count === 1 ? "permission" : "permissions"} chosen. "Only this person" means you step in with a reason
        to do that work yourself. You can take any permission back at any time.
      </p>
      {!role && (
        <label className="platform-form-field" style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
          <input
            type="checkbox"
            checked={updateTemplate}
            disabled={!templateId}
            onChange={(e) => setUpdateTemplate(e.target.checked)}
          />
          Also update this template for future roles (existing roles won't change)
        </label>
      )}
    </Modal>
  );
}
