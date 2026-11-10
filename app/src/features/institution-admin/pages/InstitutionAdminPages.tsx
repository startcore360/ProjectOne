import { useEffect, useMemo, useState } from "react";
import {
  Button,
  Kicker,
  Panel,
  StatusBadge as StateBadge,
  Badge,
  PageHeader,
} from "../../../design-system/components";
import { Icon } from "../../../components/ui/Icon";
import { useAppSelector } from "../../../store/hooks";
import { getApiErrorMessage } from "../../../services/apiHelper";
import {
  institutionService,
  type InstitutionItem,
} from "../api/institutionService";
import {
  roleAssignmentService,
  type RoleDefinitionItem,
  type RoleDefinitionStatus,
} from "../api/roleAssignmentService";
import {
  leaveApproverService,
  type GoLiveCheckResponse,
} from "../api/leaveApproverService";
import {
  rolldownService,
  type RolldownMapEntry,
  type RolldownOverride,
} from "../api/rolldownService";
import {
  CreateInstitutionModal,
  BlockRequestModal,
  GovernanceDecisionModal,
  PrincipalLeaveApproverModal,
  ViceChancellorModal,
  CreateCustomRoleModal,
  RetireCustomRoleModal,
  RolldownOverrideModal,
} from "../components";

export function InstitutionsPage() {
  const user = useAppSelector((state) => state.auth.user);
  const tenantId = user?.tenantId || "00000000-0000-0000-0000-000000000001";
  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("All");
  const [modalOpen, setModalOpen] = useState(false);
  const [activatingId, setActivatingId] = useState<string | null>(null);
  const [blockModalItem, setBlockModalItem] = useState<InstitutionItem | null>(null);
  const [decideModalItem, setDecideModalItem] = useState<InstitutionItem | null>(null);
  const [feedback, setFeedback] = useState<{
    tone: "success" | "danger" | "info";
    message: string;
  } | null>(null);

  // Check if role is restricted from creating/managing institutions
  const isNonAdminRole =
    user?.roleCode === "SCHOOL_ADMIN" ||
    user?.roleCode === "COLLEGE_ADMIN" ||
    user?.roleCode === "STUDENT_COLLEGE" ||
    user?.roleCode === "STUDENT_SCHOOL" ||
    user?.roleCode === "PARENT_COLLEGE" ||
    user?.roleCode === "PARENT_SCHOOL" ||
    user?.roleCode === "CLASS_TEACHER" ||
    user?.roleCode === "SUBJECT_TEACHER";
  const canManageInstitutions = !isNonAdminRole;

  // Leave Approver state (Stories #45 & #46)
  const [goLiveCheck, setGoLiveCheck] = useState<GoLiveCheckResponse | null>(null);
  const [approverModalItem, setApproverModalItem] = useState<InstitutionItem | null>(null);
  const [vcModalOpen, setVcModalOpen] = useState(false);

  const fetchInstitutions = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await institutionService.listInstitutions(tenantId);
      setInstitutions(data.items);
      try {
        const check = await leaveApproverService.getGoLiveCheck(tenantId);
        setGoLiveCheck(check);
      } catch {
        // silent fallback if go-live check not applicable
      }
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInstitutions();
  }, [tenantId]);

  const handleActivate = async (item: InstitutionItem) => {
    setActivatingId(item.institutionId);
    setFeedback(null);
    try {
      const updated = await institutionService.activateInstitution(tenantId, item.institutionId);
      setInstitutions((prev) =>
        prev.map((inst) => (inst.institutionId === updated.institutionId ? updated : inst))
      );
      setFeedback({
        tone: "success",
        message: `Institution '${updated.institutionName}' activated successfully!`,
      });
    } catch (err: unknown) {
      const e = err as { response?: { data?: { errorCode?: string; error?: string; message?: string } } };
      const errorCode = e?.response?.data?.errorCode;
      const errorMsg = e?.response?.data?.error || e?.response?.data?.message || getApiErrorMessage(err);
      if (errorCode === "GO_LIVE_BLOCKED" || errorMsg.includes("GO_LIVE_BLOCKED") || errorMsg.includes("[C7]")) {
        setFeedback({
          tone: "danger",
          message: `Activation blocked: Principal's-leave approver not set for '${item.institutionName}' [Rule C7]. Please configure the leave approver in Settings before going live.`,
        });
      } else {
        setFeedback({
          tone: "danger",
          message: `Failed to activate institution: ${errorMsg}`,
        });
      }
    } finally {
      setActivatingId(null);
    }
  };

  const filtered = useMemo(() => {
    return institutions.filter((item) => {
      const matchesQuery =
        !query ||
        item.institutionName.toLowerCase().includes(query.toLowerCase()) ||
        item.institutionCode.toLowerCase().includes(query.toLowerCase()) ||
        (item.city && item.city.toLowerCase().includes(query.toLowerCase()));
      const matchesStatus = statusFilter === "All" || item.status === statusFilter;
      return matchesQuery && matchesStatus;
    });
  }, [institutions, query, statusFilter]);

  return (
    <div className="platform-page">
      <PageHeader
        title="Institutions"
        description="Register colleges and schools within this tenant. Institutions begin as Draft and require Vice Chancellor approval before going live [WF-16, C7]."
        actions={
          canManageInstitutions ? (
            <div style={{ display: "flex", gap: "var(--space-2)" }}>
              <Button
                variant="secondary"
                onClick={() => setVcModalOpen(true)}
                title="Configure Vice-Chancellor leave approver [Rule C6, VC_LEAVE_APPROVER_SET]"
              >
                Set VC Approver
              </Button>
              <Button onClick={() => setModalOpen(true)}>
                <span aria-hidden="true">+ </span>Register institution (Draft)
              </Button>
            </div>
          ) : (
            <Badge tone="neutral">Scope: Read-only View</Badge>
          )
        }
      />

      {!canManageInstitutions && (
        <div
          role="status"
          style={{
            padding: "var(--space-3) var(--space-4)",
            marginBottom: "var(--space-4)",
            borderRadius: "var(--radius-md)",
            border: "1px solid var(--eds-color-info-border, #bfdbfe)",
            backgroundColor: "var(--eds-color-info-bg, #eff6ff)",
            color: "var(--eds-color-info-text, #1e40af)",
            fontSize: "0.875rem",
            display: "flex",
            alignItems: "center",
            gap: "var(--space-2)",
          }}
        >
          <span aria-hidden="true">ℹ</span>
          <span>
            <strong>Read-only institution register:</strong> Institution creation (colleges &amp; schools) and executive approvals are restricted to University Administrators [Rule INSTITUTION_CREATE_DRAFT]. As a {user?.roleName || "College / School Administrator"}, your assigned institutions are listed below.
          </span>
        </div>
      )}

      {goLiveCheck && !goLiveCheck.ready && (
        <div
          role="region"
          aria-label="Go-Live Readiness Warning"
          style={{
            padding: "var(--space-3) var(--space-4)",
            marginBottom: "var(--space-4)",
            borderRadius: "var(--radius-md)",
            border: "1px solid var(--eds-color-warning-border, #f59e0b)",
            backgroundColor: "var(--eds-color-warning-bg, #fffbeb)",
            color: "var(--eds-color-warning-text, #92400e)",
            fontSize: "0.875rem",
          }}
        >
          <strong>Go-Live Readiness Warning [Rule C7 / C6]:</strong>
          <p style={{ margin: "4px 0" }}>
            The following statutory leave approver settings are missing before institutions/tenant can go live:
          </p>
          <ul style={{ paddingLeft: "var(--space-4)", margin: "4px 0" }}>
            {goLiveCheck.blockers.map((b, idx) => (
              <li key={idx}>
                {b.blocker === "VC_LEAVE_APPROVER_MISSING"
                  ? "Vice-Chancellor leave approver not set (Rule C6 required for university group)."
                  : `Principal leave approver not set for institution ID: ${b.institutionId || "unknown"}.`}
              </li>
            ))}
          </ul>
        </div>
      )}

      {feedback && (
        <div
          role="alert"
          style={{
            padding: "var(--space-3) var(--space-4)",
            marginBottom: "var(--space-4)",
            borderRadius: "var(--radius-md)",
            border: `1px solid var(--eds-color-${feedback.tone === "danger" ? "danger" : "success"}-border, #e0e0e0)`,
            backgroundColor: `var(--eds-color-${feedback.tone === "danger" ? "danger" : "success"}-bg, #f9f9f9)`,
            color: `var(--eds-color-${feedback.tone === "danger" ? "danger" : "success"}-text, #333)`,
            fontSize: "0.875rem",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <span>{feedback.message}</span>
          <button
            onClick={() => setFeedback(null)}
            style={{ background: "none", border: "none", cursor: "pointer", fontWeight: "bold" }}
          >
            ×
          </button>
        </div>
      )}

      {error && (
        <div className="eds-error-state" role="alert" style={{ marginBottom: "var(--space-4)" }}>
          <h3 className="eds-error-state__title">Unable to load institutions</h3>
          <p className="eds-error-state__message">{error}</p>
          <Button onClick={fetchInstitutions} style={{ marginTop: "var(--space-2)" }}>
            Retry
          </Button>
        </div>
      )}

      <Panel title="Institution register" description="University scope · Plan limit enforced [WF-14]">
        <div className="platform-toolbar" style={{ display: "flex", gap: "var(--space-3)", marginBottom: "var(--space-3)" }}>
          <label className="platform-search" style={{ flex: 1 }}>
            <span aria-hidden="true">
              <Icon name="search" size={17} />
            </span>
            <span className="visually-hidden">Search institutions</span>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search code, name or city"
            />
          </label>
          <label className="platform-select-label">
            Status
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              style={{ marginLeft: "var(--space-2)" }}
            >
              <option value="All">All statuses</option>
              <option value="DRAFT">Draft</option>
              <option value="ACTIVE">Active</option>
              <option value="BLOCKED">Blocked</option>
              <option value="ARCHIVED">Archived</option>
            </select>
          </label>
        </div>

        {loading && institutions.length === 0 ? (
          <div style={{ padding: "var(--space-6)", textAlign: "center", color: "var(--color-muted)" }}>
            Loading institutions...
          </div>
        ) : filtered.length === 0 ? (
          <div className="platform-admin-empty" style={{ padding: "var(--space-6)", textAlign: "center" }}>
            <span aria-hidden="true">
              <Icon name="shield" size={24} />
            </span>
            <strong style={{ display: "block", marginTop: "var(--space-2)" }}>
              {query || statusFilter !== "All"
                ? "No institutions match the filter criteria."
                : "No institutions registered yet in this tenant."}
            </strong>
            <p style={{ color: "var(--color-muted)", fontSize: "0.875rem" }}>
              Click “Register institution (Draft)” to add your first college or school.
            </p>
          </div>
        ) : (
          <div className="platform-table-wrap">
            <table className="platform-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Institution</th>
                  <th>Kind</th>
                  <th>City / State</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((institution) => (
                  <tr key={institution.institutionId || institution.institutionCode}>
                    <td>
                      <code>{institution.institutionCode}</code>
                    </td>
                    <td>
                      <strong>{institution.institutionName}</strong>
                      {institution.externalUniversityMode && (
                        <span
                          style={{
                            display: "block",
                            fontSize: "0.75rem",
                            color: "var(--color-muted)",
                          }}
                        >
                          External university mode
                        </span>
                      )}
                    </td>
                    <td>{institution.institutionType === "COLLEGE" ? "College" : "School"}</td>
                    <td>{institution.city ? `${institution.city}, ${institution.stateName || ""}` : "—"}</td>
                    <td>
                      <StateBadge status={institution.status} />
                    </td>
                    <td>
                      {!canManageInstitutions ? (
                        <span style={{ fontSize: "0.8125rem", color: "var(--color-muted)" }}>
                          {institution.status === "ACTIVE" ? "Active" : institution.status}
                        </span>
                      ) : (
                        <div style={{ display: "flex", gap: "6px", alignItems: "center", flexWrap: "wrap" }}>
                          {institution.status === "DRAFT" && (
                            <Button
                              onClick={() => handleActivate(institution)}
                              disabled={activatingId === institution.institutionId}
                              title="Vice Chancellor approval required to activate [WF-16]"
                            >
                              {activatingId === institution.institutionId ? "Activating..." : "Activate"}
                            </Button>
                          )}
                          <Button
                            variant="secondary"
                            onClick={() => setApproverModalItem(institution)}
                            title="Configure Principal's leave approver [Rule C7, PRINCIPAL_LEAVE_APPROVER_SET]"
                          >
                            Approver
                          </Button>
                          {institution.status === "ACTIVE" && (
                            <Button
                              variant="secondary"
                              onClick={() => setBlockModalItem(institution)}
                              title="Ask to block this institution's access [WF-15, RL-01]"
                            >
                              Block
                            </Button>
                          )}
                          <Button
                            variant="secondary"
                            onClick={() => setDecideModalItem(institution)}
                            title="Executive review of institution block [WF-15, INSTITUTION_BLOCK_APPROVE]"
                          >
                            Review Block Decision
                          </Button>
                          {institution.status !== "DRAFT" && institution.status !== "ACTIVE" && (
                            <span style={{ fontSize: "0.8125rem", color: "var(--color-muted)" }}>
                              {institution.status}
                            </span>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {/* Creation Modal */}
      <CreateInstitutionModal
        tenantId={tenantId}
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={(newInst) => {
          setInstitutions((prev) => [newInst, ...prev]);
          setFeedback({
            tone: "success",
            message: `Draft institution '${newInst.institutionName}' (${newInst.institutionCode}) created successfully [WF-16]!`,
          });
        }}
      />

      {/* Block Request Modal */}
      <BlockRequestModal
        tenantId={tenantId}
        institution={blockModalItem}
        onClose={() => setBlockModalItem(null)}
        onSubmitted={(message) => {
          setFeedback({
            tone: "info",
            message,
          });
          void fetchInstitutions();
        }}
      />

      {/* Block Decision Modal (Story #28: INSTITUTION_BLOCK_APPROVE) */}
      <GovernanceDecisionModal
        tenantId={tenantId}
        institution={decideModalItem}
        onClose={() => setDecideModalItem(null)}
        onDecided={(message) => {
          setFeedback({
            tone: "success",
            message,
          });
          void fetchInstitutions();
        }}
      />

      {/* Principal Leave Approver Modal (Story #45: PRINCIPAL_LEAVE_APPROVER_SET) */}
      <PrincipalLeaveApproverModal
        tenantId={tenantId}
        institution={approverModalItem}
        onClose={() => setApproverModalItem(null)}
        onSaved={(message) => {
          setFeedback({
            tone: "success",
            message,
          });
          void fetchInstitutions();
        }}
      />

      {/* VC Leave Approver Modal (Story #46: VC_LEAVE_APPROVER_SET) */}
      <ViceChancellorModal
        tenantId={tenantId}
        isOpen={vcModalOpen}
        onClose={() => setVcModalOpen(false)}
        onSaved={(message) => {
          setFeedback({
            tone: "success",
            message,
          });
          void fetchInstitutions();
        }}
      />
    </div>
  );
}

export function InstitutionUsersPage() {
  const [query, setQuery] = useState("");
  const [kind, setKind] = useState("All");
  const emptyMessage =
    query || kind !== "All"
      ? "No Northbridge University users match these filters."
      : "No Northbridge University user records are loaded in this prototype view.";
  return (
    <div className="platform-page">
      <PageHeader
        title="Users & roles"
        description="Create institution profiles, assign roles and link guardians. Profile creation and role assignment are separate steps."
        actions={
          <Button disabled title="User management API is not connected">
            Create user
          </Button>
        }
      />
      <Panel
        title="User register"
        description="University scope · the current prototype does not supply university user rows."
      >
        <div className="platform-toolbar">
          <label className="platform-search">
            <span aria-hidden="true">
              <Icon name="search" size={17} />
            </span>
            <span className="visually-hidden">Search</span>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search name, ID or email"
            />
          </label>
          <label className="platform-select-label">
            Profile type{" "}
            <select value={kind} onChange={(e) => setKind(e.target.value)}>
              <option>All</option>
              <option>Student</option>
              <option>Teacher</option>
              <option>Parent</option>
            </select>
          </label>
        </div>
        <div className="platform-admin-empty">
          <span aria-hidden="true">
            <Icon name="users" size={19} />
          </span>
          <strong>{emptyMessage}</strong>
          <p>
            When institution records are connected, this register will show only people within the
            selected university scope.
          </p>
        </div>
      </Panel>
    </div>
  );
}

const roleRows = [
  { name: "University Admin", level: "University", type: "Standard" },
  { name: "College Admin", level: "College", type: "Standard" },
  { name: "School Admin", level: "School", type: "Standard" },
  { name: "Principal", level: "School", type: "Standard" },
  { name: "Teacher", level: "School", type: "Standard" },
  { name: "Accountant", level: "Institution", type: "Standard" },
  { name: "Governing Body Approver", level: "Governance", type: "Standard" },
];

export function RolesPermissionsPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId) || "00000000-0000-0000-0000-000000000001";
  const [query, setQuery] = useState("");
  const [roles, setRoles] = useState<RoleDefinitionItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; message: string } | null>(null);

  // Custom role creation state (Story #16: ROLE_CUSTOM_MANAGE)
  const [createModalOpen, setCreateModalOpen] = useState(false);

  // Custom role retirement state (Story #16: ROLE_CUSTOM_MANAGE)
  const [retireModalItem, setRetireModalItem] = useState<RoleDefinitionItem | null>(null);

  const fetchRoles = async () => {
    setLoading(true);
    try {
      const data = await roleAssignmentService.listRoles(tenantId);
      if (data && data.length > 0) {
        setRoles(data);
      }
    } catch {
      // Keep existing roles
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchRoles();
  }, [tenantId]);

  const displayRows = useMemo(() => {
    if (roles.length > 0) {
      return roles.map((r) => ({
        id: r.roleDefinitionId ?? r.roleCode,
        raw: r,
        name: r.roleName,
        code: r.roleCode,
        level: r.roleLevel,
        type: r.roleOrigin === "STANDARD" ? "Standard" : "Custom",
        sensitive: r.sensitiveRole,
        status: r.status || "ACTIVE",
      }));
    }
    return roleRows.map((r) => ({
      id: r.name,
      raw: undefined as RoleDefinitionItem | undefined,
      name: r.name,
      code: r.name.toUpperCase().replace(/\s+/g, "_"),
      level: r.level,
      type: r.type,
      sensitive: false,
      status: "ACTIVE" as RoleDefinitionStatus,
    }));
  }, [roles]);

  const filtered = useMemo(
    () =>
      displayRows.filter((role) =>
        (role.name + role.level + (role.code || "")).toLowerCase().includes(query.toLowerCase())
      ),
    [displayRows, query]
  );

  return (
    <div className="platform-page">
      <PageHeader
        title="Roles & permissions"
        description="Review standard roles and their permission scope. Standard roles are read-only; custom roles use actions from the approved Action List [ROLE_ASSIGN / ROLE_CUSTOM_MANAGE]."
        actions={
          <Button onClick={() => setCreateModalOpen(true)}>
            + Create Custom Role
          </Button>
        }
      />

      {feedback && (
        <div
          role="alert"
          style={{
            padding: "var(--space-3) var(--space-4)",
            marginBottom: "var(--space-4)",
            borderRadius: "var(--radius-md)",
            border: `1px solid var(--eds-color-${feedback.tone === "danger" ? "danger" : "success"}-border, #e0e0e0)`,
            backgroundColor: `var(--eds-color-${feedback.tone === "danger" ? "danger" : "success"}-bg, #f9f9f9)`,
            color: `var(--eds-color-${feedback.tone === "danger" ? "danger" : "success"}-text, #333)`,
            fontSize: "0.875rem",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <span>{feedback.message}</span>
          <button
            onClick={() => setFeedback(null)}
            style={{ background: "none", border: "none", cursor: "pointer", fontWeight: "bold" }}
          >
            ×
          </button>
        </div>
      )}

      <Panel
        title="Role catalogue"
        description="Representative role names from the V4.0 prototype. This is not the complete 71-role catalogue. Standard roles are system-defined; custom roles are tenant-managed [ROLE_CUSTOM_MANAGE]."
      >
        {loading && <p className="platform-empty-note">Loading roles from catalogue...</p>}
        <div className="platform-toolbar">
          <label className="platform-search">
            <span aria-hidden="true">
              <Icon name="search" size={17} />
            </span>
            <span className="visually-hidden">Search roles</span>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search role or level"
            />
          </label>
        </div>
        <div className="platform-table-wrap">
          <table className="platform-table">
            <thead>
              <tr>
                <th>Role</th>
                <th>Code</th>
                <th>Level</th>
                <th>Type</th>
                <th>Sensitivity [MX-04]</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((role) => (
                <tr key={role.code || role.name}>
                  <td>
                    <strong>{role.name}</strong>
                  </td>
                  <td>
                    <code>{role.code}</code>
                  </td>
                  <td>{role.level}</td>
                  <td>{role.type}</td>
                  <td>
                    {role.sensitive ? (
                      <Badge tone="warning">Sensitive (Admin only)</Badge>
                    ) : (
                      <Badge tone="neutral">Non-sensitive [MX-04]</Badge>
                    )}
                  </td>
                  <td>
                    <Badge tone={role.status === "ACTIVE" ? "success" : "neutral"}>
                      {role.status}
                    </Badge>
                  </td>
                  <td>
                    {role.type === "Custom" && role.status === "ACTIVE" && role.raw ? (
                      <Button
                        variant="secondary"
                        style={{ padding: "2px 8px", fontSize: "0.8rem" }}
                        onClick={() => setRetireModalItem(role.raw!)}
                      >
                        Retire
                      </Button>
                    ) : (
                      <span style={{ fontSize: "0.8rem", color: "var(--color-muted)" }}>
                        {role.type === "Standard" ? "Read-only" : "Retired"}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>

      {/* Create Custom Role Modal (Story #16: ROLE_CUSTOM_MANAGE) */}
      <CreateCustomRoleModal
        tenantId={tenantId}
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        onCreated={(newRole, inputCode) => {
          setRoles((prev) => [newRole, ...prev]);
          setFeedback({
            tone: "success",
            message: `Custom role ${inputCode || newRole.roleCode} created successfully [Rule 17]!`,
          });
          fetchRoles();
        }}
      />

      {/* Retire Custom Role Modal (Story #16: ROLE_CUSTOM_MANAGE) */}
      <RetireCustomRoleModal
        tenantId={tenantId}
        role={retireModalItem}
        onClose={() => setRetireModalItem(null)}
        onRetired={(retired) => {
          setRoles((prev) =>
            prev.map((r) => (r.roleDefinitionId === retired.roleDefinitionId ? retired : r))
          );
          setFeedback({
            tone: "success",
            message: `Custom role ${retired.roleName} retired. Existing assignments remain historical per Rule 17.`,
          });
          fetchRoles();
        }}
      />

      <div className="platform-policy-callout">
        <span aria-hidden="true">
          <Icon name="info" size={15} />
        </span>
        <div>
          <strong>Role assignment & delegation policy (Rule MX-04)</strong>
          <p>
            Standard roles are templates; assigning a role to a person happens per user account and is
            scoped to an institution or department. Under statutory Rule MX-04, IT Administrators and
            School IT Support can assign Non-sensitive operational roles (e.g. Office Staff, Library,
            Security, Transport). Leadership, academic faculty, financial, HR, and examination roles
            require full Administrative assignment authority.
          </p>
        </div>
      </div>
    </div>
  );
}

export function MissingRolesPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId) || "00000000-0000-0000-0000-000000000001";

  const [entries, setEntries] = useState<RolldownMapEntry[]>([]);
  const [overrides, setOverrides] = useState<RolldownOverride[]>([]);
  const [loading, setLoading] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; message: string } | null>(null);

  // Set override modal state (Story #24: ROLLDOWN_OVERRIDE_SET)
  const [modalOpen, setModalOpen] = useState(false);
  const [overrideTarget, setOverrideTarget] = useState<{ missingRoleCode: string; permissionKind: string } | null>(null);

  const fetchRolldown = async () => {
    setLoading(true);
    try {
      const [mapData, overrideData] = await Promise.all([
        rolldownService.getRolldownMap(tenantId),
        rolldownService.getOverrides(tenantId),
      ]);
      setEntries(mapData);
      setOverrides(overrideData);
    } catch (err) {
      setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchRolldown();
  }, [tenantId]);

  const handleRevokeOverride = async (overrideId: string) => {
    try {
      await rolldownService.revokeOverride(tenantId, overrideId);
      setFeedback({ tone: "success", message: "Rolldown override revoked. Reverted to default roll-down order." });
      fetchRolldown();
    } catch (err: unknown) {
      setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
    }
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="Missing roles & roll-down map"
        description="V4.0 role roll-down (Decision V4-PD-01 / Actions #23 & #24): see who handles approvals and operational tasks when a role has no active holder."
        actions={
          <Button onClick={() => setModalOpen(true)}>
            + Set Rolldown Override
          </Button>
        }
      />

      {feedback && (
        <div
          role="alert"
          style={{
            padding: "var(--space-3) var(--space-4)",
            marginBottom: "var(--space-4)",
            borderRadius: "var(--radius-md)",
            border: `1px solid var(--eds-color-${feedback.tone === "danger" ? "danger" : "success"}-border, #e0e0e0)`,
            backgroundColor: `var(--eds-color-${feedback.tone === "danger" ? "danger" : "success"}-bg, #f9f9f9)`,
            color: `var(--eds-color-${feedback.tone === "danger" ? "danger" : "success"}-text, #333)`,
            fontSize: "0.875rem",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <span>{feedback.message}</span>
          <button
            onClick={() => setFeedback(null)}
            style={{ background: "none", border: "none", cursor: "pointer", fontWeight: "bold" }}
          >
            ×
          </button>
        </div>
      )}

      <div className="platform-policy-callout">
        <span aria-hidden="true">
          <Icon name="info" size={15} />
        </span>
        <div>
          <strong>Decision V4-PD-01</strong>
          <p>
            When a role is missing, approvals go to the head of the next level down. Operational work goes
            to that level's administrator. Approval rights can never be withheld (Guard RD-G2). Changes require an authorized role (Management, VC, or Admin) and a statutory justification.
          </p>
        </div>
      </div>

      <Panel
        title="Active roll-down resolution map (ROLLDOWN_VIEW / Action #23)"
        description="Ordered stand-in preferences evaluated dynamically per context (University wide, College, School)."
      >
        {loading && <p className="platform-empty-note">Loading roll-down resolution map...</p>}
        <div className="platform-table-wrap">
          <table className="platform-table">
            <thead>
              <tr>
                <th>Missing Role</th>
                <th>Context</th>
                <th>Kind</th>
                <th>Default Stand-In</th>
                <th>Resolution Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {entries.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ textAlign: "center", padding: "var(--space-4)", color: "var(--color-muted)" }}>
                    No roll-down map rules loaded.
                  </td>
                </tr>
              ) : (
                entries.map((e, idx) => (
                  <tr key={`${e.missingRoleCode}-${e.permissionKind}-${idx}`}>
                    <td><code>{e.missingRoleCode}</code></td>
                    <td>{e.context}</td>
                    <td>
                      <Badge tone={e.permissionKind === "APPROVE" ? "warning" : "neutral"}>
                        {e.permissionKind}
                      </Badge>
                    </td>
                    <td>
                      <code>{e.standInRoleCode}</code> (Pref #{e.preference})
                    </td>
                    <td>
                      {e.hasOverride ? (
                        <Badge tone="danger">
                          Active Override: {e.overrideMode} {e.overrideTargetRoleCode ? `-> ${e.overrideTargetRoleCode}` : ""}
                        </Badge>
                      ) : (
                        <Badge tone="success">Default order</Badge>
                      )}
                    </td>
                    <td>
                      {e.hasOverride ? (
                        <Button
                          variant="secondary"
                          style={{ padding: "2px 8px", fontSize: "0.8rem" }}
                          onClick={() => {
                            const ov = overrides.find(
                              (o) => o.missingRoleCode === e.missingRoleCode && o.permissionKind === e.permissionKind
                            );
                            if (ov) handleRevokeOverride(ov.roleRolldownOverrideId);
                          }}
                        >
                          Revoke Override
                        </Button>
                      ) : (
                        <Button
                          variant="secondary"
                          style={{ padding: "2px 8px", fontSize: "0.8rem" }}
                          onClick={() => {
                            setOverrideTarget({
                              missingRoleCode: e.missingRoleCode,
                              permissionKind: e.permissionKind,
                            });
                            setModalOpen(true);
                          }}
                        >
                          Set Override
                        </Button>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Panel>

      {/* Set Rolldown Override Modal (Story #24: ROLLDOWN_OVERRIDE_SET) */}
      <RolldownOverrideModal
        tenantId={tenantId}
        isOpen={modalOpen}
        initialMissingRoleCode={overrideTarget?.missingRoleCode}
        initialPermissionKind={overrideTarget?.permissionKind}
        onClose={() => {
          setModalOpen(false);
          setOverrideTarget(null);
        }}
        onCreated={(override) => {
          setOverrides((prev) => [override, ...prev]);
          setFeedback({
            tone: "success",
            message: `Rolldown override established for ${override.missingRoleCode} [Rule 24]!`,
          });
        }}
      />

      <Panel
        title="Default roll-down rules"
        description="Policy view from the prototype; no live role-holder assignments are loaded."
      >
        <div className="platform-roll-grid">
          <article>
            <Kicker>APPROVALS</Kicker>
            <h3>Next-level leader</h3>
            <p>
              Approval work moves to the head of the next level down. Approval rights cannot be
              withheld.
            </p>
          </article>
          <article>
            <Kicker>OTHER WORK</Kicker>
            <h3>Next-level administrator</h3>
            <p>
              Other work moves to the next level's administrator. Where permitted, it may be withheld
              until the role is filled.
            </p>
          </article>
        </div>
      </Panel>
      <Panel title="Rules that cannot be overridden">
        <ul className="platform-rule-list">
          <li>Nobody approves their own request.</li>
          <li>A preparer never approves their own work.</li>
          <li>Students, parents, applicants and platform staff never act for an institution role.</li>
          <li>
            Every action taken through roll-down must show “Acting for …” and be recorded in the audit
            log.
          </li>
        </ul>
      </Panel>
    </div>
  );
}
