import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  Button,
  ConfirmDialog as DsConfirmDialog,
  DataTable,
  EmptyState,
  ErrorState,
  Modal,
  Notice,
  PageHeader,
  Panel,
  SearchBox,
  Spinner,
  StatusBadge,
  TextField,
} from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { getApiErrorMessage, type ApiError } from "../../../services/apiHelper";
import {
  userManagementService,
  type ActingScope,
  type CreateUserResponse,
  type StudentProfileResponse,
  type ScopeType,
  type TopUserResponse,
  type UserSummary,
} from "../api/userManagementService";
import {
  ScopeFields,
  CreateUserCheckModal,
  CreateUserModal,
  CreateStudentModal,
  CreatedCredentialsModal,
  CreateTopUserModal,
  BulkUploadModal,
  StudentStatusPanel,
  GuardianManagementPanel,
} from "../components";

export {
  ScopeFields,
  CreateUserCheckModal,
  CreateUserModal,
  CreateStudentModal,
  CreatedCredentialsModal,
  CreateTopUserModal,
  BulkUploadModal,
  StudentStatusPanel,
  GuardianManagementPanel,
};

type LoadState = "idle" | "loading" | "loaded" | "denied" | "error";

function isForbidden(error: unknown): boolean {
  return !!(
    error &&
    typeof error === "object" &&
    "isAxiosError" in error &&
    (error as ApiError).response?.status === 403
  );
}

// Re-export aliases for tests and backward compatibility
export const CreateUserCheckDialog = CreateUserCheckModal;
export const CreateUserDialog = CreateUserModal;
export const CreateGovernanceUserDialog = CreateTopUserModal;
export const BulkUploadUsersDialog = BulkUploadModal;
export const OneTimePasswordModal = CreatedCredentialsModal;

export function ConfirmDialog({
  title,
  description,
  confirmLabel,
  danger,
  busy,
  onConfirm,
  onCancel,
}: {
  titleId?: string;
  title: string;
  description: string;
  confirmLabel: string;
  danger?: boolean;
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <DsConfirmDialog
      isOpen={true}
      title={title}
      message={description}
      confirmLabel={confirmLabel}
      confirmTone={danger ? "danger" : "primary"}
      busy={busy}
      onConfirm={onConfirm}
      onClose={onCancel}
    />
  );
}

