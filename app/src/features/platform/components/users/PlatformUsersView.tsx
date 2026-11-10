import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  Button,
  ConfirmDialog,
  DataTable,
  Modal,
  Notice,
  Panel,
  SearchBox,
  StatusBadge,
} from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { PLATFORM_ROLE_LABELS, readErrorMessage } from "../common/types";
import { useAppSelector } from "../../../../store/hooks";
import { usePlatformAccess } from "../../platformAccess";
import { platformUserService, roleChoicePayload, type PlatformUserItem } from "../../api/platformUserService";
import { platformRoleService, type PlatformRoleItem } from "../../api/platformRoleService";
import { CreatePlatformUserModal, RoleOptions, type CreatePlatformUserDraft } from "./CreatePlatformUserModal";

const EMPTY_DRAFT: CreatePlatformUserDraft = { fullName: "", email: "", roleChoice: "std:PLATFORM_ADMIN" };

function roleLabel(user: PlatformUserItem) {
  return user.customRoleName ?? PLATFORM_ROLE_LABELS[user.platformRole] ?? user.platformRole;
}

function ChangeRoleModal({
  user,
  customRoles,
  onClose,
  onSaved,
}: {
  user: PlatformUserItem;
  customRoles: PlatformRoleItem[];
  onClose: () => void;
  onSaved: (message: string) => void;
}) {
  const [choice, setChoice] = useState(
    user.platformRoleId ? `custom:${user.platformRoleId}` : `std:${user.platformRole}`,
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const save = async () => {
    setBusy(true);
    setError("");
    try {
      await platformUserService.updatePlatformUser(user.platformUserId, roleChoicePayload(choice));
      onSaved(`Changed ${user.fullName}'s role. It applies right away.`);
    } catch (err) {
      setError(readErrorMessage(err, "Could not change this role."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      isOpen
      onClose={onClose}
      kicker="CHANGE ROLE"
      title={user.fullName}
      description="What they can do changes right away, without signing in again."
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button onClick={() => void save()} busy={busy}>Save role</Button>
        </>
      }
    >
      <label className="platform-form-field">
        Platform role
        <select value={choice} onChange={(e) => setChoice(e.target.value)}>
          <RoleOptions customRoles={customRoles} />
        </select>
      </label>
      {error && <Notice tone="danger">{error}</Notice>}
    </Modal>
  );
}

export function PlatformUsersView() {
  const myId = useAppSelector((state) => state.auth.user?.id);
  const { can } = usePlatformAccess();
  const canManage = can("PLATFORM_USER_MANAGE");

  const [users, setUsers] = useState<PlatformUserItem[]>([]);
  const [customRoles, setCustomRoles] = useState<PlatformRoleItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("All");
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [createdUser, setCreatedUser] = useState<PlatformUserItem | null>(null);
  const [draft, setDraft] = useState<CreatePlatformUserDraft>(EMPTY_DRAFT);
  const [notice, setNotice] = useState("");
  const [actionError, setActionError] = useState("");
  const [changingRole, setChangingRole] = useState<PlatformUserItem | null>(null);
  const [statusTarget, setStatusTarget] = useState<PlatformUserItem | null>(null);
  const [statusBusy, setStatusBusy] = useState(false);

  const loadUsers = useCallback(() => {
    setLoading(true);
    setLoadError("");
    platformUserService
      .getPlatformUsers()
      .then((res) => setUsers(res.data))
      .catch((err) => setLoadError(readErrorMessage(err, "Could not load platform users.")))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    loadUsers();
    if (canManage) {
      Promise.resolve(platformRoleService.getRoles())
        .then((res) => setCustomRoles(Array.isArray(res?.data) ? res.data : []))
        .catch(() => setCustomRoles([]));
    }
  }, [loadUsers, canManage]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setCreating(true);
    setCreateError("");
    try {
      const response = await platformUserService.createPlatformUser({
        fullName: draft.fullName,
        email: draft.email,
        ...roleChoicePayload(draft.roleChoice),
      });
      setCreatedUser(response.data);
      setShowCreate(false);
      setDraft(EMPTY_DRAFT);
      loadUsers();
    } catch (err) {
      setCreateError(readErrorMessage(err, "Failed to create platform user"));
    } finally {
      setCreating(false);
    }
  };

  const applyStatus = async () => {
    if (!statusTarget) return;
    const reactivate = statusTarget.status !== "ACTIVE";
    setStatusBusy(true);
    setActionError("");
    try {
      await platformUserService.updatePlatformUser(statusTarget.platformUserId, {
        status: reactivate ? "ACTIVE" : "INACTIVE",
      });
      setNotice(`${statusTarget.fullName} is ${reactivate ? "active again" : "deactivated and signed out"}.`);
      loadUsers();
    } catch (err) {
      setActionError(readErrorMessage(err, "Could not change this account."));
    } finally {
      setStatusBusy(false);
      setStatusTarget(null);
    }
  };

  const filtered = users.filter(
    (u) =>
      (filter === "All" || (filter === "Active") === (u.status === "ACTIVE")) &&
      (u.fullName + u.email + roleLabel(u)).toLowerCase().includes(query.toLowerCase()),
  );

  return (
    <div className="platform-page">
      <PageHeader
        title="Platform users"
        description="Manage access for people who operate the EDU 360 platform."
        actions={
          <Button
            disabled={!canManage}
            title={canManage ? undefined : "Only the Product Owner can create platform staff accounts (PLATFORM_USER_MANAGE)"}
            onClick={() => {
              setCreateError("");
              setCreatedUser(null);
              setNotice("");
              setShowCreate(true);
            }}
          >
            Invite platform user
          </Button>
        }
      />
      {createdUser && (
        <Notice tone="info">
          Created <strong>{createdUser.fullName}</strong> ({createdUser.email}). Temporary password (shown once):{" "}
          <strong>{createdUser.tempPassword}</strong>. They must change it at first login.
        </Notice>
      )}
      {notice && <Notice tone="success">{notice}</Notice>}
      {actionError && <Notice tone="danger">{actionError}</Notice>}
      {loadError && (
        <Notice tone="danger">
          {loadError}{" "}
          <Button variant="secondary" size="sm" onClick={loadUsers}>Try again</Button>
        </Notice>
      )}
      <Panel
        title="Platform access"
        description="Platform staff accounts only. Institution users are managed within their institution."
      >
        <div className="platform-toolbar">
          <SearchBox value={query} onChange={setQuery} placeholder="Search name, email or role" />
          <label className="platform-select-label">
            Status{" "}
            <select value={filter} onChange={(e) => setFilter(e.target.value)}>
              <option>All</option>
              <option>Active</option>
              <option>Inactive</option>
            </select>
          </label>
        </div>
        {loading ? (
          <div className="platform-empty-inline">
            <Spinner label="Loading platform users" />
          </div>
        ) : (
          <DataTable
            headers={["Name", "Email", "Platform role", "Status", "Last active", "Actions"]}
            empty={!filtered.length}
            emptyMessage={users.length ? "No matching people." : "No platform staff yet."}
          >
            {filtered.map((u) => (
              <tr key={u.platformUserId}>
                <td><strong>{u.fullName}</strong></td>
                <td>{u.email}</td>
                <td>{roleLabel(u)}</td>
                <td><StatusBadge value={u.status === "ACTIVE" ? "Active" : "Inactive"} /></td>
                <td>{u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : "Never logged in"}</td>
                <td>
                  {canManage && u.platformUserId !== myId ? (
                    <div style={{ display: "flex", gap: "var(--space-2)", flexWrap: "wrap" }}>
                      <Button variant="secondary" size="sm" onClick={() => { setNotice(""); setChangingRole(u); }}>
                        Change role
                      </Button>
                      <Button variant="secondary" size="sm" onClick={() => { setNotice(""); setStatusTarget(u); }}>
                        {u.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
                      </Button>
                    </div>
                  ) : (
                    u.platformUserId === myId && <small className="platform-cell-sub">This is you</small>
                  )}
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Panel>

      <CreatePlatformUserModal
        isOpen={showCreate}
        onClose={() => setShowCreate(false)}
        onSubmit={submit}
        draft={draft}
        setDraft={setDraft}
        customRoles={customRoles}
        creating={creating}
        createError={createError}
      />
      {changingRole && (
        <ChangeRoleModal
          user={changingRole}
          customRoles={customRoles}
          onClose={() => setChangingRole(null)}
          onSaved={(message) => {
            setChangingRole(null);
            setNotice(message);
            loadUsers();
          }}
        />
      )}
      <ConfirmDialog
        isOpen={!!statusTarget}
        onClose={() => setStatusTarget(null)}
        onConfirm={() => void applyStatus()}
        title={statusTarget?.status === "ACTIVE" ? `Deactivate ${statusTarget?.fullName}?` : `Reactivate ${statusTarget?.fullName}?`}
        message={
          statusTarget?.status === "ACTIVE"
            ? "They're signed out now and can't sign in again until you reactivate them."
            : "They can sign in again with their existing role."
        }
        confirmLabel={statusTarget?.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
        confirmTone={statusTarget?.status === "ACTIVE" ? "danger" : "primary"}
        busy={statusBusy}
      />
    </div>
  );
}

// Export with page name alias
export const PlatformUsersPage = PlatformUsersView;
