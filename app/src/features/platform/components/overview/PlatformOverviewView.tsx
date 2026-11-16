import { useEffect, useState } from "react";
import { Button, MetricCard, Panel, StatusBadge, Notice } from "../../../../design-system/components";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Spinner } from "../../../../components/ui";
import { Icon } from "../../../../components/ui/Icon";
import { platformTenantService, type TenantItem } from "../../api/platformTenantService";
import { platformUserService, type PlatformUserItem } from "../../api/platformUserService";
import { platformOpsService, type PlatformAuditLogItem } from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";

export function PlatformOverviewView() {
  const { can, canAny, role } = usePlatformAccess();
  const canViewTenants = canAny(["TENANT_CREATE", "TENANT_SETTINGS", "TENANT_ACTIVATE", "TENANT_SUSPEND_ARCHIVE", "INVOICE_MANAGE", "SUPPORT_TICKET_MANAGE"]);
  const canViewUsers = can("PLATFORM_USER_MANAGE");
  const canViewAudit = can("PLATFORM_AUDIT_VIEW");
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [users, setUsers] = useState<PlatformUserItem[]>([]);
  const [auditLogs, setAuditLogs] = useState<PlatformAuditLogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    const tenantRequest = canViewTenants
      ? platformTenantService.getTenants().then((res) => (Array.isArray(res.data) ? res.data : res.data?.items ?? []))
      : Promise.resolve([] as TenantItem[]);
    const userRequest = canViewUsers
      ? platformUserService.getPlatformUsers().then((res) => (Array.isArray(res.data) ? res.data : (res.data as { items?: PlatformUserItem[] })?.items ?? []))
      : Promise.resolve([] as PlatformUserItem[]);
    const auditRequest = canViewAudit ? platformOpsService.listAuditLogs() : Promise.resolve([] as PlatformAuditLogItem[]);

    Promise.allSettled([tenantRequest, userRequest, auditRequest]).then(([tenantResult, userResult, auditResult]) => {
      if (!active) return;
      setTenants(tenantResult.status === "fulfilled" ? tenantResult.value : []);
      setUsers(userResult.status === "fulfilled" ? userResult.value : []);
      setAuditLogs(auditResult.status === "fulfilled" ? auditResult.value : []);
      const failed = [tenantResult, userResult, auditResult].some((result) => result.status === "rejected");
      setError(failed ? "Some overview panels could not be loaded. Retry to refresh the available data." : "");
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [retryKey, canViewTenants, canViewUsers, canViewAudit]);

  const activeTenants = tenants.filter((t) => t.state === "ACTIVE");
  const trialTenants = tenants.filter((t) => t.state === "TRIAL");

  const workspaceMessage = role === "BILLING_MANAGER"
    ? "Billing workspace: use Invoices for invoice review, approvals and overdue accounts."
    : role === "SUPPORT_MANAGER"
      ? "Support workspace: use Tickets and Support Access for your assigned support work."
      : role === "RELEASE_MANAGER"
        ? "Release workspace: use Releases for deployment and release management."
        : role === "DATA_PROTECTION_OFFICER"
          ? "Data protection workspace: review export, deletion and retention approval queues."
          : null;


  return (
    <div className="app-page">
      <PageHeader
        title="Platform overview"
        description="Tenant lifecycle, platform access, integrations and service health at a glance."
      />

      {error && !loading && (
        <Notice tone="warning">
          {error} <Button size="sm" variant="secondary" onClick={() => { setLoading(true); setRetryKey((value) => value + 1); }}>Retry</Button>
        </Notice>
      )}

      {loading ? (
        <div className="app-empty-inline">
          <Spinner label="Loading platform overview..." />
        </div>
      ) : (
        <>
          {(canViewTenants || canViewUsers) && (
            <div className="kpi-grid">
              {canViewTenants && <MetricCard label="Tenants" value={tenants.length} note="Across the platform" mark="book" />}
              {canViewTenants && <MetricCard label="Active tenants" value={activeTenants.length} note="Currently active" mark="check-circle" />}
              {canViewTenants && <MetricCard label="Trial tenants" value={trialTenants.length} note="30-day evaluation period" mark="clock" />}
              {canViewUsers && <MetricCard label="Platform users" value={users.length} note="Admin and support accounts" mark="users" />}
            </div>
          )}

          {workspaceMessage && <Panel title="Your workspace" description={workspaceMessage}><p>{workspaceMessage}</p></Panel>}

          {(canViewTenants || canViewAudit) && (
          <div className="app-two-col">
            {canViewTenants && <Panel title="Enrolled Tenants" description="Registered institutions on EDU 360">
              <div className="app-list">
                {tenants.slice(0, 5).map((tenant) => (
                  <div className="app-list-row" key={tenant.tenantId}>
                    <span className="app-list-icon">
                      <Icon name="book" size={16} />
                    </span>
                    <span className="app-list-copy">
                      <strong>{tenant.tenantName}</strong>
                      <small>
                        {tenant.subdomain} · {tenant.tenantType} · Plan: {tenant.planName}
                      </small>
                    </span>
                    <StatusBadge value={tenant.state} />
                  </div>
                ))}
                {tenants.length === 0 && (
                  <p className="app-empty-inline">No tenants enrolled yet.</p>
                )}
              </div>
            </Panel>}

            {canViewAudit && <Panel title="Platform activity" description="Latest app-scope audit events">
              <div className="app-list">
                {auditLogs.slice(0, 5).map((event) => (
                  <div className="app-list-row" key={event.auditOutboxId}>
                    <span className="app-list-icon">
                      <Icon name="clock" size={16} />
                    </span>
                    <span className="app-list-copy">
                      <strong>{event.operation}</strong>
                      <small>
                        {event.actorKind} · {new Date(event.occurredAt).toLocaleDateString()}
                      </small>
                    </span>
                    <StatusBadge value={event.outcome} />
                  </div>
                ))}
                {auditLogs.length === 0 && (
                  <p className="app-empty-inline">No platform events recorded yet.</p>
                )}
              </div>
            </Panel>}
          </div>
          )}
          {!canViewTenants && !canViewUsers && !canViewAudit && !workspaceMessage && (
            <Notice tone="info">Your platform workspace is available through the actions assigned to your role.</Notice>
          )}
        </>
      )}
    </div>
  );
}

export const PlatformOverviewPage = PlatformOverviewView;
