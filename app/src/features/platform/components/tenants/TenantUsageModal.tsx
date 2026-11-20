import { useEffect, useState } from "react";
import { Modal, Button, DataTable, Notice } from "../../../../design-system/components";
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
      .catch((err: unknown) => {
        if (!active) return;
        setError(err instanceof Error ? err.message : "Failed to load tenant monthly usage data.");
        setLoadedTenantId(tenantId);
      });
    return () => {
      active = false;
    };
  }, [isOpen, tenantId, loadedTenantId]);

  if (!tenant) return null;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Resource Usage: ${tenant.name}`}
      titleId="tenant-usage-title"
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
        {error && (
          <Notice tone="danger">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", width: "100%" }}>
              <span>{error}</span>
              <Button size="sm" variant="secondary" onClick={() => setLoadedTenantId(null)}>
                Retry
              </Button>
            </div>
          </Notice>
        )}

        <div style={{ display: "grid", gap: "var(--space-2)" }}>
              <h4 style={{ margin: 0, fontSize: "var(--fs-body)", color: "var(--color-text)" }}>
                Monthly Historical Records
              </h4>
              <DataTable
                headers={["Usage Month", "Metric", "Quantity", "Measured", "Computed At", "Final"]}
                loading={loading && usageList.length === 0}
                loadingLabel="Loading usage metrics"
                empty={!loading && usageList.length === 0}
              >
                {usageList.map((u) => (
                  <tr key={u.tenantUsageMonthlyId}>
                    <td><strong>{u.usageMonth.slice(0, 7)}</strong></td>
                    <td>{u.metricCode}</td>
                    <td>{u.measured ? Number(u.quantity).toLocaleString() : "Not measured"}</td>
                    <td>{u.measured ? "Yes" : "No"}</td>
                    <td>{u.computedAt ? new Date(u.computedAt).toLocaleString() : "—"}</td>
                    <td>{u.isFinal ? "Yes" : "No"}</td>
                  </tr>
                ))}
              </DataTable>
            </div>
      </div>
    </Modal>
  );
}
