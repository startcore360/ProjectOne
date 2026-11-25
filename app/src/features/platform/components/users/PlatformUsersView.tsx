import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  Button,
  ConfirmDialog,
  DataTable,
  ErrorState,
  Modal,
  Notice,
  Panel,
  SearchBox,
  StatusBadge,
  toast,
} from "../../../../design-system/components";
import { CreatedCredentialsModal } from "../../../user-management/components/users/CreatedCredentialsModal";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { PLATFORM_ROLE_LABELS, readErrorMessage } from "../common/types";
import { useAppSelector } from "../../../../store/hooks";
import { usePlatformAccess } from "../../platformAccess";
import { platformUserService, roleChoicePayload, type PlatformUserItem } from "../../api/platformUserService";
import { platformRoleService, type PlatformRoleItem } from "../../api/platformRoleService";
import { CreatePlatformUserModal, RoleOptions, type CreatePlatformUserDraft } from "./CreatePlatformUserModal";
import { MfaResetDialog } from "../../../auth/components/MfaResetDialog";

const EMPTY_DRAFT: CreatePlatformUserDraft = { fullName: "", email: "", roleChoice: "std:PLATFORM_ADMIN" };

function roleLabel(user: PlatformUserItem) {
  return user.customRoleName ?? PLATFORM_ROLE_LABELS[user.platformRole] ?? user.platformRole;
}

