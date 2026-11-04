import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Badge, Button, EmptyState, ErrorState, PageHeader, Spinner } from "../../../components";
import { Kicker, TextField } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { getApiErrorMessage, type ApiError } from "../../../services/apiHelper";
import {
  userManagementService,
  type ActingScope,
  type ScopeType,
  type UserAccountStatus,
  type UserSummary,
} from "../api/userManagementService";

// WHAT: the tenant-side "Users & roles" register (USER_VIEW), a user's detail view with edit
// (USER_EDIT), deactivate (USER_DEACTIVATE) and password-reset/unlock (USER_PASSWORD_RESET_UNLOCK)
// actions, per api/src/main/java/com/edu360/api/rbac/*Controller.java.
//
// WHY every screen here asks the caller to state an "acting scope" (scopeType + optionally
// institutionId/departmentId) instead of just showing data: PermissionEvaluatorService has no
// column recording "the one scope a user operates in" - a principal can hold several differently
// scoped role assignments, so every one of these endpoints needs to be told exactly which one
// this request means (see UserViewController's own javadoc). This is NOT a filter on which users
// are shown; it is which of the caller's own grants is being exercised.
//
// WHY there is no client-side role gate on this route: AuthUser (store/slices/authSlice.ts) only
// carries tenantId/userType/platformRole - there is no tenant role name exposed to the frontend
// yet. Per CLAUDE.md, this screen is reachable by any authenticated tenant user and relies
// entirely on the backend's 403 (via PermissionEvaluatorService) for real enforcement. A 403 is
// shown as a clean "permission denied" state below, never a crash.

type LoadState = "idle" | "loading" | "loaded" | "denied" | "error";

function isForbidden(error: unknown): boolean {
  return !!(error && typeof error === "object" && "isAxiosError" in error && (error as ApiError).response?.status === 403);
}

function statusTone(status: UserAccountStatus): "success" | "warning" | "danger" {
  if (status === "ACTIVE") return "success";
  if (status === "PENDING_ACTIVATION") return "warning";
  return "danger";
}

function StatusBadge({ status }: { status: UserAccountStatus }) {
  return <Badge tone={statusTone(status)}>{status.replace("_", " ")}</Badge>;
}

function Panel({ title, description, action, children }: { title: string; description?: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="platform-panel">
      <header className="platform-panel__header">
        <div>
          <h2>{title}</h2>
          {description && <p>{description}</p>}
        </div>
        {action}
      </header>
      {children}
    </section>
  );
}

// The "acting scope" picker shared by the list screen and the detail screen's view/edit calls.
// institutionId/departmentId only appear once they are actually needed by the chosen scopeType,
// so nothing asks for a department when the caller is asserting a tenant-wide grant.
function ScopeFields({
  scope,
  onChange,
  idPrefix,
}: {
  scope: ActingScope;
  onChange: (next: ActingScope) => void;
  idPrefix: string;
}) {
  return (
    <div className="platform-form-grid">
      <label className="platform-form-field">
        Acting scope
        <select
          value={scope.scopeType}
          onChange={(e) => onChange({ ...scope, scopeType: e.target.value as ScopeType })}
        >
          <option value="TENANT">Tenant (university-wide)</option>
          <option value="INSTITUTION">Institution</option>
          <option value="DEPARTMENT">Department</option>
        </select>
      </label>
      {scope.scopeType !== "TENANT" && (
        <TextField
          id={`${idPrefix}-institution-id`}
          label="Institution ID"
          required
          value={scope.institutionId ?? ""}
          onChange={(e) => onChange({ ...scope, institutionId: e.target.value })}
          placeholder="UUID of the institution"
        />
      )}
      {scope.scopeType === "DEPARTMENT" && (
        <TextField
          id={`${idPrefix}-department-id`}
          label="Department ID"
          required
          value={scope.departmentId ?? ""}
          onChange={(e) => onChange({ ...scope, departmentId: e.target.value })}
          placeholder="UUID of the department"
        />
      )}
    </div>
  );
}

