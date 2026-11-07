import { useEffect, useState } from "react";
import { Modal, Button, DataTable, MetricCard } from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import { platformTenantService, type TenantUsageMonthlyDto } from "../../api/platformTenantService";
import type { Tenant } from "../common/types";

interface TenantUsageModalProps {
  isOpen: boolean;
  onClose: () => void;
  tenant: Tenant | null;
}

export function TenantUsageModal({ isOpen, onClose, tenant }: TenantUsageModalProps) {
  const [usageList, setUsageList] = useState<TenantUsageMonthlyDto[]>([]);
  const [error, setError] = useState("");
  const [loadedTenantId, setLoadedTenantId] = useState<string | null>(null);

  const tenantId = tenant?.id;
  const loading = Boolean(isOpen && tenantId && loadedTenantId !== tenantId);

  useEffect(() => {
    if (!isOpen || !tenantId) return;
    let active = true;
    platformTenantService
      .getTenantUsage(tenantId)
      .then((res) => {
        if (!active) return;
        setUsageList(res.data || []);
        setError("");
        setLoadedTenantId(tenantId);
      })
      .catch(() => {
        if (!active) return;
        setError("Failed to load tenant monthly usage data.");
        setLoadedTenantId(tenantId);
      });
    return () => {
      active = false;
    };
  }, [isOpen, tenantId]);

  if (!tenant) return null;

  const latest = usageList[0] || null;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Resource Usage: ${tenant.name}`}
      kicker="Telemetry & Quotas"
      size="lg"
      footer={
        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        </div>
      }
    >
      <div style={{ display: "grid", gap: "var(--space-4)" }}>
        {error && <div className="notice notice--danger">{error}</div>}

        {loading ? (
          <Spinner label="Loading usage metrics" />
        ) : (
          <>
            {latest && (
              <div className="kpi-grid">
                <MetricCard
                  label="Active Users"
                  value={latest.activeUserCount}
                  note={`Period: ${latest.usageYear}-${String(latest.usageMonth).padStart(2, "0")}`}
                  mark="users"
                />
                <MetricCard
                  label="Storage Used"
                  value={`${latest.storageUsedGb.toFixed(1)} GB`}
                  note="Cloud Object & DB"
                  mark="download"
                />
                <MetricCard
                  label="Total Logins"
                  value={latest.loginCount.toLocaleString()}
                  note="Monthly auth sessions"
                  mark="lock"
                />
                <MetricCard
                  label="API Calls"
                  value={latest.apiCallCount.toLocaleString()}
                  note="Platform requests"
                  mark="refresh"
                />
              </div>
            )}

            <div style={{ display: "grid", gap: "var(--space-2)" }}>
              <h4 style={{ margin: 0, fontSize: "var(--fs-body)", color: "var(--text-primary)" }}>
                Monthly Historical Records
              </h4>
              <DataTable
                headers={[
                  "Billing Period",
                  "Active Users",
                  "Storage (GB)",
                  "Logins",
                  "API Calls",
                  "Bandwidth (GB)",
                ]}
                empty={usageList.length === 0}
              >
                {usageList.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <strong>{`${u.usageYear}-${String(u.usageMonth).padStart(2, "0")}`}</strong>
                    </td>
                    <td>{u.activeUserCount}</td>
                    <td>{u.storageUsedGb.toFixed(2)} GB</td>
                    <td>{u.loginCount.toLocaleString()}</td>
                    <td>{u.apiCallCount.toLocaleString()}</td>
                    <td>{u.bandwidthGb.toFixed(2)} GB</td>
                  </tr>
                ))}
              </DataTable>
            </div>
          </>
        )}
      </div>
    </Modal>
  );
}
