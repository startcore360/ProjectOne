import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Button,
  Notice,
  EmptyState,
  Kicker,
  Panel,
  StatusBadge as StateBadge,
  Badge,
  PageHeader,
  SkeletonTable,
} from "../../../design-system/components";
import { Icon } from "../../../components/ui/Icon";
import { ErrorState } from "../../../components/feedback/ErrorState";
import { useAppSelector } from "../../../store/hooks";
import { useTenantAccess } from "../../profile/hooks/useTenantAccess";
import { InstitutionIdFormatsModal } from "../components/institutions/InstitutionIdFormatsModal";
import { getApiErrorMessage } from "../../../services/apiHelper";
import {
  institutionService,
  type InstitutionItem,
} from "../api/institutionService";
import {
  roleAssignmentService,
  type RoleDefinitionItem,
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

function formatCodeLabel(value: string): string {
  const known: Record<string, string> = {
    VICE_CHANCELLOR: "Vice Chancellor",
    GOVERNING_BODY_APPROVER: "Governing Body Approver",
    DATA_PROTECTION_CONTACT: "Data Protection Contact",
    DATA_PROTECTION_OFFICER: "Data Protection Officer",
    UNIVERSITY_WIDE: "University-wide",
  };
  return known[value] ?? value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function TenantContextNotice() {
  return (
    <ErrorState
      title="Institution context unavailable"
      message="This page requires a signed-in account linked to a tenant. No tenant-scoped request has been sent."
    />
  );
}

export function InstitutionsPage() {
  const user = useAppSelector((state) => state.auth.user);
  const tenantId = user?.tenantId ?? "";
  const { can } = useTenantAccess();
  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("All");
  const [modalOpen, setModalOpen] = useState(false);
  const [activatingId, setActivatingId] = useState<string | null>(null);
  const [blockModalItem, setBlockModalItem] = useState<InstitutionItem | null>(null);
  const [idFormatsModalItem, setIdFormatsModalItem] = useState<InstitutionItem | null>(null);
  const [decideModalItem, setDecideModalItem] = useState<InstitutionItem | null>(null);
  const [feedback, setFeedback] = useState<{
    tone: "success" | "danger" | "info";
    message: string;
  } | null>(null);

  const isNonAdminRole =
    user?.roleCode === "SCHOOL_ADMIN" ||
    user?.roleCode === "COLLEGE_ADMIN" ||
    user?.roleCode === "STUDENT_COLLEGE" ||
    user?.roleCode === "STUDENT_SCHOOL" ||
    user?.roleCode === "PARENT_COLLEGE" ||
    user?.roleCode === "PARENT_SCHOOL" ||
    user?.roleCode === "CLASS_TEACHER" ||
    user?.roleCode === "SUBJECT_TEACHER";
  const canManageInstitutions =
    can("INSTITUTION_CREATE_DRAFT") ||
    can("INSTITUTION_ACTIVATE") ||
    (!isNonAdminRole && (!user?.roleCode || user?.roleCode === "UNIVERSITY_ADMIN"));

  // Leave Approver state (Stories #45 & #46)
  const [goLiveCheck, setGoLiveCheck] = useState<GoLiveCheckResponse | null>(null);
  const [goLiveCheckError, setGoLiveCheckError] = useState("");
  const [approverModalItem, setApproverModalItem] = useState<InstitutionItem | null>(null);
  const [vcModalOpen, setVcModalOpen] = useState(false);

  const fetchInstitutions = useCallback(async () => {
    if (!tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await institutionService.listInstitutions(tenantId);
      setInstitutions(data.items);
      setGoLiveCheckError("");
      setGoLiveCheck(null);
      try {
        const check = await leaveApproverService.getGoLiveCheck(tenantId);
        setGoLiveCheck(check);
      } catch {
        setGoLiveCheckError("Go-live readiness could not be verified. Institution activation remains subject to server-side checks.");
      }
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    if (tenantId) void fetchInstitutions();
  }, [fetchInstitutions, tenantId]);

  const handleActivate = async (item: InstitutionItem) => {
    if (!tenantId) return;
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
          message: `Activation blocked: a Principal's leave approver is not configured for '${item.institutionName}'. Configure the leave approver before going live.`,
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

  if (!tenantId) return <TenantContextNotice />;

  return (
    <div className="app-page">
      <PageHeader
        title="Institutions"
        description="Register colleges and schools within this tenant. New institutions begin as drafts and require executive approval before going live."
        actions={
          canManageInstitutions ? (
            <div style={{ display: "flex", gap: "var(--space-2)" }}>
              <Button
                variant="secondary"
                onClick={() => setVcModalOpen(true)}
                title="Configure the Vice-Chancellor leave approver"
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
        <Notice tone="info" style={{ marginBottom: "var(--space-4)" }}>
          <strong>Read-only institution register:</strong> Institution creation and executive approvals are restricted to University Administrators. As a {user?.roleName || "College or School Administrator"}, your assigned institutions are listed below.
        </Notice>
      )}

      {goLiveCheckError && (
        <Notice tone="danger">
          {goLiveCheckError} <Button variant="secondary" size="sm" onClick={() => void fetchInstitutions()}>Retry readiness check</Button>
        </Notice>
      )}

      {goLiveCheck && !goLiveCheck.ready && (
        <Notice tone="warning" style={{ marginBottom: "var(--space-4)" }}>
          <strong>Go-live readiness warning:</strong>
          <p style={{ margin: "4px 0" }}>
            The following statutory leave approver settings are missing before institutions/tenant can go live:
          </p>
          <ul style={{ paddingLeft: "var(--space-4)", margin: "4px 0" }}>
            {goLiveCheck.blockers.map((b, idx) => (
              <li key={idx}>
                {b.blocker === "VC_LEAVE_APPROVER_MISSING"
                  ? "Vice-Chancellor leave approver has not been configured for this university."
                  : `Principal leave approver has not been configured for institution ${b.institutionId || "unknown"}.`}
              </li>
            ))}
          </ul>
        </Notice>
      )}

      {feedback && (
        <Notice tone={feedback.tone} style={{ marginBottom: "var(--space-4)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <span>{feedback.message}</span>
          <button
            onClick={() => setFeedback(null)}
            style={{ background: "none", border: "none", cursor: "pointer", fontWeight: "bold", color: "inherit" }}
            aria-label="Dismiss"
          >
            ×
          </button>
        </Notice>
      )}

      {error && (
        <ErrorState
          title="Unable to load institutions"
          message={error}
          action={<Button variant="secondary" onClick={() => void fetchInstitutions()}>Retry</Button>}
        />
      )}

      <Panel title="Institution register" description="University scope · Plan limit enforced [WF-14]">
        <div className="app-toolbar" style={{ display: "flex", gap: "var(--space-3)", marginBottom: "var(--space-3)" }}>
          <label className="app-search" style={{ flex: 1 }}>
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
          <label className="app-select-label">
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
          <>
            <div className="sr-only" role="status">Loading institutions...</div>
            <SkeletonTable rows={5} columns={6} />
          </>
        ) : error ? null : filtered.length === 0 ? (
          <div className="app-admin-empty" style={{ padding: "var(--space-6)", textAlign: "center" }}>
            <span aria-hidden="true">
              <Icon name="shield" size={24} />
            </span>
            <strong style={{ display: "block", marginTop: "var(--space-2)" }}>
              {query || statusFilter !== "All"
                ? "No institutions match the filter criteria."
                : "No institutions registered yet in this tenant."}
            </strong>
            <p style={{ color: "var(--color-text-muted)", fontSize: "0.875rem" }}>
              Click “Register institution (Draft)” to add your first college or school.
            </p>
          </div>
        ) : (
          <div className="app-table-wrap">
            <table className="app-table">
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
                            color: "var(--color-text-muted)",
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
                        <span style={{ fontSize: "0.8125rem", color: "var(--color-text-muted)" }}>
                          {institution.status === "ACTIVE" ? "Active" : institution.status}
                        </span>
                      ) : (
                        <div style={{ display: "flex", gap: "6px", alignItems: "center", flexWrap: "wrap" }}>
                          {institution.status === "DRAFT" && (
                            <Button
                              onClick={() => handleActivate(institution)}
                              disabled={activatingId === institution.institutionId}
                              title="Vice Chancellor approval required to activate "
                            >
                              {activatingId === institution.institutionId ? "Activating..." : "Activate"}
                            </Button>
                          )}
                          <Button
                            variant="secondary"
                            onClick={() => setIdFormatsModalItem(institution)}
                            title="Configure institution student and staff ID formats"
                          >
                            ID formats
                          </Button>
                          <Button
                            variant="secondary"
                            onClick={() => setApproverModalItem(institution)}
                            title="Configure Principal's leave approver "
                          >
                            Approver
                          </Button>
                          {institution.status === "ACTIVE" && (
                            <Button
                              variant="secondary"
                              onClick={() => setBlockModalItem(institution)}
                              title="Ask to block this institution's access "
                            >
                              Block
                            </Button>
                          )}
                          <Button
                            variant="secondary"
                            onClick={() => setDecideModalItem(institution)}
                            title="Executive review of institution block "
                          >
                            Review Block Decision
                          </Button>
                          {institution.status !== "DRAFT" && institution.status !== "ACTIVE" && (
                            <span style={{ fontSize: "0.8125rem", color: "var(--color-text-muted)" }}>
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


      <InstitutionIdFormatsModal
        tenantId={tenantId}
        institution={idFormatsModalItem}
        onClose={() => setIdFormatsModalItem(null)}
        onSaved={() => setFeedback({
          tone: "success",
          message: "Institution ID formats saved. Student formats lock after the first generated student code; staff formats lock after the first generated staff code.",
        })}
      />

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
  return (
    <div className="app-page">
      <PageHeader
        title="Users & roles"
        description="Create institution profiles, assign roles and link guardians. Profile creation and role assignment are separate steps."
      />
      <ErrorState
        title="Not yet available"
        message="University-scope user management is not connected in this version. Use the tenant-wide Users & Roles screen to manage user accounts."
      />
    </div>
  );
}

export function RolesPermissionsPage() {
  const user = useAppSelector((state) => state.auth.user);
  const tenantId = user?.tenantId ?? "";
  const { can } = useTenantAccess();
  const canManageCustomRole = can("ROLE_CUSTOM_MANAGE") || !user?.roleCode || user?.roleCode === "UNIVERSITY_ADMIN";
  const [query, setQuery] = useState("");
  const [roles, setRoles] = useState<RoleDefinitionItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; message: string } | null>(null);

  // Custom role creation state (Story #16: ROLE_CUSTOM_MANAGE)
  const [createModalOpen, setCreateModalOpen] = useState(false);

  // Custom role retirement state (Story #16: ROLE_CUSTOM_MANAGE)
  const [retireModalItem, setRetireModalItem] = useState<RoleDefinitionItem | null>(null);

  const fetchRoles = useCallback(async () => {
    if (!tenantId) return;
    setLoading(true);
    setLoadError("");
    try {
      const data = await roleAssignmentService.listRoles(tenantId);
      setRoles(Array.isArray(data) ? data : []);
    } catch (err) {
      setRoles([]);
      setLoadError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    if (tenantId) void fetchRoles();
  }, [fetchRoles, tenantId]);

  const displayRows = useMemo(() => roles.map((r) => ({
    id: r.roleDefinitionId ?? r.roleCode,
    raw: r,
    name: r.roleName,
    code: r.roleCode,
    level: r.roleLevel,
    type: r.roleOrigin === "STANDARD" ? "Standard" : "Custom",
    sensitive: r.sensitiveRole,
    status: r.status || "ACTIVE",
  })), [roles]);

  const filtered = useMemo(
    () =>
      displayRows.filter((role) =>
        (role.name + role.level + (role.code || "")).toLowerCase().includes(query.toLowerCase())
      ),
    [displayRows, query]
  );

  if (!tenantId) return <TenantContextNotice />;

  return (
    <div className="app-page">
      <PageHeader
        title="Roles & permissions"
        description="Review standard roles and their permission scope. Standard roles are read-only; custom roles can be managed by authorized administrators."
        actions={
          canManageCustomRole ? (
            <Button onClick={() => setCreateModalOpen(true)}>
              + Create Custom Role
            </Button>
          ) : null
        }
      />

      {feedback && (
        <Notice tone={feedback.tone} style={{ marginBottom: "var(--space-4)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <span>{feedback.message}</span>
          <button
            onClick={() => setFeedback(null)}
            style={{ background: "none", border: "none", cursor: "pointer", fontWeight: "bold", color: "inherit" }}
            aria-label="Dismiss"
          >
            ×
          </button>
        </Notice>
      )}

      <Panel
        title="Role catalogue"
        description="Roles returned by the tenant role catalogue. Standard roles are system-defined; custom roles are tenant-managed."
      >
        {loading && (
          <>
            <p className="app-empty-note sr-only" role="status">Loading roles from catalogue...</p>
            <SkeletonTable rows={5} columns={6} />
          </>
        )}
        {loadError && !loading && (
          <Notice tone="danger">
            {loadError} <Button variant="secondary" size="sm" onClick={() => void fetchRoles()}>Retry</Button>
          </Notice>
        )}
        {!loading && !loadError && roles.length === 0 && (
          <p className="app-empty-note">No roles were returned by the tenant role catalogue.</p>
        )}
        <div className="app-toolbar">
          <label className="app-search">
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
        {!loading && !loadError && roles.length > 0 && <div className="app-table-wrap">
          <table className="app-table">
            <thead>
              <tr>
                <th>Role</th>
                <th>Level</th>
                <th>Type</th>
                <th>Sensitivity</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((role) => (
                <tr key={role.code || role.name}>
                  <td><strong>{role.name}</strong></td>
                  <td>{role.level}</td>
                  <td>{role.type}</td>
                  <td>
                    {role.sensitive ? (
                      <Badge tone="warning">Sensitive (Admin only)</Badge>
                    ) : (
                      <Badge tone="neutral">Non-sensitive</Badge>
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
                      <span style={{ fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
                        {role.type === "Standard" ? "Read-only" : "Retired"}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>}
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
            message: `Custom role ${inputCode || newRole.roleCode} created successfully.`,
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
            message: `Custom role ${retired.roleName} retired. Existing assignments are retained for historical reference.`,
          });
          fetchRoles();
        }}
      />

      <div className="app-callout">
        <span aria-hidden="true">
          <Icon name="info" size={15} />
        </span>
        <div>
          <strong>Role assignment and delegation policy</strong>
          <p>
            Standard roles are templates; assigning a role to a person happens per user account and is
            scoped to an institution or department. IT administrators and School IT support can assign non-sensitive operational roles (e.g. Office Staff, Library,
            Security, Transport). Leadership, academic faculty, financial, HR, and examination roles
            require full Administrative assignment authority.
          </p>
        </div>
      </div>
    </div>
  );
}

export function MissingRolesPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId) ?? "";
  const { can: canTenantAction } = useTenantAccess();
  const canSetOverride = canTenantAction("ROLLDOWN_OVERRIDE_SET");

  const [entries, setEntries] = useState<RolldownMapEntry[]>([]);
  const [overrides, setOverrides] = useState<RolldownOverride[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; message: string } | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [overrideTarget, setOverrideTarget] = useState<{ missingRoleCode: string; permissionKind: string } | null>(null);

  const fetchRolldown = useCallback(async () => {
    if (!tenantId) return;
    setLoading(true);
    setLoadError("");
    try {
      const [mapData, overrideData] = await Promise.all([
        rolldownService.getRolldownMap(tenantId),
        rolldownService.getOverrides(tenantId),
      ]);
      setEntries(Array.isArray(mapData) ? mapData : []);
      setOverrides(Array.isArray(overrideData) ? overrideData : []);
    } catch {
      setLoadError("We could not load the role coverage rules. Check your connection and try again.");
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    if (tenantId) void fetchRolldown();
  }, [fetchRolldown, tenantId]);

  const handleRevokeOverride = async (overrideId: string) => {
    if (!tenantId) return;
    try {
      await rolldownService.revokeOverride(tenantId, overrideId);
      await fetchRolldown();
      setFeedback({ tone: "success", message: "Override removed. The default role coverage order is now active." });
    } catch {
      setFeedback({ tone: "danger", message: "The override could not be removed. Please try again." });
    }
  };

  if (!tenantId) return <TenantContextNotice />;

  return (
    <div className="app-page">
      <PageHeader
        title="Role coverage"
        description="See which roles take responsibility when an institution role has no active holder."
        actions={canSetOverride ? (
          <Button onClick={() => setModalOpen(true)}>Set role coverage override</Button>
        ) : null}
      />

      {feedback && (
        <Notice tone={feedback.tone}>
          <div className="app-feedback-row">
            <span>{feedback.message}</span>
            <Button variant="tertiary" size="sm" onClick={() => setFeedback(null)}>Dismiss</Button>
          </div>
        </Notice>
      )}

      <div className="app-callout">
        <span aria-hidden="true"><Icon name="info" size={15} /></span>
        <div>
          <strong>How role coverage works</strong>
          <p>
            Approval work moves to the leader at the next level down. Other operational work moves to
            that level's administrator. Approval rights cannot be withheld. Authorized administrators
            can change eligible operational assignments with a documented reason.
          </p>
        </div>
      </div>

      {loadError && (
        <Notice tone="danger">
          {loadError} <Button variant="secondary" size="sm" onClick={() => void fetchRolldown()}>Retry</Button>
        </Notice>
      )}

      <Panel
        title="Role coverage by institution level"
        description="Default stand-in roles used when a role has no active holder."
      >
        {loading && (
          <>
            <p className="app-empty-note sr-only" role="status">Loading role coverage rules…</p>
            <SkeletonTable rows={5} columns={6} />
          </>
        )}
        {!loading && !loadError && entries.length === 0 && (
          <EmptyState
            icon="shield"
            title="No default role coverage rules returned"
            description="The service returned no rules for this tenant. Confirm that the V4.0 role roll-down reference data has been applied."
          />
        )}
        {!loading && !loadError && entries.length > 0 && (
          <div className="app-table-wrap">
            <table className="app-table">
              <thead>
                <tr>
                  <th>Missing role</th>
                  <th>Context</th>
                  <th>Work type</th>
                  <th>Default stand-in</th>
                  <th>Resolution</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry, index) => {
                  const override = overrides.find((item) =>
                    item.missingRoleCode === entry.missingRoleCode && item.permissionKind === entry.permissionKind
                  );
                  return (
                    <tr key={`${entry.missingRoleCode}-${entry.permissionKind}-${index}`}>
                      <td>{formatCodeLabel(entry.missingRoleCode)}</td>
                      <td>{formatCodeLabel(entry.context)}</td>
                      <td>
                        <Badge tone={entry.permissionKind === "APPROVE" ? "warning" : "neutral"}>
                          {entry.permissionKind === "APPROVE" ? "Approval" : formatCodeLabel(entry.permissionKind)}
                        </Badge>
                      </td>
                      <td>{formatCodeLabel(entry.standInRoleCode)} <span className="muted">· Priority {entry.preference}</span></td>
                      <td>
                        {entry.hasOverride ? (
                          <Badge tone="warning">
                            {entry.overrideMode === "WITHHOLD"
                              ? "Operational work withheld"
                              : `Assigned to ${formatCodeLabel(entry.overrideTargetRoleCode ?? "")}`}
                          </Badge>
                        ) : <Badge tone="success">Default order</Badge>}
                      </td>
                      <td>
                        {canSetOverride ? (entry.hasOverride ? (
                          <Button variant="secondary" size="sm" disabled={!override} onClick={() => override && void handleRevokeOverride(override.roleRolldownOverrideId)}>
                            Remove override
                          </Button>
                        ) : (
                          <Button variant="secondary" size="sm" onClick={() => {
                            setOverrideTarget({ missingRoleCode: entry.missingRoleCode, permissionKind: entry.permissionKind });
                            setModalOpen(true);
                          }}>Set override</Button>
                        )) : <span className="muted">View only</span>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      <RolldownOverrideModal
        tenantId={tenantId}
        isOpen={modalOpen}
        initialMissingRoleCode={overrideTarget?.missingRoleCode}
        initialPermissionKind={overrideTarget?.permissionKind}
        onClose={() => {
          setModalOpen(false);
          setOverrideTarget(null);
        }}
        onCreated={async (override) => {
          setModalOpen(false);
          setOverrideTarget(null);
          await fetchRolldown();
          setFeedback({ tone: "success", message: `Role coverage override saved for ${formatCodeLabel(override.missingRoleCode)}.` });
        }}
      />

      <Panel
        title="Default role coverage rules"
        description="Policy summary. Live role-holder assignments are not included in this view."
      >
        <div className="app-roll-grid">
          <article>
            <Kicker>APPROVALS</Kicker>
            <h3>Next-level leader</h3>
            <p>Approval work moves to the head of the next level down. Approval rights cannot be withheld.</p>
          </article>
          <article>
            <Kicker>OTHER WORK</Kicker>
            <h3>Next-level administrator</h3>
            <p>Other work moves to the next level's administrator. Where permitted, it may be withheld until the role is filled.</p>
          </article>
        </div>
      </Panel>
      <Panel title="Rules that cannot be overridden">
        <ul className="app-rule-list">
          <li>Nobody approves their own request.</li>
          <li>A preparer never approves their own work.</li>
          <li>Students, parents, applicants and platform staff never act for an institution role.</li>
          <li>Every action taken through role coverage must show “Acting for…” and be recorded in the audit log.</li>
        </ul>
      </Panel>
    </div>
  );
}
