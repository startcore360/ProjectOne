import { useCallback, useEffect, useState } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Icon } from "../../../../components/ui/Icon";
import { Button, Notice, Panel, SkeletonPage, StatusBadge } from "../../../../design-system/components";
import { MetricCard } from "../../../../design-system/components/MetricCard";
import { useAppSelector } from "../../../../store/hooks";
import { renderGreeting } from "../../../../utils/greeting";
import {
  platformDataProtectionService,
  type RetentionChangeRequestDto,
} from "../../api/platformDataProtectionService";
import type { DeletionRequestDto, ExportRequestDto } from "../../api/platformTenantService";

export function DataProtectionOfficerDashboard() {
  const displayName = useAppSelector((state) => state.auth.user?.displayName);
  const [exports, setExports] = useState<ExportRequestDto[]>([]);
  const [deletions, setDeletions] = useState<DeletionRequestDto[]>([]);
  const [retentionChanges, setRetentionChanges] = useState<RetentionChangeRequestDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [minSpinning, setMinSpinning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const isSpinning = loading || minSpinning;

  const handleRefresh = useCallback(() => {
    setMinSpinning(true);
    setRefreshKey((k) => k + 1);
    setTimeout(() => setMinSpinning(false), 600);
  }, []);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [exportRes, deletionRes, retentionRes] = await Promise.all([
        platformDataProtectionService.listExports().catch(() => ({ data: [] })),
        platformDataProtectionService.listDeletions().catch(() => ({ data: [] })),
        platformDataProtectionService.listRetentionChanges().catch(() => ({ data: [] })),
      ]);
      setExports(Array.isArray(exportRes?.data) ? exportRes.data : []);
      setDeletions(Array.isArray(deletionRes?.data) ? deletionRes.data : []);
      setRetentionChanges(Array.isArray(retentionRes?.data) ? retentionRes.data : []);
    } catch {
      setError("Data protection telemetry could not be loaded. Check your connection or permissions.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchData();
  }, [fetchData, refreshKey]);

  const safeExports = Array.isArray(exports) ? exports : [];
  const safeDeletions = Array.isArray(deletions) ? deletions : [];
  const safeRetention = Array.isArray(retentionChanges) ? retentionChanges : [];

  const pendingExports = safeExports.filter((e) => e?.status === "REQUESTED");
  const pendingDeletions = safeDeletions.filter((d) => d?.status === "REQUESTED");
  const pendingRetention = safeRetention.filter((r) => r?.status === "REQUESTED");
  const totalPending = pendingExports.length + pendingDeletions.length + pendingRetention.length;

  const today = new Date().toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long" });

  const allPendingRows = [
    ...pendingExports.map((e) => ({
      id: e.exportRequestId,
      type: "Export",
      tenant: e.tenantName,
      created: e.createdAt,
      status: e.status,
    })),
    ...pendingDeletions.map((d) => ({
      id: d.deletionRequestId,
      type: "Deletion",
      tenant: d.tenantName,
      created: d.createdAt,
      status: d.status,
    })),
    ...pendingRetention.map((r) => ({
      id: r.retentionChangeRequestId,
      type: "Retention",
      tenant: r.tenantName,
      created: r.createdAt,
      status: r.status,
    })),
  ].sort((a, b) => new Date(b.created || 0).getTime() - new Date(a.created || 0).getTime());

  return (
    <div className="app-page po-dash">
      <PageHeader
        title={renderGreeting(displayName)}
        description={`Data protection workspace · ${today}`}
        actions={
          <div className="po-dash__actions">
            <Button
              variant="tertiary"
              size="sm"
              className="po-dash__refresh"
              onClick={handleRefresh}
              disabled={isSpinning}
              aria-label="Refresh dashboard"
              title="Refresh"
            >
              <Icon name="refresh" size={16} spin={isSpinning} />
            </Button>
          </div>
        }
      />

      {error && !loading && (
        <Notice tone="warning">
          {error}{" "}
          <Button size="sm" variant="secondary" onClick={() => setRefreshKey((k) => k + 1)}>
            Retry
          </Button>
        </Notice>
      )}

      {loading && exports.length === 0 && deletions.length === 0 ? (
        <SkeletonPage title="Loading DPO dashboard" showKpis kpiCount={4} showTable tableRows={4} tableColumns={4} />
      ) : (
        <>
          {/* Attention Banner */}
          <section
            className={`po-status-card ${totalPending > 0 ? "po-status-card--warning" : "po-status-card--ok"}`}
            aria-labelledby="dpo-attention-title"
          >
            <div className="po-status-card__head">
              <span className="po-status-card__icon" aria-hidden="true">
                <Icon name={totalPending > 0 ? "alert" : "check-circle"} size={18} />
              </span>
              <h2 id="dpo-attention-title">
                {totalPending > 0
                  ? `${totalPending} ${totalPending === 1 ? "request needs" : "requests need"} your authorization`
                  : "All clear — no privacy requests waiting for action"}
              </h2>
            </div>
            {totalPending > 0 && (
              <ul className="po-attention__list" aria-label="Requests needing review">
                <li className="po-attention__item po-attention__item--warning">
                  <span className="po-attention__icon" aria-hidden="true">
                    <Icon name="shield" size={16} />
                  </span>
                  <span className="po-attention__copy">
                    <strong>
                      {pendingExports.length} export, {pendingDeletions.length} erasure, and {pendingRetention.length} retention requests
                    </strong>
                    <small>Review tenant requests to uphold statutory compliance (DPDP &amp; GDPR).</small>
                  </span>
                  <a
                    className="eds-button eds-button--primary eds-button--sm po-attention__action"
                    href="/platform/data-protection"
                  >
                    <span>Open approval queue</span>
                    <span aria-hidden="true" style={{ fontSize: "1.1em", lineHeight: 1 }}>&rarr;</span>
                  </a>
                </li>
              </ul>
            )}
          </section>

          {/* Headline numbers */}
          <div className="kpi-grid po-kpis">
            <MetricCard
              label="Pending Approvals"
              value={totalPending}
              icon="shield"
              context="Awaiting DPO decision"
              progress={totalPending > 0 ? [totalPending, Math.max(totalPending, 20), "Pending share"] : undefined}
            />
            <MetricCard
              label="Export Requests"
              value={exports.length}
              icon="book"
              context={`${pendingExports.length} pending review`}
            />
            <MetricCard
              label="Deletion Requests"
              value={deletions.length}
              icon="alert"
              context={`${pendingDeletions.length} pending authorization`}
            />
            <MetricCard
              label="Retention Changes"
              value={retentionChanges.length}
              icon="clock"
              context={`${pendingRetention.length} awaiting policy review`}
            />
          </div>

          {/* Work lists */}
          <div className="po-dash__columns">
            <Panel
              title="Urgent Privacy Requests"
              description="Newest requests across tenant institutions awaiting decision."
              action={<a className="eds-link-button" href="/platform/data-protection">Open workspace →</a>}
            >
              <div className="app-panel__body">
                {allPendingRows.length === 0 ? (
                  <p className="po-empty">
                    <Icon name="check-circle" size={16} /> No pending requests in queue.
                  </p>
                ) : (
                  <ul className="po-rows">
                    {allPendingRows.slice(0, 5).map((row) => (
                      <li key={row.id} className="po-row">
                        <span className="po-row__body">
                          <strong>{row.tenant}</strong>
                          <small>
                            {row.type} request · {row.created ? new Date(row.created).toLocaleDateString("en-IN") : "Recent"}
                          </small>
                        </span>
                        <StatusBadge value={row.status} status={row.status} />
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </Panel>

            <Panel
              title="DPO Governance & Delegation"
              description="Statutory coverage and RBAC compliance management."
              action={<a className="eds-link-button" href="/platform/data-protection">Delegate →</a>}
            >
              <div className="app-panel__body" style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
                <div style={{ padding: "var(--space-3)", background: "var(--color-bg-subtle)", borderRadius: "var(--radius-md)", border: "1px solid var(--color-border-divider)" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <Icon name="shield" size={16} style={{ color: "var(--color-action-primary)" }} />
                    <strong style={{ fontSize: "var(--fs-body-sm)", color: "var(--color-text)" }}>Statutory Authority Active</strong>
                  </div>
                  <p style={{ margin: "4px 0 0", fontSize: "var(--fs-caption)", color: "var(--color-text-muted)", lineHeight: 1.4 }}>
                    Authorized to approve data portability exports, permanent account deletions, and data retention tenure modifications.
                  </p>
                </div>

                <div style={{ padding: "var(--space-3)", background: "var(--color-bg-subtle)", borderRadius: "var(--radius-md)", border: "1px solid var(--color-border-divider)" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <Icon name="users" size={16} style={{ color: "var(--color-action-primary)" }} />
                    <strong style={{ fontSize: "var(--fs-body-sm)", color: "var(--color-text)" }}>Temporary Delegation</strong>
                  </div>
                  <p style={{ margin: "4px 0 0", fontSize: "var(--fs-caption)", color: "var(--color-text-muted)", lineHeight: 1.4 }}>
                    Assign temporary review rights to an alternate Data Protection Officer during planned leave or operational handover.
                  </p>
                </div>

                <div style={{ marginTop: "auto", display: "flex", gap: "var(--space-2)" }}>
                  <a href="/platform/data-protection" className="eds-button eds-button--secondary eds-button--sm">
                    Manage delegations
                  </a>
                </div>
              </div>
            </Panel>
          </div>
        </>
      )}
    </div>
  );
}

export default DataProtectionOfficerDashboard;
