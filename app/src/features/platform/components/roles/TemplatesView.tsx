import { useCallback, useEffect, useState } from "react";
import {
  Button,
  ConfirmDialog,
  DataTable,
  ErrorState,
  Modal,
  Notice,
  Panel,
  StatusBadge,
  TextField,
} from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { PLATFORM_ROLE_LABELS, readErrorMessage } from "../common/types";
import type { PlatformRole } from "../../api/platformUserService";
import {
  CUSTOMISABLE_ROLES,
  baseLimitReason,
  platformRoleService,
  type AccessMode,
  type ActionCatalogItem,
  type RoleTemplateItem,
} from "../../api/platformRoleService";
import { PermissionPicker } from "./PermissionPicker";

type PendingConfirm = { title: string; message: string; label: string; run: () => Promise<unknown> };

function TemplateEditorModal({
  template,
  catalog,
  onClose,
  onSaved,
}: {
  template: RoleTemplateItem | null;
  catalog: ActionCatalogItem[];
  onClose: () => void;
  onSaved: (message: string) => void;
}) {
  const readOnly = !!template?.locked;
  const [name, setName] = useState(template?.templateName ?? "");
  const [description, setDescription] = useState(template?.description ?? "");
  const [base, setBase] = useState<PlatformRole>(template?.basePlatformRole ?? "PLATFORM_ADMIN");
  const [selected, setSelected] = useState<Record<string, AccessMode>>(
    Object.fromEntries((template?.actionCodes ?? []).map((code) => [code, "SHARED" as AccessMode])),
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const chooseBase = (next: PlatformRole) => {
    setBase(next);
    setSelected(Object.fromEntries(Object.entries(selected).filter(([code]) => !baseLimitReason(code, next))));
  };

  const save = async () => {
    if (name.trim().length < 2) {
      setError("Enter a template name of at least 2 characters.");
      return;
    }
    setBusy(true);
    setError("");
    const payload = {
      templateName: name.trim(),
      description: description.trim() || null,
      basePlatformRole: base,
      actionCodes: Object.keys(selected),
    };
    try {
      if (template) await platformRoleService.updateTemplate(template.platformRoleTemplateId, payload);
      else await platformRoleService.createTemplate(payload);
      onSaved(`Saved ${name.trim()}. Roles you create from now on start from this.`);
    } catch (err) {
      setError(readErrorMessage(err, "Could not save this template."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      isOpen
      onClose={onClose}
      size="lg"
      kicker={readOnly ? "LOCKED TEMPLATE" : template ? "EDIT TEMPLATE" : "NEW TEMPLATE"}
      title={readOnly ? name : template ? `Edit ${template.templateName}` : "Create role template"}
      description={
        readOnly
          ? "Data protection approvals stay with a named person, so this template can't be changed. Assign the standard role instead."
          : "A template is only a starting suggestion. Changing it never changes a role that already exists."
      }
      footer={
        readOnly ? (
          <Button variant="secondary" onClick={onClose}>Close</Button>
        ) : (
          <>
            <Button variant="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
            <Button onClick={() => void save()} busy={busy}>Save template</Button>
          </>
        )
      }
    >
      {!readOnly && (
        <>
          <TextField
            id="platform-template-name"
            label="Template name"
            required
            maxLength={100}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <TextField
            id="platform-template-description"
            label="Description"
            maxLength={500}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
          <label className="platform-form-field">
            Based on
            <select value={base} onChange={(e) => chooseBase(e.target.value as PlatformRole)}>
              {CUSTOMISABLE_ROLES.map((value) => (
                <option key={value} value={value}>{PLATFORM_ROLE_LABELS[value]}</option>
              ))}
            </select>
          </label>
        </>
      )}
      <PermissionPicker
        catalog={catalog}
        base={base}
        selected={selected}
        onChange={setSelected}
        showAccessMode={false}
        readOnly={readOnly}
      />
      {error && <Notice tone="danger">{error}</Notice>}
    </Modal>
  );
}

export function TemplatesView() {
  const [templates, setTemplates] = useState<RoleTemplateItem[]>([]);
  const [catalog, setCatalog] = useState<ActionCatalogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [notice, setNotice] = useState("");
  const [actionError, setActionError] = useState("");
  const [editing, setEditing] = useState<RoleTemplateItem | "new" | null>(null);
  const [confirm, setConfirm] = useState<PendingConfirm | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const [t, c] = await Promise.all([platformRoleService.getTemplates(), platformRoleService.getActionCatalog()]);
      setTemplates(t.data);
      setCatalog(c.data);
    } catch (err) {
      setLoadError(readErrorMessage(err, "Could not load role templates."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const runConfirm = async () => {
    if (!confirm) return;
    setBusy(true);
    setActionError("");
    try {
      await confirm.run();
      setNotice(`${confirm.label} complete. Existing roles haven't changed.`);
      setConfirm(null);
      await load();
    } catch (err) {
      setConfirm(null);
      setActionError(readErrorMessage(err, "That change could not be made."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="Role templates"
        description="Starting suggestions for new roles. Change them freely: existing roles never change."
        actions={
          <Button onClick={() => { setNotice(""); setEditing("new"); }} disabled={loading || !!loadError}>
            Create template
          </Button>
        }
      />
      {notice && <Notice tone="success">{notice}</Notice>}
      {actionError && <Notice tone="danger">{actionError}</Notice>}

      {loading ? (
        <div className="platform-empty-inline">
          <Spinner label="Loading role templates" />
        </div>
      ) : loadError ? (
        <ErrorState
          title="Role templates didn't load"
          message={loadError}
          action={<Button variant="secondary" onClick={() => void load()}>Try again</Button>}
        />
      ) : (
        <Panel title="Templates">
          <DataTable
            headers={["Template", "Based on", "Type", "Status", "Permissions", "Actions"]}
            empty={templates.length === 0}
            emptyMessage="No templates yet."
          >
            {templates.map((t) => (
              <tr key={t.platformRoleTemplateId}>
                <td>
                  <strong>{t.templateName}</strong>
                  {t.description && <small className="platform-cell-sub">{t.description}</small>}
                </td>
                <td>{PLATFORM_ROLE_LABELS[t.basePlatformRole]}</td>
                <td>{t.locked ? "Locked" : t.standard ? "Standard" : "Custom"}</td>
                <td><StatusBadge value={t.status === "ACTIVE" ? "Active" : "Archived"} /></td>
                <td>{t.actionCodes.length}</td>
                <td>
                  {t.status === "ACTIVE" && (
                    <div style={{ display: "flex", gap: "var(--space-2)", flexWrap: "wrap" }}>
                      <Button variant="secondary" size="sm" onClick={() => { setNotice(""); setEditing(t); }}>
                        {t.locked ? "View" : "Edit"}
                      </Button>
                      {t.standard && !t.locked && (
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() =>
                            setConfirm({
                              title: `Reset ${t.templateName}?`,
                              message: "This puts back the standard permissions. Existing roles don't change.",
                              label: "Reset",
                              run: () => platformRoleService.resetTemplate(t.platformRoleTemplateId),
                            })
                          }
                        >
                          Reset
                        </Button>
                      )}
                      {!t.locked && (
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() =>
                            setConfirm({
                              title: `Archive ${t.templateName}?`,
                              message: "It won't be offered for new roles. Existing roles don't change.",
                              label: "Archive",
                              run: () => platformRoleService.archiveTemplate(t.platformRoleTemplateId),
                            })
                          }
                        >
                          Archive
                        </Button>
                      )}
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </DataTable>
        </Panel>
      )}

      {editing && (
        <TemplateEditorModal
          template={editing === "new" ? null : editing}
          catalog={catalog}
          onClose={() => setEditing(null)}
          onSaved={(message) => {
            setEditing(null);
            setNotice(message);
            void load();
          }}
        />
      )}
      <ConfirmDialog
        isOpen={!!confirm}
        onClose={() => setConfirm(null)}
        onConfirm={() => void runConfirm()}
        title={confirm?.title ?? ""}
        message={confirm?.message ?? ""}
        confirmLabel={confirm?.label}
        busy={busy}
      />
    </div>
  );
}

export const PlatformRoleTemplatesPage = TemplatesView;
