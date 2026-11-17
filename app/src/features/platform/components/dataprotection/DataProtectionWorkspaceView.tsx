import { useCallback, useEffect, useState, type FormEvent } from "react";
import "./DataProtectionWorkspaceView.css";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  Modal,
  PageHeader,
  SelectField,
  Spinner,
  StatusBadge,
  Tabs,
  TextAreaField,
} from "../../../../design-system/components";
import { usePlatformAccess } from "../../platformAccess";
import {
  platformDataProtectionService,
  type DataProtectionDecision,
  type RetentionChangeRequestDto,
} from "../../api/platformDataProtectionService";
import type { DeletionRequestDto, ExportRequestDto } from "../../api/platformTenantService";

type QueueTab = "exports" | "deletions" | "retention";
type DecisionTarget =
  | { kind: "export"; id: string; tenantId: string; tenantName: string; rowVersion: number }
  | { kind: "deletion"; id: string; tenantId: string; tenantName: string; waiverReason?: string }
  | { kind: "retention"; id: string; tenantId: string; tenantName: string };

const TAB_LABELS: Record<QueueTab, string> = {
  exports: "Export requests",
  deletions: "Deletion requests",
  retention: "Retention changes",
};

export function DataProtectionWorkspacePage() {
  const { can } = usePlatformAccess();
  const canReviewExport = can("TENANT_EXPORT_APPROVE");
  const canReviewDeletion = can("TENANT_DELETION_APPROVE");
  const canReviewRetention = can("RETENTION_CHANGE_APPROVE");

  const [activeTab, setActiveTab] = useState<QueueTab>("exports");
  const [exports, setExports] = useState<ExportRequestDto[]>([]);
  const [deletions, setDeletions] = useState<DeletionRequestDto[]>([]);
  const [retentionChanges, setRetentionChanges] = useState<RetentionChangeRequestDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [target, setTarget] = useState<DecisionTarget | null>(null);
  const [decision, setDecision] = useState<DataProtectionDecision>("APPROVED");
  const [remark, setRemark] = useState("");
  const [saving, setSaving] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [exportResponse, deletionResponse, retentionResponse] = await Promise.all([
        platformDataProtectionService.listExports(),
        platformDataProtectionService.listDeletions(),
        platformDataProtectionService.listRetentionChanges(),
      ]);
      setExports(exportResponse.data ?? []);
      setDeletions(deletionResponse.data ?? []);
      setRetentionChanges(retentionResponse.data ?? []);
    } catch {
      setError("The data protection queues could not be loaded. Check your access and retry.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const openDecision = (next: DecisionTarget) => {
    setTarget(next);
    setDecision("APPROVED");
    setRemark("");
  };

  const submitDecision = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!target || saving || (decision === "REJECTED" && !remark.trim())) return;

    const permitted = target.kind === "export"
      ? canReviewExport
      : target.kind === "deletion"
        ? canReviewDeletion
        : canReviewRetention;
    if (!permitted) return;

    setSaving(true);
    setError(null);
    try {
      if (target.kind === "export") {
        await platformDataProtectionService.decideExport(target.id, decision, remark.trim(), target.rowVersion);
      } else if (target.kind === "deletion") {
        await platformDataProtectionService.decideDeletion(target.id, decision, remark.trim());
      } else {
        await platformDataProtectionService.decideRetentionChange(target.id, decision, remark.trim());
      }
      setTarget(null);
      await refresh();
    } catch {
      setError("The decision was not saved. The request may have changed; refresh the queue and retry.");
    } finally {
      setSaving(false);
    }
  };

  const reviewButton = (allowed: boolean, next: DecisionTarget) => allowed
    ? <Button size="sm" variant="secondary" onClick={() => openDecision(next)}>Review</Button>
    : "—";

  const tabs = [
    { id: "exports", label: TAB_LABELS.exports, count: exports.length },
    { id: "deletions", label: TAB_LABELS.deletions, count: deletions.length },
    { id: "retention", label: TAB_LABELS.retention, count: retentionChanges.length },
  ];

  return (
    <div className="app-page data-protection-workspace">
      <PageHeader
        title="Data protection workspace"
        description="Review tenant export requests, deletion requests, and retention changes. Each decision is recorded against its request."
        actions={<Button variant="secondary" onClick={() => void refresh()} disabled={loading}>Refresh queues</Button>}
      />

      {error && (
        <ErrorState
          title="Data protection queues unavailable"
          message={error}
          action={<Button variant="secondary" onClick={() => void refresh()} disabled={loading}>Retry</Button>}
        />
      )}

      {loading ? (
        <div className="app-empty-inline"><Spinner label="Loading data protection queues" /></div>
      ) : !error ? (
        <>
          <Tabs
            items={tabs}
            activeId={activeTab}
            onChange={(id) => setActiveTab(id as QueueTab)}
            ariaLabel="Data protection queues"
          />

          <section className="data-protection-workspace__queue" aria-label={`${TAB_LABELS[activeTab]} queue`}>
            {activeTab === "exports" && (exports.length ? (
              <DataTable headers={["Tenant", "Requested by", "Status", "Requested", "Decision"]}>
                {exports.map((item) => (
                  <tr key={item.exportRequestId}>
                    <td><strong>{item.tenantName}</strong></td>
                    <td>{item.requestedByKind}</td>
                    <td><StatusBadge value={item.status} status={item.status} /></td>
                    <td>{item.createdAt ? new Date(item.createdAt).toLocaleString() : "—"}</td>
                    <td>{item.status === "REQUESTED" ? reviewButton(canReviewExport, {
                      kind: "export", id: item.exportRequestId, tenantId: item.tenantId,
                      tenantName: item.tenantName, rowVersion: item.rowVersion,
                    }) : "—"}</td>
                  </tr>
                ))}
              </DataTable>
            ) : <EmptyState title="No export requests" description="There are no export requests in this queue." />)}

            {activeTab === "deletions" && (deletions.length ? (
              <DataTable headers={["Tenant", "Origin", "Status", "PO confirmed", "Retention ends", "Decision"]}>
                {deletions.map((item) => (
                  <tr key={item.deletionRequestId}>
                    <td><strong>{item.tenantName}</strong></td>
                    <td>{item.origin}</td>
                    <td><StatusBadge value={item.status} status={item.status} /></td>
                    <td>{item.poConfirmedAt ? new Date(item.poConfirmedAt).toLocaleString() : "—"}</td>
                    <td>{item.status === "EXPORT_DONE" && item.retentionEndsAt
                      ? `Waiting: retention ends ${item.retentionEndsAt}`
                      : item.retentionEndsAt ?? "Not set"}
                      {item.retentionWaivedAt ? " (wait waived by Product Owner)" : ""}</td>
                    <td>{item.status === "PENDING_DPO" ? reviewButton(canReviewDeletion, {
                      kind: "deletion", id: item.deletionRequestId, tenantId: item.tenantId,
                      tenantName: item.tenantName, waiverReason: item.retentionWaiverReason,
                    }) : "—"}</td>
                  </tr>
                ))}
              </DataTable>
            ) : <EmptyState title="No deletion requests" description="There are no deletion requests awaiting review." />)}

            {activeTab === "retention" && (retentionChanges.length ? (
              <DataTable headers={["Tenant", "Data class", "Current", "Requested", "Reason", "Status", "Decision"]}>
                {retentionChanges.map((item) => (
                  <tr key={item.retentionChangeRequestId}>
                    <td><strong>{item.tenantName}</strong></td>
                    <td>{item.dataClassCode}</td>
                    <td>{item.currentMonths == null ? "Not set" : `${item.currentMonths} months`}</td>
                    <td>{item.requestedMonths} months</td>
                    <td>{item.reason}</td>
                    <td><StatusBadge value={item.status} status={item.status} /></td>
                    <td>{item.status === "REQUESTED" ? reviewButton(canReviewRetention, {
                      kind: "retention", id: item.retentionChangeRequestId, tenantId: item.tenantId,
                      tenantName: item.tenantName,
                    }) : "—"}</td>
                  </tr>
                ))}
              </DataTable>
            ) : <EmptyState title="No retention changes" description="There are no retention change requests awaiting review." />)}
          </section>
        </>
      ) : null}

      <Modal
        isOpen={Boolean(target)}
        onClose={() => setTarget(null)}
        title="Review data protection request"
        description="Choose a decision. A remark is mandatory when rejecting a request."
        size="md"
        footer={
          <div className="data-protection-workspace__decision-actions">
            <Button variant="secondary" onClick={() => setTarget(null)} disabled={saving}>Cancel</Button>
            <Button
              type="submit"
              form="data-protection-decision-form"
              disabled={saving || (decision === "REJECTED" && !remark.trim())}
              busy={saving}
            >
              {decision === "APPROVED" ? "Approve request" : "Reject request"}
            </Button>
          </div>
        }
      >
        {target && (
          <form id="data-protection-decision-form" className="data-protection-workspace__decision-form" onSubmit={submitDecision}>
            <div className="data-protection-workspace__request-details">
              <p><strong>Tenant:</strong> {target.tenantName}</p>
              <p><strong>Request ID:</strong> <code>{target.id}</code></p>
              <p><strong>Request type:</strong> {target.kind}</p>
              {target.kind === "deletion" && target.waiverReason && (
                <p><strong>Retention wait waived by the Product Owner:</strong> {target.waiverReason}</p>
              )}
            </div>
            <SelectField
              id="data-protection-decision"
              label="Decision"
              value={decision}
              onChange={(event) => setDecision(event.target.value as DataProtectionDecision)}
              options={[
                { value: "APPROVED", label: "Approve" },
                { value: "REJECTED", label: "Reject" },
              ]}
            />
            <TextAreaField
              id="data-protection-remark"
              label="Decision remark"
              value={remark}
              onChange={(event) => setRemark(event.target.value)}
              rows={4}
              maxLength={1000}
              required={decision === "REJECTED"}
            />
          </form>
        )}
      </Modal>
    </div>
  );
}
