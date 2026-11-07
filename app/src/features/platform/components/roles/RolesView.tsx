import { useCallback, useEffect, useState } from "react";
import {
  Button,
  ConfirmDialog,
  DataTable,
  EmptyState,
  ErrorState,
  Notice,
  Panel,
  StatusBadge,
} from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { useAppDispatch, useAppSelector } from "../../../../store/hooks";
import { PLATFORM_ROLE_LABELS, formatTimestamp, readErrorMessage } from "../common/types";
import { loadPlatformPermissions, usePlatformAccess } from "../../platformAccess";
import {
  ROLE_STATUS_LABELS,
  platformRoleService,
  type ActionCatalogItem,
  type PlatformRoleItem,
  type RoleTemplateItem,
  type StepInItem,
} from "../../api/platformRoleService";
import { RoleEditorModal } from "./RoleEditorModal";
import { StepInModal } from "./StepInModal";

type PendingConfirm = { title: string; message: string; label: string; run: () => Promise<unknown> };
const SPLIT_ACTIONS = { display: "flex", gap: "var(--space-2)", flexWrap: "wrap" } as const;

export function RolesView() {
  const dispatch = useAppDispatch();
  const userId = useAppSelector((state) => state.auth.user?.id);
  const { steppedIn } = usePlatformAccess();

  const [roles, setRoles] = useState<PlatformRoleItem[]>([]);
  const [templates, setTemplates] = useState<RoleTemplateItem[]>([]);
  const [catalog, setCatalog] = useState<ActionCatalogItem[]>([]);
  const [stepIns, setStepIns] = useState<StepInItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [notice, setNotice] = useState("");
  const [actionError, setActionError] = useState("");
  const [editing, setEditing] = useState<PlatformRoleItem | "new" | null>(null);
  const [stepInAction, setStepInAction] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<PendingConfirm | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const [r, t, c, s] = await Promise.all([
        platformRoleService.getRoles(),
        platformRoleService.getTemplates(),
        platformRoleService.getActionCatalog(),
        platformRoleService.getStepIns(),
      ]);
      setRoles(r.data);
      setTemplates(t.data);
      setCatalog(c.data);
      setStepIns(s.data);
    } catch (err) {
      setLoadError(readErrorMessage(err, "Could not load platform roles."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const refreshAfterChange = async (message: string) => {
    setNotice(message);
    setActionError("");
    await load();
    if (userId) await loadPlatformPermissions(dispatch, userId);
  };

  const runConfirm = async () => {
    if (!confirm) return;
    setBusy(true);
    try {
      await confirm.run();
      const done = confirm.label;
      setConfirm(null);
      await refreshAfterChange(`${done} complete.`);
    } catch (err) {
      setConfirm(null);
      setActionError(readErrorMessage(err, "That change could not be made."));
    } finally {
      setBusy(false);
    }
  };

  const label = (actionCode: string) =>
    catalog.find((item) => item.actionCode === actionCode)?.description ?? actionCode;

  const handedOver = roles
    .filter((role) => role.status === "ACTIVE")
    .flatMap((role) =>
      role.actions.filter((a) => a.accessMode === "HANDED_OVER").map((a) => ({ role, actionCode: a.actionCode })),
    );

  return (
    <div className="platform-page">
      <PageHeader
        title="Platform roles"
        description="Build roles from templates, choose what each one can do, and take access back at any time."
        actions={
          <Button onClick={() => { setNotice(""); setEditing("new"); }} disabled={loading || !!loadError}>
            Create role
          </Button>
        }
      />
      {notice && <Notice tone="success">{notice}</Notice>}
      {actionError && <Notice tone="danger">{actionError}</Notice>}

      {loading ? (
        <div className="platform-empty-inline">
          <Spinner label="Loading platform roles" />
        </div>
      ) : loadError ? (
        <ErrorState
          title="Platform roles didn't load"
          message={loadError}
          action={<Button variant="secondary" onClick={() => void load()}>Try again</Button>}
        />
      ) : (
        <>
          <Panel title="Roles" description="The six standard roles stay available when you add a person. These are your own.">
            {roles.length === 0 ? (
              <EmptyState
                icon="shield"
                title="No custom roles yet"
                description="Create one from a template when you're ready to share work."
              />
            ) : (
              <DataTable headers={["Role", "Based on", "Status", "Permissions", "People", "Actions"]}>
                {roles.map((role) => (
                  <tr key={role.platformRoleId}>
                    <td><strong>{role.roleName}</strong></td>
                    <td>{PLATFORM_ROLE_LABELS[role.basePlatformRole]}</td>
                    <td><StatusBadge value={ROLE_STATUS_LABELS[role.status]} /></td>
                    <td>{role.actions.length}</td>
                    <td>{role.holders}</td>
                    <td>
                      {role.status !== "ARCHIVED" && (
                        <div style={SPLIT_ACTIONS}>
                          <Button variant="secondary" size="sm" onClick={() => { setNotice(""); setEditing(role); }}>
                            Edit
                          </Button>
                          {role.status === "ACTIVE" ? (
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() =>
                                setConfirm({
                                  title: `Deactivate ${role.roleName}?`,
                                  message: `${role.holders} ${role.holders === 1 ? "person loses" : "people lose"} these permissions right away.`,
                                  label: "Deactivate",
                                  run: () => platformRoleService.deactivateRole(role.platformRoleId),
                                })
                              }
                            >
                              Deactivate
                            </Button>
                          ) : (
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() =>
                                setConfirm({
                                  title: `Activate ${role.roleName}?`,
                                  message: "It can then be assigned, and anyone already holding it gets these permissions.",
                                  label: "Activate",
                                  run: () => platformRoleService.activateRole(role.platformRoleId),
                                })
                              }
                            >
                              Activate
                            </Button>
                          )}
                          {role.status !== "ACTIVE" && (
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() =>
                                setConfirm({
                                  title: `Archive ${role.roleName}?`,
                                  message: "It's kept for the record but can't be used or changed again.",
                                  label: "Archive",
                                  run: () => platformRoleService.archiveRole(role.platformRoleId),
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
            )}
          </Panel>

          <Panel
            title="Handed over"
            description='Work you marked "Only this person". If nobody active holds the role, you can do it without stepping in.'
          >
            {handedOver.length === 0 ? (
              <EmptyState icon="users" title="Nothing handed over" description="All work is shared with you." />
            ) : (
              <DataTable headers={["Permission", "Role", "People", "Actions"]}>
                {handedOver.map(({ role, actionCode }) => (
                  <tr key={`${role.platformRoleId}-${actionCode}`}>
                    <td><strong>{label(actionCode)}</strong></td>
                    <td>{role.roleName}</td>
                    <td>{role.holders}</td>
                    <td>
                      <div style={SPLIT_ACTIONS}>
                        {steppedIn.includes(actionCode) ? (
                          <StatusBadge value="Stepped in" />
                        ) : (
                          <Button
                            variant="secondary"
                            size="sm"
                            disabled={role.holders === 0}
                            title={role.holders === 0 ? "Nobody holds this role, so you can act directly." : undefined}
                            onClick={() => { setNotice(""); setStepInAction(actionCode); }}
                          >
                            Step in
                          </Button>
                        )}
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() =>
                            setConfirm({
                              title: `Take back "${label(actionCode)}"?`,
                              message: `${role.roleName} loses this permission right away.`,
                              label: "Take back",
                              run: () => platformRoleService.takeBack(role.platformRoleId, actionCode),
                            })
                          }
                        >
                          Take back
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </DataTable>
            )}
          </Panel>

          <Panel title="Step-in history" description="The latest 50. These records can't be changed.">
            <DataTable
              headers={["Permission", "Reason", "Started", "Ended"]}
              empty={stepIns.length === 0}
              emptyMessage="Nobody has stepped in yet."
            >
              {stepIns.map((s) => (
                <tr key={s.platformStepInId}>
                  <td><strong>{label(s.actionCode)}</strong></td>
                  <td>{s.reason}</td>
                  <td>{formatTimestamp(s.startedAt)}</td>
                  <td>{formatTimestamp(s.expiresAt)}</td>
                </tr>
              ))}
            </DataTable>
          </Panel>
        </>
      )}

      {editing && (
        <RoleEditorModal
          role={editing === "new" ? null : editing}
          catalog={catalog}
          templates={templates}
          onClose={() => setEditing(null)}
          onSaved={(message) => {
            setEditing(null);
            void refreshAfterChange(message);
          }}
        />
      )}
      {stepInAction && (
        <StepInModal
          actionCode={stepInAction}
          actionLabel={label(stepInAction)}
          onClose={() => setStepInAction(null)}
          onDone={(message) => {
            setStepInAction(null);
            void refreshAfterChange(message);
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

export const PlatformRolesPage = RolesView;