export function UserListPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [scope, setScope] = useState<ActingScope>({ scopeType: "TENANT" });
  const [state, setState] = useState<LoadState>("idle");
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [errorMessage, setErrorMessage] = useState("");
  const [query, setQuery] = useState("");
  const [showCreateCheck, setShowCreateCheck] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showCreateStudentModal, setShowCreateStudentModal] = useState(false);
  const [showBulkModal, setShowBulkModal] = useState(false);
  const [showGovernanceModal, setShowGovernanceModal] = useState(false);
  const [createdUser, setCreatedUser] = useState<CreateUserResponse | TopUserResponse | null>(null);
  const [createdStudent, setCreatedStudent] = useState<StudentProfileResponse | null>(null);

  if (!tenantId) {
    return (
      <div className="platform-page">
        <PageHeader title="Users & roles" description="Manage tenant user accounts within your permitted scope." />
        <ErrorState
          title="Not available"
          message="This screen is for tenant accounts. No tenant is associated with your sign-in."
        />
      </div>
    );
  }

  const load = async () => {
    if (scope.scopeType !== "TENANT" && !scope.institutionId) {
      setState("error");
      setErrorMessage("Enter an institution ID for this scope before loading users.");
      return;
    }
    if (scope.scopeType === "DEPARTMENT" && !scope.departmentId) {
      setState("error");
      setErrorMessage("Enter a department ID for this scope before loading users.");
      return;
    }
    setState("loading");
    setErrorMessage("");
    try {
      const { data } = await userManagementService.listUsers(tenantId, scope);
      setUsers(data.users);
      setState("loaded");
    } catch (error) {
      setUsers([]);
      if (isForbidden(error)) {
        setState("denied");
      } else {
        setState("error");
      }
      setErrorMessage(getApiErrorMessage(error));
    }
  };

  const filtered = users.filter((u) =>
    (u.displayName + " " + u.email).toLowerCase().includes(query.toLowerCase())
  );

  return (
    <div className="platform-page">
      <PageHeader
        title="Users & roles"
        description="View and manage tenant user accounts within your permitted scope."
        actions={
          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
            <Button variant="secondary" onClick={() => setShowCreateCheck(true)}>
              Check if I can create a user
            </Button>
            <Button variant="secondary" onClick={() => setShowCreateStudentModal(true)}>
              Create student profile
            </Button>
            <Button variant="secondary" onClick={() => setShowBulkModal(true)}>
              Bulk import user accounts
            </Button>
            <Button variant="secondary" onClick={() => setShowGovernanceModal(true)}>
              Create governance user
            </Button>
            <Button variant="primary" onClick={() => setShowCreateModal(true)}>
              Create user
            </Button>
          </div>
        }
      />

      <Panel
        title="Choose a scope"
        description="State the scope your own role assignment is acting in, then load the register."
      >
        <form
          className="platform-toolbar"
          style={{ flexDirection: "column", alignItems: "stretch" }}
          onSubmit={(e) => {
            e.preventDefault();
            void load();
          }}
        >
          <ScopeFields scope={scope} onChange={setScope} idPrefix="list" />
          <div>
            <Button type="submit" busy={state === "loading"}>
              Load users
            </Button>
          </div>
        </form>
      </Panel>

      {state === "denied" && (
        <ErrorState
          title="Permission denied"
          message={errorMessage || "You do not have permission to view users in this scope."}
        />
      )}
      {state === "error" && (
        <ErrorState
          title="Could not load users"
          message={errorMessage}
          action={
            <Button variant="secondary" onClick={() => void load()}>
              Try again
            </Button>
          }
        />
      )}
      {state === "loading" && <Spinner label="Loading users" />}

      {state === "loaded" && (
        <Panel
          title="User register"
          description={`${filtered.length} of ${users.length} user${users.length === 1 ? "" : "s"} in this scope`}
        >
          <div className="platform-toolbar">
            <SearchBox
              value={query}
              onChange={setQuery}
              placeholder="Search name or email"
            />
          </div>
          {filtered.length === 0 ? (
            <EmptyState
              title="No users match"
              description="No user accounts were found for this scope or search."
            />
          ) : (
            <DataTable
              headers={["Name", "Email", "Mobile", "Type", "Status", "Action"]}
            >
              {filtered.map((u) => (
                <tr key={u.userAccountId}>
                  <td>
                    <strong>{u.displayName}</strong>
                  </td>
                  <td>{u.email}</td>
                  <td>{u.mobile}</td>
                  <td>{u.userType}</td>
                  <td>
                    <StatusBadge status={u.status} />
                  </td>
                  <td>
                    <Link
                      className="eds-link-button"
                      to={`/tenants/users/${u.userAccountId}?scopeType=${scope.scopeType}${
                        scope.institutionId ? `&institutionId=${scope.institutionId}` : ""
                      }${scope.departmentId ? `&departmentId=${scope.departmentId}` : ""}`}
                    >
                      View
                    </Link>
                  </td>
                </tr>
              ))}
            </DataTable>
          )}
        </Panel>
      )}

      {state === "idle" && (
        <EmptyState
          title="No users loaded yet"
          description="Choose a scope above and select Load users to view the register."
        />
      )}

      <CreateUserCheckModal
        tenantId={tenantId}
        isOpen={showCreateCheck}
        onClose={() => setShowCreateCheck(false)}
      />

      <BulkUploadModal
        tenantId={tenantId}
        isOpen={showBulkModal}
        onClose={() => setShowBulkModal(false)}
        onImportComplete={() => {
          void load();
        }}
      />

      <CreateTopUserModal
        tenantId={tenantId}
        isOpen={showGovernanceModal}
        onClose={() => setShowGovernanceModal(false)}
        onCreated={(topUser) => {
          setShowGovernanceModal(false);
          setCreatedUser(topUser);
          void load();
        }}
      />

      <CreateStudentModal
        tenantId={tenantId}
        isOpen={showCreateStudentModal}
        onClose={() => setShowCreateStudentModal(false)}
        onCreated={(student) => {
          setShowCreateStudentModal(false);
          setCreatedStudent(student);
          void load();
        }}
      />

      <CreateUserModal
        tenantId={tenantId}
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onCreated={(newUsr) => {
          setShowCreateModal(false);
          setCreatedUser(newUsr);
          void load();
        }}
      />

      {createdStudent && (
        <CreatedCredentialsModal
          user={{
            displayName: createdStudent.fullName,
            identifier: { label: "Student code", value: createdStudent.studentCode },
            temporaryPassword: createdStudent.temporaryPassword ?? undefined,
            message: "Student profile and enrolment were created. Login provisioning follows the institution and class settings.",
          }}
          isOpen={true}
          onClose={() => setCreatedStudent(null)}
          title="Student profile created successfully"
          kicker="STUDENT PROFILE"
          description="Share any issued temporary password securely. It is shown only in this confirmation."
        />
      )}

      {createdUser && (
        <CreatedCredentialsModal
          user={createdUser}
          isOpen={true}
          onClose={() => setCreatedUser(null)}
        />
      )}
    </div>
  );
}

