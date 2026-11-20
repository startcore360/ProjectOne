import { useCallback, useEffect, useState } from "react";
import { getApiErrorMessage } from "../../services/apiHelper";
import { useAppSelector } from "../../store/hooks";
import { useTenantAccess } from "../profile/hooks/useTenantAccess";
import { PageHeader } from "../../components/layout/PageHeader";
import { ErrorState } from "../../components/feedback/ErrorState";
import { Button, SkeletonTable, StatusBadge } from "../../design-system/components";
import {
  tenantDataProtectionService,
  type TenantDeletionRequest,
  type TenantExportRequest,
} from "./tenantDataProtectionService";
import "./TenantDataProtectionPage.css";

const OPEN_DELETION_STATUSES = ["REQUESTED", "PO_CONFIRMED", "EXPORT_DONE", "PENDING_DPO", "APPROVED"];

function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString() : "—";
}

function effectiveExportStatus(item: TenantExportRequest) {
  if (item.status === "READY" && item.downloadExpiresAt
    && new Date(item.downloadExpiresAt).getTime() <= Date.now()) return "EXPIRED";
  return item.status;
}

export default function TenantDataProtectionPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const { can } = useTenantAccess();
  const canRequestExport = can("TENANT_EXPORT_REQUEST");
  const canRequestDeletion = can("TENANT_DELETION_REQUEST");
  const [exports, setExports] = useState<TenantExportRequest[]>([]);
  const [deletions, setDeletions] = useState<TenantDeletionRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const refresh = useCallback(async () => {
    if (!tenantId) {
      setLoading(false);
      setError("A tenant sign-in is required to view data protection requests.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      const [exportResponse, deletionResponse] = await Promise.all([
        tenantDataProtectionService.listExports(tenantId),
        tenantDataProtectionService.listDeletions(tenantId),
      ]);
      setExports(exportResponse.data ?? []);
      setDeletions(deletionResponse.data ?? []);
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => { void refresh(); }, [refresh]);

  const requestExport = async () => {
    if (!tenantId || !canRequestExport || submitting) return;
    setSubmitting(true);
    setError("");
    setNotice("");
    try {
      await tenantDataProtectionService.requestExport(tenantId);
      setNotice("The tenant export request was submitted for Data Protection Officer review.");
      await refresh();
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const requestDeletion = async () => {
    if (!tenantId || !canRequestDeletion || submitting) return;
    if (!window.confirm("Submit a tenant deletion request? This starts the approval workflow; it does not delete tenant data immediately.")) return;
    setSubmitting(true);
    setError("");
    setNotice("");
    try {
      await tenantDataProtectionService.requestDeletion(tenantId);
      setNotice("The tenant deletion request was submitted. Product Owner confirmation and DPO approval are still required.");
      await refresh();
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const downloadExport = async (requestId: string) => {
    if (!tenantId || !canRequestExport || downloadingId) return;
    setDownloadingId(requestId);
    setError("");
    try {
      const response = await tenantDataProtectionService.downloadExport(tenantId, requestId);
      const objectUrl = URL.createObjectURL(response.data);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = `tenant-export-${requestId}.zip`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(objectUrl);
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setDownloadingId(null);
    }
  };

  const openDeletionExists = deletions.some((item) => OPEN_DELETION_STATUSES.includes(item.status));
  const tenantDeleted = deletions.some((item) => item.status === "DELETED" || item.deletedAt);
  const canSubmitDeletion = canRequestDeletion && !openDeletionExists && !tenantDeleted;

  return (
    <div className="app-page tenant-data-protection">
      <PageHeader
        title="Data protection"
        description="Request a full-tenant data export or start the tenant deletion approval workflow. Your request history is visible below."
        actions={<Button type="button" variant="secondary" onClick={() => void refresh()} disabled={loading}>Refresh</Button>}
      />

      {notice && <div className="tenant-data-protection__notice" role="status">{notice}</div>}
      {error && <ErrorState title="Data protection request unavailable" message={error} action={<Button variant="secondary" onClick={() => void refresh()} disabled={loading}>Retry</Button>} />}

      <section className="tenant-data-protection__request-grid" aria-label="Data protection requests">
        {canRequestExport && (
          <article className="tenant-data-protection__request-card">
            <h2>Full-tenant data export</h2>
            <p>Request a complete tenant export. The Data Protection Officer must approve it before generation begins. A ready download link is available for 24 hours.</p>
            <Button type="button" onClick={() => void requestExport()} busy={submitting} disabled={submitting || loading}>Request data export</Button>
          </article>
        )}
        {canRequestDeletion && (
          <article className="tenant-data-protection__request-card">
            <h2>Tenant deletion</h2>
            <p>Start the tenant deletion workflow. Product Owner confirmation, completion of the required export, the retention period and DPO approval remain mandatory.</p>
            {openDeletionExists && <p className="tenant-data-protection__hint">An open deletion request already exists. A second request cannot be submitted.</p>}
            {tenantDeleted && <p className="tenant-data-protection__hint">This tenant has already been deleted.</p>}
            <Button type="button" variant="secondary" onClick={() => void requestDeletion()} busy={submitting} disabled={!canSubmitDeletion || submitting || loading}>Request tenant deletion</Button>
          </article>
        )}
      </section>

      <section className="tenant-data-protection__history" aria-labelledby="tenant-export-history-heading">
        <div className="tenant-data-protection__section-heading">
          <div>
            <h2 id="tenant-export-history-heading">Export request history</h2>
            <p>Downloads are available only while a request is READY and within its original 24-hour window.</p>
          </div>
        </div>
        {loading ? <SkeletonTable columns={5} rows={3} /> : exports.length === 0 ? (
          <p className="tenant-data-protection__empty">No tenant export requests have been submitted.</p>
        ) : (
          <div className="app-table-wrap">
            <table className="app-table">
              <thead><tr><th>Request</th><th>Status</th><th>Requested</th><th>Download expires</th><th>Download</th></tr></thead>
              <tbody>{exports.map((item) => {
                const status = effectiveExportStatus(item);
                const downloadable = canRequestExport && status === "READY";
                return <tr key={item.exportRequestId}>
                  <td><code>{item.exportRequestId}</code></td>
                  <td><StatusBadge value={status} status={status} /></td>
                  <td>{formatDate(item.createdAt)}</td>
                  <td>{formatDate(item.downloadExpiresAt)}</td>
                  <td>{downloadable ? (
                    <Button type="button" size="sm" variant="secondary" disabled={Boolean(downloadingId)} busy={downloadingId === item.exportRequestId} onClick={() => void downloadExport(item.exportRequestId)}>Download ZIP</Button>
                  ) : "—"}</td>
                </tr>;
              })}</tbody>
            </table>
          </div>
        )}
      </section>

      <section className="tenant-data-protection__history" aria-labelledby="tenant-deletion-history-heading">
        <div className="tenant-data-protection__section-heading">
          <div>
            <h2 id="tenant-deletion-history-heading">Deletion request history</h2>
            <p>Deletion is not immediate. The Product Owner and Data Protection Officer complete their separate approval steps.</p>
          </div>
        </div>
        {loading ? <SkeletonTable columns={5} rows={3} /> : deletions.length === 0 ? (
          <p className="tenant-data-protection__empty">No tenant deletion requests have been submitted.</p>
        ) : (
          <div className="app-table-wrap">
            <table className="app-table">
              <thead><tr><th>Request</th><th>Status</th><th>Requested</th><th>PO confirmed</th><th>Retention ends</th><th>DPO decision</th></tr></thead>
              <tbody>{deletions.map((item) => <tr key={item.deletionRequestId}>
                <td><code>{item.deletionRequestId}</code></td>
                <td><StatusBadge value={item.status} status={item.status} /></td>
                <td>{formatDate(item.createdAt)}</td>
                <td>{formatDate(item.poConfirmedAt)}</td>
                <td>{item.retentionEndsAt ? new Date(item.retentionEndsAt).toLocaleDateString() : "Not set"}</td>
                <td>{formatDate(item.dpoDecidedAt)}</td>
              </tr>)}</tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
