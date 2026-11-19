import { useEffect, useState } from "react";
import { Modal, Button, DataTable } from "../../../../design-system/components";
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
            <div style={{ display: "grid", gap: "var(--space-2)" }}>
              <h4 style={{ margin: 0, fontSize: "var(--fs-body)", color: "var(--text-primary)" }}>
                Monthly Historical Records
              </h4>
              <DataTable
                headers={["Usage Month", "Metric", "Quantity", "Measured", "Computed At", "Final"]}
                empty={usageList.length === 0}
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
          </>
        )}
      </div>
    </Modal>
  );
}