type ActionBanner = { tone: "success" | "info" | "danger"; message: string } | null;

export function UserDetailPage() {
  const { userId } = useParams<{ userId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);

  const initialScopeType = searchParams.get("scopeType") as ScopeType | null;
  const [scope, setScope] = useState<ActingScope>({
    scopeType: initialScopeType ?? "TENANT",
    institutionId: searchParams.get("institutionId") ?? "",
    departmentId: searchParams.get("departmentId") ?? "",
  });
  const [needsScope, setNeedsScope] = useState(!initialScopeType);

  const [state, setState] = useState<LoadState>(initialScopeType ? "loading" : "idle");
  const [user, setUser] = useState<UserSummary | null>(null);
  const [errorMessage, setErrorMessage] = useState("");

  const [fields, setFields] = useState({ displayName: "", email: "", mobile: "" });
  const [saving, setSaving] = useState(false);
  const [saveBanner, setSaveBanner] = useState<ActionBanner>(null);

  const [actionInstitutionId, setActionInstitutionId] = useState(scope.institutionId ?? "");
  const [confirming, setConfirming] = useState<"deactivate" | "reset" | "unlock" | null>(null);
  const [actionBusy, setActionBusy] = useState(false);
  const [actionBanner, setActionBanner] = useState<ActionBanner>(null);
  const [temporaryPassword, setTemporaryPassword] = useState<string | null>(null);
  const [copyNotice, setCopyNotice] = useState("");

  const load = async (scopeToUse: ActingScope) => {
    if (!tenantId || !userId) return;
    setState("loading");
    setErrorMessage("");
    try {
      const { data } = await userManagementService.getUser(tenantId, userId, scopeToUse);
      setUser(data);
      setFields({ displayName: data.displayName, email: data.email, mobile: data.mobile });
      setState("loaded");
    } catch (error) {
      setUser(null);
      setState(isForbidden(error) ? "denied" : "error");
      setErrorMessage(getApiErrorMessage(error));
    }
  };

  useEffect(() => {
    if (initialScopeType) void load(scope);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!tenantId || !userId) {
    return (
      <div className="platform-page">
        <PageHeader title="User detail" description="Manage tenant user accounts and their profile details." />
        <ErrorState
          title="Not available"
          message="This screen is for tenant accounts with a valid user ID."
        />
      </div>
    );
  }

  const submitScope = (event: FormEvent) => {
    event.preventDefault();
    setNeedsScope(false);
    void load(scope);
  };

  const saveEdit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setSaveBanner(null);
    try {
      const { data } = await userManagementService.updateUser(tenantId, userId, scope, fields);
      setUser(data);
      setSaveBanner({ tone: "success", message: "Changes saved." });
    } catch (error) {
      setSaveBanner({
        tone: isForbidden(error) ? "danger" : "danger",
        message: getApiErrorMessage(error),
      });
    } finally {
      setSaving(false);
    }
  };

  const runDeactivate = async () => {
    setActionBusy(true);
    setActionBanner(null);
    try {
      const { data } = await userManagementService.deactivateUser(
        tenantId,
        userId,
        actionInstitutionId
      );
      if (data.reasonCode === "ALREADY_DEACTIVATED") {
        setActionBanner({ tone: "info", message: data.message });
      } else {
        setActionBanner({ tone: "success", message: data.message });
        setUser((prev) => (prev ? { ...prev, status: "DEACTIVATED" } : prev));
      }
      setConfirming(null);
    } catch (error) {
      setActionBanner({ tone: "danger", message: getApiErrorMessage(error) });
    } finally {
      setActionBusy(false);
    }
  };

  const runResetPassword = async () => {
    setActionBusy(true);
    setActionBanner(null);
    try {
      const { data } = await userManagementService.resetPassword(
        tenantId,
        userId,
        actionInstitutionId
      );
      setTemporaryPassword(data.temporaryPassword);
      setConfirming(null);
    } catch (error) {
      setActionBanner({ tone: "danger", message: getApiErrorMessage(error) });
    } finally {
      setActionBusy(false);
    }
  };

  const runUnlock = async () => {
    setActionBusy(true);
    setActionBanner(null);
    try {
      const { data } = await userManagementService.unlockUser(
        tenantId,
        userId,
        actionInstitutionId
      );
      setActionBanner({ tone: "success", message: data.message });
      setConfirming(null);
    } catch (error) {
      setActionBanner({ tone: "danger", message: getApiErrorMessage(error) });
    } finally {
      setActionBusy(false);
    }
  };

  const copyTempPassword = async () => {
    if (!temporaryPassword) return;
    try {
      await navigator.clipboard.writeText(temporaryPassword);
      setCopyNotice("Copied to clipboard.");
    } catch {
      setCopyNotice("Could not copy automatically - select and copy the password manually.");
    }
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="User detail"
        description="View, edit and manage a single user account. Sensitive changes are checked against your active role and institution scope."
        actions={
          <Button variant="secondary" onClick={() => navigate("/tenants/users")}>
            Back to register
          </Button>
        }
      />

      {needsScope && (
        <Panel
          title="Choose a scope"
          description="State the scope your own role assignment is acting in, then load this user."
        >
          <form
            className="platform-toolbar"
            style={{ flexDirection: "column", alignItems: "stretch" }}
            onSubmit={submitScope}
          >
            <ScopeFields scope={scope} onChange={setScope} idPrefix="detail" />
            <div>
              <Button type="submit">Load user</Button>
            </div>
          </form>
        </Panel>
      )}

      {!needsScope && state === "loading" && <Spinner label="Loading user" />}
      {!needsScope && state === "denied" && (
        <ErrorState
          title="Permission denied"
          message={errorMessage || "You do not have permission to view this user in this scope."}
        />
      )}
      {!needsScope && state === "error" && (
        <ErrorState
          title="Could not load user"
          message={errorMessage}
          action={
            <Button variant="secondary" onClick={() => void load(scope)}>
              Try again
            </Button>
          }
        />
      )}

      {!needsScope && state === "loaded" && user && (
        <>
          <Panel
            title={user.displayName}
            description={`${user.userType} · ${user.email}`}
            action={<StatusBadge status={user.status} />}
          >
            <form
              className="platform-form-grid"
              style={{ padding: "0 var(--space-5) var(--space-5)" }}
              onSubmit={saveEdit}
            >
              <TextField
                id="edit-display-name"
                label="Display name"
                value={fields.displayName}
                disabled={user.status === "DEACTIVATED"}
                onChange={(e) => setFields({ ...fields, displayName: e.target.value })}
              />
              <TextField
                id="edit-email"
                label="Email"
                type="email"
                value={fields.email}
                disabled={user.status === "DEACTIVATED"}
                onChange={(e) => setFields({ ...fields, email: e.target.value })}
              />
              <TextField
                id="edit-mobile"
                label="Mobile"
                value={fields.mobile}
                disabled={user.status === "DEACTIVATED"}
                onChange={(e) => setFields({ ...fields, mobile: e.target.value })}
              />
              {user.status === "DEACTIVATED" ? (
                <div className="platform-modal-note" style={{ gridColumn: "1 / -1" }}>
                  This account is deactivated. Deactivation is permanent in this system (no
                  reactivate), so it can no longer be edited.
                </div>
              ) : (
                <div style={{ gridColumn: "1 / -1" }}>
                  <Button type="submit" busy={saving}>
                    Save changes
                  </Button>
                </div>
              )}
              {saveBanner && (
                <div style={{ gridColumn: "1 / -1" }}>
                  <Notice tone={saveBanner.tone === "success" ? "success" : "danger"}>
                    {saveBanner.message}
                  </Notice>
                </div>
              )}
            </form>
          </Panel>

          <Panel
            title="Institution-scoped actions"
            description="Account deactivation and password recovery are always checked against your own institution scope."
          >
            <div style={{ padding: "0 var(--space-5) var(--space-5)" }}>
              <TextField
                id="action-institution-id"
                label="Institution ID"
                required
                value={actionInstitutionId}
                onChange={(e) => setActionInstitutionId(e.target.value)}
                placeholder="UUID of your institution"
              />
              <div
                style={{
                  display: "flex",
                  gap: "var(--space-2)",
                  flexWrap: "wrap",
                  marginTop: "var(--space-4)",
                }}
              >
                <Button
                  variant="danger"
                  disabled={!actionInstitutionId || user.status === "DEACTIVATED"}
                  onClick={() => setConfirming("deactivate")}
                >
                  Deactivate user
                </Button>
                <Button
                  variant="secondary"
                  disabled={!actionInstitutionId}
                  onClick={() => setConfirming("reset")}
                >
                  Reset password
                </Button>
                <Button
                  variant="secondary"
                  disabled={!actionInstitutionId}
                  onClick={() => setConfirming("unlock")}
                >
                  Unlock account
                </Button>
              </div>
              {user.status === "DEACTIVATED" && (
                <p className="platform-empty-inline">This user is already deactivated.</p>
              )}
              {actionBanner && (
                <div style={{ marginTop: "var(--space-3)" }}>
                  <Notice tone={actionBanner.tone === "danger" ? "danger" : "success"}>
                    {actionBanner.message}
                  </Notice>
                </div>
              )}
            </div>
          </Panel>

          {user.userType === "STUDENT" && (
            <>
              <StudentStatusPanel tenantId={tenantId} studentId={userId!} />
              <GuardianManagementPanel
                tenantId={tenantId}
                studentId={userId!}
                institutionId={actionInstitutionId}
              />
            </>
          )}
        </>
      )}

      {confirming === "deactivate" && (
        <ConfirmDialog
          titleId="confirm-deactivate-title"
          title="Deactivate this user?"
          description="This is a permanent soft delete - the account cannot be reactivated afterwards. It never removes the underlying record."
          confirmLabel="Deactivate"
          danger
          busy={actionBusy}
          onConfirm={() => void runDeactivate()}
          onCancel={() => setConfirming(null)}
        />
      )}
      {confirming === "reset" && (
        <ConfirmDialog
          titleId="confirm-reset-title"
          title="Reset this user's password?"
          description="A new temporary password will be generated in Keycloak and shown to you once. The user's current password stops working immediately."
          confirmLabel="Reset password"
          busy={actionBusy}
          onConfirm={() => void runResetPassword()}
          onCancel={() => setConfirming(null)}
        />
      )}
      {confirming === "unlock" && (
        <ConfirmDialog
          titleId="confirm-unlock-title"
          title="Unlock this user's account?"
          description="This restores the user's ability to sign in via a fresh credential reset. See the on-screen note after unlocking for a known limitation."
          confirmLabel="Unlock"
          busy={actionBusy}
          onConfirm={() => void runUnlock()}
          onCancel={() => setConfirming(null)}
        />
      )}

      <Modal
        isOpen={!!temporaryPassword}
        onClose={() => {
          setActionBanner({
            tone: "success",
            message:
              "Password reset for this user. The temporary password was shown once and is no longer available.",
          });
          setTemporaryPassword(null);
          setCopyNotice("");
        }}
        kicker="ONE-TIME DISPLAY"
        title="Copy this password now"
        titleId="temp-password-title"
        description="This temporary password will not be shown again after you close this dialog. It is not stored anywhere in this browser."
        size="sm"
        footer={
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", width: "100%" }}>
            <Button variant="secondary" onClick={() => void copyTempPassword()}>
              Copy to clipboard
            </Button>
            <Button
              onClick={() => {
                setActionBanner({
                  tone: "success",
                  message:
                    "Password reset for this user. The temporary password was shown once and is no longer available.",
                });
                setTemporaryPassword(null);
                setCopyNotice("");
              }}
            >
              I've copied it, close
            </Button>
          </div>
        }
      >
        <div className="eds-field">
          <label htmlFor="temp-password-value">Temporary password</label>
          <input
            id="temp-password-value"
            readOnly
            value={temporaryPassword ?? ""}
            onFocus={(e) => e.target.select()}
            style={{
              fontFamily: "monospace",
              fontSize: "1.1rem",
              padding: "0.5rem",
              borderRadius: "4px",
              border: "1px solid var(--color-border, #cbd5e1)",
              width: "100%",
            }}
          />
        </div>
        {copyNotice && <p className="platform-empty-inline" style={{ marginTop: "8px" }}>{copyNotice}</p>}
      </Modal>
    </div>
  );
}

export default UserListPage;
