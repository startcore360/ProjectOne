import { useEffect, useState } from "react";
import { Button, MetricCard, Panel, StatusBadge } from "../../../../design-system/components";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Spinner } from "../../../../components/ui";
import { Icon } from "../../../../components/ui/Icon";
import { platformTenantService, type TenantItem } from "../../api/platformTenantService";
import { platformUserService, type PlatformUserItem } from "../../api/platformUserService";
import { platformOpsService, type PlatformAuditLogItem } from "../../api/platformOpsService";

export function PlatformOverviewView() {
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [users, setUsers] = useState<PlatformUserItem[]>([]);
  const [auditLogs, setAuditLogs] = useState<PlatformAuditLogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    Promise.all([
      platformTenantService.getTenants().then((res) => (Array.isArray(res.data) ? res.data : res.data?.items ?? [])),
      platformUserService.getPlatformUsers().then((res) => (Array.isArray(res.data) ? res.data : (res.data as { items?: PlatformUserItem[] })?.items ?? [])),
      platformOpsService.listAuditLogs(),
    ]).then(([tList, uList, aList]) => {
      if (!active) return;
      setTenants(Array.isArray(tList) ? tList : []);
      setUsers(Array.isArray(uList) ? uList : []);
      setAuditLogs(Array.isArray(aList) ? aList : []);
    }).catch((err: unknown) => {
      if (active) setError(err instanceof Error ? err.message : "Unable to load platform overview. Please retry.");
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [retryKey]);

  const activeTenants = tenants.filter((t) => t.state === "ACTIVE");
  const trialTenants = tenants.filter((t) => t.state === "TRIAL");

  return (
    <div className="platform-page">
      <PageHeader
        title="Platform overview"
        description="Tenant lifecycle, platform access, integrations and service health at a glance."
      />

      {error && !loading && (
        <div className="eds-error-state" role="alert">
          <p className="eds-error-state__message">{error}</p>
          <Button variant="secondary" onClick={() => setRetryKey((value) => value + 1)}>Retry</Button>
        </div>
      )}

      {loading ? (
        <div className="platform-empty-inline">
          <Spinner label="Loading platform overview..." />
        </div>
      ) : error ? null : (
        <>
          <div className="kpi-grid">
            <MetricCard label="Tenants" value={tenants.length} note="Across the platform" mark="book" />
            <MetricCard
              label="Active tenants"
              value={activeTenants.length}
              note="Currently active"
              mark="check-circle"
            />
            <MetricCard
              label="Trial tenants"
              value={trialTenants.length}
              note="30-day evaluation period"
              mark="clock"
            />
            <MetricCard label="Platform users" value={users.length} note="Admin and support accounts" mark="users" />
          </div>

          <div className="platform-two-col">
            <Panel title="Enrolled Tenants" description="Registered institutions on EDU 360">
              <div className="platform-list">
                {tenants.slice(0, 5).map((tenant) => (
                  <div className="platform-list-row" key={tenant.tenantId}>
                    <span className="platform-list-icon">
                      <Icon name="book" size={16} />
                    </span>
                    <span className="platform-list-copy">
                      <strong>{tenant.tenantName}</strong>
                      <small>
                        {tenant.subdomain} · {tenant.tenantType} · Plan: {tenant.planName}
                      </small>
                    </span>
                    <StatusBadge value={tenant.state} />
                  </div>
                ))}
                {tenants.length === 0 && (
                  <p className="platform-empty-inline">No tenants enrolled yet.</p>
                )}
              </div>
            </Panel>

            <Panel title="Platform activity" description="Latest platform-scope audit events">
              <div className="platform-list">
                {auditLogs.slice(0, 5).map((event) => (
                  <div className="platform-list-row" key={event.auditOutboxId}>
                    <span className="platform-list-icon">
                      <Icon name="clock" size={16} />
                    </span>
                    <span className="platform-list-copy">
                      <strong>{event.operation}</strong>
                      <small>
                        {event.actorKind} · {new Date(event.occurredAt).toLocaleDateString()}
                      </small>
                    </span>
                    <StatusBadge value={event.outcome} />
                  </div>
                ))}
                {auditLogs.length === 0 && (
                  <p className="platform-empty-inline">No platform events recorded yet.</p>
                )}
              </div>
            </Panel>
          </div>
        </>
      )}
    </div>
  );
}

export const PlatformOverviewPage = PlatformOverviewView;