function formatLastLogin(value?: string | null, now = new Date()) {
  if (!value) return "Never logged in";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Never logged in";

  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const loginDay = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const daysAgo = Math.round((today.getTime() - loginDay.getTime()) / 86_400_000);
  if (daysAgo === 0) return "Today";
  if (daysAgo === 1) return "Yesterday";

  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(date);
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
      <label className="app-form-field">
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
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [rolesError, setRolesError] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("All");
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [createdUser, setCreatedUser] = useState<PlatformUserItem | null>(null);
  const [draft, setDraft] = useState<CreatePlatformUserDraft>(EMPTY_DRAFT);
  const [changingRole, setChangingRole] = useState<PlatformUserItem | null>(null);
  const [statusTarget, setStatusTarget] = useState<PlatformUserItem | null>(null);
  const [statusBusy, setStatusBusy] = useState(false);
  const [unlockTarget, setUnlockTarget] = useState<PlatformUserItem | null>(null);
  const [unlockBusy, setUnlockBusy] = useState(false);
  const [passwordTarget, setPasswordTarget] = useState<PlatformUserItem | null>(null);
  const [passwordBusy, setPasswordBusy] = useState(false);
  // MFA-01 / E8-S4: reset a staff member's two-step login (never your own).
  const [mfaTarget, setMfaTarget] = useState<PlatformUserItem | null>(null);
  const [mfaBusy, setMfaBusy] = useState(false);
  const [mfaError, setMfaError] = useState("");

  const resetMfa = async (reason: string) => {
    if (!mfaTarget) return;
    setMfaBusy(true);
    setMfaError("");
    try {
      await platformUserService.resetMfa(mfaTarget.platformUserId, reason);
      toast.success(
        "Two-step login reset",
        `${mfaTarget.fullName}'s two-step login was reset. They set up a new authenticator at their next sign-in.`
      );
      setMfaTarget(null);
    } catch (err) {
      setMfaError(readErrorMessage(err, "Could not reset two-step login."));
    } finally {
      setMfaBusy(false);
    }
  };

  const loadUsers = useCallback(() => {
    setLoading(true);
    setLoadError("");
    platformUserService
      .getPlatformUsers()
      .then((res) => setUsers(res.data))
      .catch((err) => setLoadError(readErrorMessage(err, "Could not load platform users.")))
      .finally(() => setLoading(false));
  }, []);

  const loadRoles = useCallback(async () => {
    if (!canManage) return;
    setRolesError("");
    try {
      const res = await platformRoleService.getRoles();
      setCustomRoles(Array.isArray(res?.data) ? res.data : []);
    } catch (err) {
      setRolesError(readErrorMessage(err, "Could not load custom platform roles."));
    }
  }, [canManage]);

  useEffect(() => {
    loadUsers();
    void loadRoles();
  }, [loadUsers, loadRoles]);

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
      toast.success("Platform user created", `${response.data.fullName} was created successfully.`);
      loadUsers();
    } catch (err) {
      const msg = readErrorMessage(err, "Failed to create platform user");
      setCreateError(msg);
      toast.error("Failed to create platform user", msg);
    } finally {
      setCreating(false);
    }
  };

  const applyStatus = async () => {
    if (!statusTarget) return;
    const reactivate = statusTarget.status !== "ACTIVE";
    setStatusBusy(true);
    try {
      await platformUserService.updatePlatformUser(statusTarget.platformUserId, {
        status: reactivate ? "ACTIVE" : "INACTIVE",
      });
      const msg = `${statusTarget.fullName} is ${reactivate ? "active again" : "deactivated and signed out"}.`;
      toast.success("Account status updated", msg);
      loadUsers();
    } catch (err) {
      toast.error("Status update failed", readErrorMessage(err, "Could not change this account."));
    } finally {
      setStatusBusy(false);
      setStatusTarget(null);
    }
  };

  const unlockAccount = async () => {
    if (!unlockTarget) return;
    setUnlockBusy(true);
    try {
      await platformUserService.unlockPlatformUser(unlockTarget.platformUserId);
      toast.success(
        "Account unlocked",
        `${unlockTarget.fullName}'s sign-in lockout was cleared. Their password and account settings were not changed.`
      );
      setUnlockTarget(null);
    } catch (err) {
      toast.error("Unlock failed", readErrorMessage(err, "Could not unlock this account."));
    } finally {
      setUnlockBusy(false);
    }
  };

  const resetPassword = async () => {
    if (!passwordTarget) return;
    setPasswordBusy(true);
    try {
      const { data } = await platformUserService.resetPassword(passwordTarget.platformUserId);
      setCreatedUser(data);
      toast.success("Password reset", `A new temporary password was generated for ${passwordTarget.fullName}.`);
      setPasswordTarget(null);
    } catch (err) {
      toast.error("Password reset failed", readErrorMessage(err, "Could not reset this platform user's password."));
      setPasswordTarget(null);
    } finally {
      setPasswordBusy(false);
    }
  };

  const filtered = users.filter(
    (u) =>
      (filter === "All" || (filter === "Active") === (u.status === "ACTIVE")) &&
      (u.fullName + u.email + roleLabel(u)).toLowerCase().includes(query.toLowerCase()),
  );

  return (
    <div className="app-page">
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
              setShowCreate(true);
            }}
          >
            Invite platform user
          </Button>
        }
      />
      <CreatedCredentialsModal
        isOpen={!!createdUser}
        onClose={() => setCreatedUser(null)}
        kicker="PLATFORM USER CREATED"
        title="Platform user created"
        description="Share this temporary password securely. It will only be shown once."
        user={{
          displayName: createdUser?.fullName ?? "",
          email: createdUser?.email,
          temporaryPassword: createdUser?.tempPassword,
        }}
      />
      {rolesError && (
        <ErrorState
          message={rolesError}
          action={<Button variant="secondary" size="sm" onClick={() => void loadRoles()}>Retry custom roles</Button>}
        />
      )}
      {loadError && (
        <ErrorState
          message={loadError}
          action={<Button variant="secondary" size="sm" onClick={loadUsers}>Try again</Button>}
        />
      )}
      <Panel
        title="Platform access"
        description="Platform staff accounts only. Institution users are managed within their institution."
      >
        <div style={{ display: "flex", gap: "var(--space-3)", flexWrap: "wrap", alignItems: "center", marginBottom: "var(--space-4)" }}>
          <div className="app-filter-tabs" role="tablist" aria-label="Filter platform staff by status">
            {(["All", "Active", "Inactive"] as const).map((st) => {
              const count = st === "All" ? users.length : users.filter((u) => u.status === (st === "Inactive" ? "INACTIVE" : "ACTIVE")).length;
              return (
                <button
                  key={st}
                  type="button"
                  role="tab"
                  aria-selected={filter === st}
                  className={`app-filter-tab ${filter === st ? "app-filter-tab--active" : ""}`}
                  onClick={() => setFilter(st)}
                >
                  {st} ({count})
                </button>
              );
            })}
          </div>
        </div>
        <div className="app-toolbar">
          <SearchBox value={query} onChange={setQuery} placeholder="Search name, email or role" />
          <label className="app-select-label" style={{ display: "none" }}>
            Status{" "}
            <select value={filter} onChange={(e) => setFilter(e.target.value)}>
              <option>All</option>
              <option>Active</option>
              <option>Inactive</option>
            </select>
          </label>
        </div>
        {!loadError && (
          <DataTable
            headers={["Name", "Email", "Platform role", "Status", "Last active", "Actions"]}
            loading={loading && users.length === 0}
            loadingLabel="Loading platform users"
            empty={!filtered.length}
            emptyMessage={users.length ? "No matching people." : "No platform staff yet."}
          >
            {filtered.map((u) => (
              <tr key={u.platformUserId}>
                <td><strong>{u.fullName}</strong></td>
                <td>{u.email}</td>
                <td>{roleLabel(u)}</td>
                <td><StatusBadge value={u.status === "ACTIVE" ? "Active" : "Inactive"} /></td>
                <td>{formatLastLogin(u.lastLoginAt)}</td>
                <td>
                  {canManage && u.platformUserId !== myId ? (
                    <div style={{ display: "flex", gap: "var(--space-2)", flexWrap: "wrap" }}>
                      <Button variant="secondary" size="sm" onClick={() => setChangingRole(u)}>
                        Change role
                      </Button>
                      <Button variant="secondary" size="sm" onClick={() => setStatusTarget(u)}>
                        {u.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
                      </Button>
                      {u.status === "ACTIVE" && (
                        <>
                          <Button variant="secondary" size="sm" onClick={() => setPasswordTarget(u)}>
                            Reset password
                          </Button>
                          <Button variant="secondary" size="sm" onClick={() => { setMfaError(""); setMfaTarget(u); }}>
                            Reset two-step login
                          </Button>
                          <Button variant="secondary" size="sm" onClick={() => setUnlockTarget(u)}>
                            Unlock sign-in
                          </Button>
                        </>
                      )}
                    </div>
                  ) : (
                    u.platformUserId === myId && <small className="app-cell-sub">This is you</small>
                  )}
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Panel>

      <MfaResetDialog
        isOpen={!!mfaTarget}
        personName={mfaTarget?.fullName ?? ""}
        busy={mfaBusy}
        error={mfaError}
        onConfirm={(reason) => void resetMfa(reason)}
        onCancel={() => setMfaTarget(null)}
      />
      <ConfirmDialog
        isOpen={!!unlockTarget}
        onClose={() => setUnlockTarget(null)}
        onConfirm={() => void unlockAccount()}
        title={unlockTarget ? `Unlock sign-in for ${unlockTarget.fullName}?` : "Unlock sign-in?"}
        message="This clears the failed-login lockout only. It does not change the password, MFA, role, or account activation."
        confirmLabel="Unlock sign-in"
        confirmTone="primary"
        busy={unlockBusy}
      />
      <ConfirmDialog
        isOpen={!!passwordTarget}
        onClose={() => setPasswordTarget(null)}
        onConfirm={() => void resetPassword()}
        title={passwordTarget ? `Reset password for ${passwordTarget.fullName}?` : "Reset password?"}
        message="A new temporary password will be generated. Their current password stops working immediately and all existing sessions will end."
        confirmLabel="Reset password"
        confirmTone="danger"
        busy={passwordBusy}
      />
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
            toast.success("Role updated", message);
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