// A small "are you sure" step reused by deactivate/reset/unlock - same platform-modal pattern
// already used for the tenant-creation dialog on PlatformPages.tsx, so this introduces no new
// visual pattern.
function ConfirmDialog({
  titleId,
  title,
  description,
  confirmLabel,
  danger,
  busy,
  onConfirm,
  onCancel,
}: {
  titleId: string;
  title: string;
  description: string;
  confirmLabel: string;
  danger?: boolean;
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <div className="platform-modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) onCancel(); }}>
      <section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <header>
          <div>
            <Kicker>CONFIRM</Kicker>
            <h2 id={titleId}>{title}</h2>
            <p>{description}</p>
          </div>
          <button className="platform-icon-button" onClick={onCancel} aria-label="Close">×</button>
        </header>
        <footer>
          <Button variant="secondary" onClick={onCancel} disabled={busy}>Cancel</Button>
          <Button variant={danger ? "danger" : "primary"} onClick={onConfirm} loading={busy}>{confirmLabel}</Button>
        </footer>
      </section>
    </div>
  );
}

// A minimal create-user authorization check (USER_CREATE). Deliberately labeled as a check, not
// a creation: UserCreationController.java is a proof-of-integration endpoint only ("no user was
// created" per its own javadoc) - there is no real USER_CREATE story (Keycloak provisioning,
// app.user_account insert, audit entry) behind it yet.
function CreateUserCheckDialog({ tenantId, onClose }: { tenantId: string; onClose: () => void }) {
  const [scope, setScope] = useState<ActingScope>({ scopeType: "TENANT" });
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(null);

  const runCheck = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setResult(null);
    try {
      const { data } = await userManagementService.checkCreateUserAuthorization(tenantId, scope);
      setResult({ ok: true, message: data.message });
    } catch (error) {
      setResult({ ok: false, message: getApiErrorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="platform-modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="create-check-title">
        <header>
          <div>
            <Kicker>USER_CREATE</Kicker>
            <h2 id="create-check-title">Check if I can create a user</h2>
            <p>This only checks whether you are authorized to create a user in the chosen scope. It does not create an account - the backend endpoint behind this check is a proof-of-integration authorization check only.</p>
          </div>
          <button className="platform-icon-button" onClick={onClose} aria-label="Close">×</button>
        </header>
        <form onSubmit={runCheck}>
          <ScopeFields scope={scope} onChange={setScope} idPrefix="create-check" />
          {result && (
            <div className={result.ok ? "platform-inline-notice" : "auth-message auth-message--error"} role="status">
              {result.message}
            </div>
          )}
          <div className="platform-modal-note">
            No account is created by this dialog, regardless of the result shown above.
          </div>
          <footer>
            <Button type="button" variant="secondary" onClick={onClose}>Close</Button>
            <Button type="submit" loading={busy}>Check authorization</Button>
          </footer>
        </form>
      </section>
    </div>
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

  if (!tenantId) {
    return (
      <div className="platform-page">
        <PageHeader title="Users & roles" description="User account management (USER_VIEW)." />
        <ErrorState title="Not available" message="This screen is for tenant accounts. No tenant is associated with your sign-in." />
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
    (u.displayName + " " + u.email).toLowerCase().includes(query.toLowerCase()),
  );

  return (
    <div className="platform-page">
      <PageHeader
        title="Users & roles"
        description="View and manage tenant user accounts in a chosen scope (USER_VIEW)."
        actions={<Button variant="secondary" onClick={() => setShowCreateCheck(true)}>Check if I can create a user</Button>}
      />

      <Panel title="Choose a scope" description="State the scope your own role assignment is acting in, then load the register.">
        <form
          className="platform-toolbar"
          style={{ flexDirection: "column", alignItems: "stretch" }}
          onSubmit={(e) => { e.preventDefault(); void load(); }}
        >
          <ScopeFields scope={scope} onChange={setScope} idPrefix="list" />
          <div>
            <Button type="submit" loading={state === "loading"}>Load users</Button>
          </div>
        </form>
      </Panel>

      {state === "denied" && (
        <ErrorState title="Permission denied" message={errorMessage || "You do not have permission to view users in this scope."} />
      )}
      {state === "error" && (
        <ErrorState title="Could not load users" message={errorMessage} action={<Button variant="secondary" onClick={() => void load()}>Try again</Button>} />
      )}
      {state === "loading" && <Spinner label="Loading users" />}

      {state === "loaded" && (
        <Panel title="User register" description={`${filtered.length} of ${users.length} user${users.length === 1 ? "" : "s"} in this scope`}>
          <div className="platform-toolbar">
            <label className="platform-search">
              <span aria-hidden="true">⌕</span>
              <span className="visually-hidden">Search</span>
              <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search name or email" />
            </label>
          </div>
          {filtered.length === 0 ? (
            <EmptyState title="No users match" description="No user accounts were found for this scope or search." />
          ) : (
            <div className="platform-table-wrap">
              <table className="platform-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Mobile</th>
                    <th>Type</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((u) => (
                    <tr key={u.userAccountId}>
                      <td><strong>{u.displayName}</strong></td>
                      <td>{u.email}</td>
                      <td>{u.mobile}</td>
                      <td>{u.userType}</td>
                      <td><StatusBadge status={u.status} /></td>
                      <td>
                        <Link
                          className="eds-link-button"
                          to={`/tenants/users/${u.userAccountId}?scopeType=${scope.scopeType}${scope.institutionId ? `&institutionId=${scope.institutionId}` : ""}${scope.departmentId ? `&departmentId=${scope.departmentId}` : ""}`}
                        >
                          View
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Panel>
      )}

      {state === "idle" && (
        <EmptyState title="No users loaded yet" description="Choose a scope above and select Load users to view the register." />
      )}

      {showCreateCheck && <CreateUserCheckDialog tenantId={tenantId} onClose={() => setShowCreateCheck(false)} />}
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

  // Load immediately when the list page already supplied a scope via the URL; otherwise wait
  // for the caller to state one below (e.g. someone opened this page directly by URL). Runs once
  // on mount only - re-fetching on an edited scope happens explicitly via submitScope/actions.
  useEffect(() => {
    if (initialScopeType) void load(scope);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!tenantId || !userId) {
    return (
      <div className="platform-page">
        <PageHeader title="User detail" description="User account management (USER_VIEW)." />
        <ErrorState title="Not available" message="This screen is for tenant accounts with a valid user ID." />
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
      setSaveBanner({ tone: isForbidden(error) ? "danger" : "danger", message: getApiErrorMessage(error) });
    } finally {
      setSaving(false);
    }
  };

  const runDeactivate = async () => {
    setActionBusy(true);
    setActionBanner(null);
    try {
      const { data } = await userManagementService.deactivateUser(tenantId, userId, actionInstitutionId);
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
      const { data } = await userManagementService.resetPassword(tenantId, userId, actionInstitutionId);
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
      const { data } = await userManagementService.unlockUser(tenantId, userId, actionInstitutionId);
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
        description="View, edit and manage a single user account (USER_VIEW / USER_EDIT / USER_DEACTIVATE / USER_PASSWORD_RESET_UNLOCK)."
        actions={<Button variant="secondary" onClick={() => navigate("/tenants/users")}>Back to register</Button>}
      />

      {needsScope && (
        <Panel title="Choose a scope" description="State the scope your own role assignment is acting in, then load this user.">
          <form className="platform-toolbar" style={{ flexDirection: "column", alignItems: "stretch" }} onSubmit={submitScope}>
            <ScopeFields scope={scope} onChange={setScope} idPrefix="detail" />
            <div><Button type="submit">Load user</Button></div>
          </form>
        </Panel>
      )}

      {!needsScope && state === "loading" && <Spinner label="Loading user" />}
      {!needsScope && state === "denied" && (
        <ErrorState title="Permission denied" message={errorMessage || "You do not have permission to view this user in this scope."} />
      )}
      {!needsScope && state === "error" && (
        <ErrorState title="Could not load user" message={errorMessage} action={<Button variant="secondary" onClick={() => void load(scope)}>Try again</Button>} />
      )}

      {!needsScope && state === "loaded" && user && (
        <>
          <Panel title={user.displayName} description={`${user.userType} · ${user.email}`} action={<StatusBadge status={user.status} />}>
            <form className="platform-form-grid" style={{ padding: "0 var(--space-5) var(--space-5)" }} onSubmit={saveEdit}>
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
                  This account is deactivated. Deactivation is permanent in this system (no reactivate), so it can no longer be edited.
                </div>
              ) : (
                <div style={{ gridColumn: "1 / -1" }}>
                  <Button type="submit" loading={saving}>Save changes</Button>
                </div>
              )}
              {saveBanner && (
                <div
                  className={saveBanner.tone === "success" ? "platform-inline-notice" : "auth-message auth-message--error"}
                  role="status"
                  style={{ gridColumn: "1 / -1" }}
                >
                  {saveBanner.message}
                </div>
              )}
            </form>
          </Panel>

          <Panel
            title="Institution-scoped actions"
            description="USER_DEACTIVATE and USER_PASSWORD_RESET_UNLOCK are always checked against your own institution-scoped grant, never tenant or department scope."
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
              <div style={{ display: "flex", gap: "var(--space-2)", flexWrap: "wrap", marginTop: "var(--space-4)" }}>
                <Button
                  variant="danger"
                  disabled={!actionInstitutionId || user.status === "DEACTIVATED"}
                  onClick={() => setConfirming("deactivate")}
                >
                  Deactivate user
                </Button>
                <Button variant="secondary" disabled={!actionInstitutionId} onClick={() => setConfirming("reset")}>
                  Reset password
                </Button>
                <Button variant="secondary" disabled={!actionInstitutionId} onClick={() => setConfirming("unlock")}>
                  Unlock account
                </Button>
              </div>
              {user.status === "DEACTIVATED" && (
                <p className="platform-empty-inline">This user is already deactivated.</p>
              )}
              {actionBanner && (
                <div
                  className={actionBanner.tone === "danger" ? "auth-message auth-message--error" : "platform-inline-notice"}
                  role="status"
                  style={{ marginTop: "var(--space-3)" }}
                >
                  {actionBanner.message}
                </div>
              )}
            </div>
          </Panel>
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

      {temporaryPassword && (
        <div className="platform-modal-backdrop" role="presentation">
          <section className="platform-modal" role="dialog" aria-modal="true" aria-labelledby="temp-password-title">
            <header>
              <div>
                <Kicker>ONE-TIME DISPLAY</Kicker>
                <h2 id="temp-password-title">Copy this password now</h2>
                <p>This temporary password will not be shown again after you close this dialog. It is not stored anywhere in this browser.</p>
              </div>
            </header>
            <div className="eds-field">
              <label htmlFor="temp-password-value">Temporary password</label>
              <input id="temp-password-value" readOnly value={temporaryPassword} onFocus={(e) => e.target.select()} />
            </div>
            {copyNotice && <p className="platform-empty-inline">{copyNotice}</p>}
            <footer>
              <Button variant="secondary" onClick={() => void copyTempPassword()}>Copy to clipboard</Button>
              <Button
                onClick={() => {
                  setActionBanner({ tone: "success", message: "Password reset for this user. The temporary password was shown once and is no longer available." });
                  setTemporaryPassword(null);
                  setCopyNotice("");
                }}
              >
                I've copied it, close
              </Button>
            </footer>
          </section>
        </div>
      )}
    </div>
  );
}
