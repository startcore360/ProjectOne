import { useEffect, useMemo, useState, type FormEvent } from "react";
import {
  Button,
  MetricCard,
  Panel,
  StatusBadge,
  DataTable,
  SearchBox,
} from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import { PageHeader } from "../../../../components/layout/PageHeader";
import type { Tenant } from "../common/types";
import {
  platformTenantService,
  type PlanItem,
  type TenantType,
  type IsolationMode,
} from "../../api/platformTenantService";
import { usePlatformAccess } from "../../platformAccess";
import { CreateTenantModal, type CreateTenantDraft } from "./CreateTenantModal";
import { TenantCredentialsNotice, type CreatedTenantCredentials } from "./TenantCredentialsNotice";
import { ActivateTenantModal } from "./ActivateTenantModal";
import { SuspendTenantModal, ArchiveTenantModal } from "./TenantLifecycleModals";
import { TenantSettingsModal, type TenantSettingsDraft } from "./TenantSettingsModal";
import { TenantDataProtectionModal } from "./TenantDataProtectionModal";
import { TenantUsageModal } from "./TenantUsageModal";
import { tenantLoginAddress } from "./tenantAddress";

export function TenantsView() {
  const { can } = usePlatformAccess();
  const canActivate = can("TENANT_ACTIVATE");
  const canManageLifecycle = can("TENANT_SUSPEND_ARCHIVE");
  const canEditSettings = can("TENANT_SETTINGS");
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(false);
  const [plans, setPlans] = useState<PlanItem[]>([]);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("All");
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [message, setMessage] = useState("");
  const [createdCredentials, setCreatedCredentials] = useState<CreatedTenantCredentials | null>(null);

  const [showActivate, setShowActivate] = useState(false);
  const [selectedTenant, setSelectedTenant] = useState<Tenant | null>(null);
  const [paymentRef, setPaymentRef] = useState("");
  const [activateNote, setActivateNote] = useState("");
  const [activating, setActivating] = useState(false);
  const [activateError, setActivateError] = useState("");

  const [showSuspend, setShowSuspend] = useState(false);
  const [suspendReason, setSuspendReason] = useState("");
  const [suspending, setSuspending] = useState(false);

  const [showArchive, setShowArchive] = useState(false);
  const [archiveReason, setArchiveReason] = useState("");
  const [archiving, setArchiving] = useState(false);

  const [showSettings, setShowSettings] = useState(false);
  const [showDataProtection, setShowDataProtection] = useState(false);
  const [showUsage, setShowUsage] = useState(false);
  const [settingsDraft, setSettingsDraft] = useState<TenantSettingsDraft>({
    timeZone: "Asia/Kolkata",
    dataRegion: "IN",
    analyticsOptOut: false,
    customDomain: "",
    userLimit: 1000,
    storageLimitGb: 50,
  });
  const [loadingSettings, setLoadingSettings] = useState(false);
  const [savingSettings, setSavingSettings] = useState(false);
  const [settingsError, setSettingsError] = useState("");

  const [draft, setDraft] = useState<CreateTenantDraft>({
    tenantName: "",
    tenantType: "STANDALONE_SCHOOL" as TenantType,
    planId: "",
    subdomain: "",
    isolationMode: "SHARED" as IsolationMode,
    firstAdminName: "",
    firstAdminEmail: "",
    firstAdminMobile: "",
    firstAdminNationalIdLast4: "",
  });

  const loadTenants = () => {
    const req = platformTenantService.getTenants();
    if (req && typeof req.then === "function") {
      setLoading(true);
      req
        .then((res) => {
          if (res?.data?.items) {
            const mapped: Tenant[] = res.data.items.map((t) => ({
              id: t.tenantId,
              name: t.tenantName,
              type:
                t.tenantType === "STANDALONE_SCHOOL"
                  ? "School"
                  : t.tenantType === "STANDALONE_COLLEGE"
                  ? "College"
                  : "University",
              plan: t.planName,
              status: t.state === "ACTIVE" ? "Active" : t.state === "TRIAL" ? "Trial" : "Suspended",
              gates: t.state === "TRIAL" ? 1 : 0,
              updated: t.createdAt ? new Date(t.createdAt).toLocaleDateString() : "Just now",
            }));
            setTenants(mapped);
          } else {
            setTenants([]);
          }
        })
        .catch(() => setTenants([]))
        .finally(() => setLoading(false));
    }
  };

  useEffect(() => {
    loadTenants();
    const planReq = platformTenantService.getPlans();
    if (planReq && typeof planReq.then === "function") {
      planReq
        .then((res) => {
          if (res?.data && res.data.length > 0) {
            setPlans(res.data);
            setDraft((prev) => ({ ...prev, planId: prev.planId || res.data[0].planId }));
          }
        })
        .catch(() => {});
    }
  }, []);

  const handleNameChange = (name: string) => {
    const slug = name
      .toLowerCase()
      .replace(/[^a-z0-9]/g, "-")
      .replace(/-+/g, "-")
      .replace(/^-|-$/g, "");
    setDraft((prev) => ({
      ...prev,
      tenantName: name,
      subdomain:
        prev.subdomain === "" ||
        prev.subdomain ===
          prev.tenantName
            .toLowerCase()
            .replace(/[^a-z0-9]/g, "-")
            .replace(/-+/g, "-")
            .replace(/^-|-$/g, "")
          ? slug
          : prev.subdomain,
    }));
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setCreating(true);
    setCreateError("");
    setMessage("");

    try {
      const response = await platformTenantService.createTenant({
        tenantName: draft.tenantName,
        tenantType: draft.tenantType,
        planId: draft.planId || (plans[0]?.planId ?? "11111111-1111-1111-1111-111111111101"),
        subdomain: draft.subdomain,
        isolationMode: draft.isolationMode,
        firstAdminName: draft.firstAdminName,
        firstAdminEmail: draft.firstAdminEmail,
        firstAdminMobile: draft.firstAdminMobile ? draft.firstAdminMobile : undefined,
        firstAdminNationalIdLast4: draft.firstAdminNationalIdLast4 ? draft.firstAdminNationalIdLast4 : undefined,
      });

      const newTenant = response.data;
      const loginAddress = tenantLoginAddress(newTenant.subdomain, window.location);
      setMessage(`Tenant "${newTenant.tenantName}" created successfully in TRIAL state.`);
      setCreatedCredentials(
        newTenant.firstAdminTemporaryPassword
          ? {
              tenantName: newTenant.tenantName,
              adminEmail: newTenant.firstAdminEmail ?? draft.firstAdminEmail,
              loginAddress,
              temporaryPassword: newTenant.firstAdminTemporaryPassword,
            }
          : null,
      );
      setShowCreate(false);
      setDraft({
        tenantName: "",
        tenantType: "STANDALONE_SCHOOL",
        planId: plans[0]?.planId ?? "",
        subdomain: "",
        isolationMode: "SHARED",
        firstAdminName: "",
        firstAdminEmail: "",
        firstAdminMobile: "",
        firstAdminNationalIdLast4: "",
      });
      loadTenants();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { error?: string } }; message?: string };
      const errDetail = e.response?.data?.error || e.message || "Failed to create tenant";
      setCreateError(errDetail);
    } finally {
      setCreating(false);
    }
  };

  const handleOpenActivate = (tenant: Tenant) => {
    setSelectedTenant(tenant);
    setPaymentRef("");
    setActivateNote("");
    setActivateError("");
    setShowActivate(true);
  };

  const handleConfirmActivate = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!selectedTenant) return;
    setActivating(true);
    setActivateError("");
    try {
      await platformTenantService.activateTenant(selectedTenant.id, {
        paymentReference: paymentRef.trim() || undefined,
        note: activateNote.trim() || undefined,
      });
      setMessage(`Tenant "${selectedTenant.name}" activated successfully.`);
      setShowActivate(false);
      setTenants((prev) =>
        prev.map((t) => (t.id === selectedTenant.id ? { ...t, status: "Active" as const, gates: 0 } : t))
      );
      loadTenants();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { error?: string } }; message?: string };
      const errDetail = e.response?.data?.error || e.message || "Failed to activate tenant";
      setActivateError(errDetail);
    } finally {
      setActivating(false);
    }
  };

  const handleOpenSuspend = (tenant: Tenant) => {
    setSelectedTenant(tenant);
    setSuspendReason("");
    setShowSuspend(true);
  };

  const handleConfirmSuspend = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!selectedTenant) return;
    setSuspending(true);
    try {
      await platformTenantService.suspendTenant(selectedTenant.id, suspendReason.trim() || undefined);
      setMessage(`Tenant "${selectedTenant.name}" has been SUSPENDED.`);
      setShowSuspend(false);
      loadTenants();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { error?: string } }; message?: string };
      setCreateError(e.response?.data?.error || e.message || "Failed to suspend tenant");
    } finally {
      setSuspending(false);
    }
  };

  const handleOpenArchive = (tenant: Tenant) => {
    setSelectedTenant(tenant);
    setArchiveReason("");
    setShowArchive(true);
  };

  const handleConfirmArchive = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!selectedTenant) return;
    setArchiving(true);
    try {
      await platformTenantService.archiveTenant(selectedTenant.id, archiveReason.trim() || undefined);
      setMessage(`Tenant "${selectedTenant.name}" has been ARCHIVED.`);
      setShowArchive(false);
      loadTenants();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { error?: string } }; message?: string };
      setCreateError(e.response?.data?.error || e.message || "Failed to archive tenant");
    } finally {
      setArchiving(false);
    }
  };

  const handleConfirmReactivate = async (tenant: Tenant) => {
    try {
      await platformTenantService.reactivateTenant(tenant.id);
      setMessage(`Tenant "${tenant.name}" reactivated to ACTIVE state.`);
      loadTenants();
    } catch (err: unknown) {
      const e = err as { message?: string };
      setMessage(`Failed to reactivate tenant: ${e.message}`);
    }
  };

  const handleOpenSettings = async (tenant: Tenant) => {
    setSelectedTenant(tenant);
    setShowSettings(true);
    setLoadingSettings(true);
    setSettingsError("");
    try {
      const res = await platformTenantService.getTenantSettings(tenant.id);
      if (res?.data) {
        setSettingsDraft({
          timeZone: res.data.timeZone || "Asia/Kolkata",
          dataRegion: res.data.dataRegion || "IN",
          analyticsOptOut: Boolean(res.data.analyticsOptOut),
          customDomain: res.data.customDomain || "",
          userLimit: res.data.userLimit || 1000,
          storageLimitGb: res.data.storageLimitGb || 50,
        });
      }
    } catch {
      setSettingsError("Could not load current settings.");
    } finally {
      setLoadingSettings(false);
    }
  };

  const handleSaveSettings = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!selectedTenant) return;
    setSavingSettings(true);
    setSettingsError("");
    try {
      await platformTenantService.updateTenantSettings(selectedTenant.id, {
        timeZone: settingsDraft.timeZone,
        dataRegion: settingsDraft.dataRegion,
        analyticsOptOut: settingsDraft.analyticsOptOut,
        customDomain: settingsDraft.customDomain.trim() || undefined,
        userLimit: Number(settingsDraft.userLimit),
        storageLimitGb: Number(settingsDraft.storageLimitGb),
      });
      setMessage(`Settings updated for tenant "${selectedTenant.name}".`);
      setShowSettings(false);
      loadTenants();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { error?: string } }; message?: string };
      setSettingsError(e.response?.data?.error || e.message || "Failed to update tenant settings");
    } finally {
      setSavingSettings(false);
    }
  };

  const filtered = useMemo(
    () =>
      tenants.filter(
        (t) =>
          (status === "All" || t.status === status) &&
          (t.name + t.id + t.type).toLowerCase().includes(query.toLowerCase())
      ),
    [tenants, status, query]
  );

  return (
    <div className="platform-page">
      <PageHeader
        title="Tenants"
        description="Manage institution tenancy and review the steps required before go-live."
        actions={
          <Button
            onClick={() => {
              setMessage("");
              setCreateError("");
              setShowCreate(true);
            }}
          >
            Create trial tenant
          </Button>
        }
      />
      {createdCredentials && (
        <TenantCredentialsNotice credentials={createdCredentials} onDismiss={() => setCreatedCredentials(null)} />
      )}
      {message && (
        <div className="platform-inline-notice" role="status">
          {message}
        </div>
      )}
      <div className="kpi-grid">
        <MetricCard label="All tenants" value={tenants.length} note="Live and trial directory" mark="book" />
        <MetricCard
          label="Active"
          value={tenants.filter((t) => t.status === "Active").length}
          note="Live tenants"
          mark="check-circle"
        />
        <MetricCard
          label="In trial"
          value={tenants.filter((t) => t.status === "Trial").length}
          note="Activation gated"
          mark="clock"
        />
      </div>
      <Panel
        title="Tenant register"
        description="A platform-level register. Tenant academic records are not shown here."
      >
        <div className="platform-toolbar">
          <SearchBox value={query} onChange={setQuery} placeholder="Search by institution or ID" />
          <label className="platform-select-label">
            Status{" "}
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option>All</option>
              <option>Active</option>
              <option>Trial</option>
              <option>Suspended</option>
            </select>
          </label>
        </div>
        {loading && tenants.length === 0 ? (
          <div className="platform-empty-inline">
            <Spinner label="Loading tenants" />
          </div>
        ) : (
          <DataTable
            headers={["Institution", "Type", "Plan", "Status", "Open gates", "Last updated", "Action"]}
            empty={!filtered.length}
          >
            {filtered.map((tenant) => (
              <tr key={tenant.id}>
                <td>
                  <strong>{tenant.name}</strong>
                  <small className="platform-cell-sub">{tenant.id}</small>
                </td>
                <td>{tenant.type}</td>
                <td>{tenant.plan}</td>
                <td>
                  <StatusBadge value={tenant.status} />
                </td>
                <td>
                  {tenant.gates ? (
                    <StatusBadge value={`${tenant.gates} pending`} />
                  ) : (
                    <StatusBadge value="Complete" />
                  )}
                </td>
                <td>{tenant.updated}</td>
                <td>
                  <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                    {canActivate && tenant.status === "Trial" && (
                      <Button variant="secondary" onClick={() => handleOpenActivate(tenant)}>
                        Activate
                      </Button>
                    )}
                    {canManageLifecycle && tenant.status === "Active" && (
                      <Button variant="secondary" onClick={() => handleOpenSuspend(tenant)}>
                        Suspend
                      </Button>
                    )}
                    {canManageLifecycle && tenant.status === "Suspended" && (
                      <>
                        <Button variant="secondary" onClick={() => handleConfirmReactivate(tenant)}>
                          Reactivate
                        </Button>
                        <Button variant="secondary" onClick={() => handleOpenArchive(tenant)}>
                          Archive
                        </Button>
                      </>
                    )}
                    {canEditSettings && (
                      <Button variant="secondary" onClick={() => handleOpenSettings(tenant)}>
                        Settings
                      </Button>
                    )}
                    <Button
                      variant="secondary"
                      onClick={() => {
                        setSelectedTenant(tenant);
                        setShowUsage(true);
                      }}
                    >
                      Usage
                    </Button>
                    <Button
                      variant="secondary"
                      onClick={() => {
                        setSelectedTenant(tenant);
                        setShowDataProtection(true);
                      }}
                    >
                      Compliance
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Panel>

      <CreateTenantModal
        isOpen={showCreate}
        onClose={() => setShowCreate(false)}
        onSubmit={submit}
        draft={draft}
        setDraft={setDraft}
        plans={plans}
        creating={creating}
        createError={createError}
        onNameChange={handleNameChange}
      />

      <ActivateTenantModal
        isOpen={showActivate}
        onClose={() => setShowActivate(false)}
        onSubmit={handleConfirmActivate}
        tenant={selectedTenant}
        paymentRef={paymentRef}
        setPaymentRef={setPaymentRef}
        activateNote={activateNote}
        setActivateNote={setActivateNote}
        activating={activating}
        activateError={activateError}
      />

      <SuspendTenantModal
        isOpen={showSuspend}
        onClose={() => setShowSuspend(false)}
        onSubmit={handleConfirmSuspend}
        tenant={selectedTenant}
        suspendReason={suspendReason}
        setSuspendReason={setSuspendReason}
        suspending={suspending}
      />

      <ArchiveTenantModal
        isOpen={showArchive}
        onClose={() => setShowArchive(false)}
        onSubmit={handleConfirmArchive}
        tenant={selectedTenant}
        archiveReason={archiveReason}
        setArchiveReason={setArchiveReason}
        archiving={archiving}
      />

      <TenantSettingsModal
        isOpen={showSettings}
        onClose={() => setShowSettings(false)}
        onSubmit={handleSaveSettings}
        tenant={selectedTenant}
        settingsDraft={settingsDraft}
        setSettingsDraft={setSettingsDraft}
        loadingSettings={loadingSettings}
        savingSettings={savingSettings}
        settingsError={settingsError}
      />

      <TenantDataProtectionModal
        isOpen={showDataProtection}
        onClose={() => setShowDataProtection(false)}
        tenant={selectedTenant}
        onTenantDeleted={(deletedId) => {
          setTenants((prev) => prev.filter((t) => t.id !== deletedId));
        }}
      />

      <TenantUsageModal
        isOpen={showUsage}
        onClose={() => setShowUsage(false)}
        tenant={selectedTenant}
      />
    </div>
  );
}

// Export with page name alias
export const TenantsPage = TenantsView;
