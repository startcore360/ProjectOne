import { useCallback, useEffect, useMemo, useState } from "react";
import { Icon } from "../../../components/ui/Icon";
import { PageHeader, Panel } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import {
  approvalDelegationService,
  type ApprovalInboxItem,
  type ApprovalRequestSummary,
} from "../../institution-admin/api/approvalDelegationService";

type Decision = "APPROVED" | "REJECTED";
const formatDate = (value?: string | null) => {
  if (!value) return "Date unavailable";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Date unavailable" : date.toLocaleString();
};
const readable = (value: string) =>
  value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());

export default function ApprovalInboxPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [items, setItems] = useState<ApprovalInboxItem[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [request, setRequest] = useState<ApprovalRequestSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [remark, setRemark] = useState("");
  const [search, setSearch] = useState("");
  const [feedback, setFeedback] = useState("");

  const loadInbox = useCallback(async (preferredId?: string) => {
    if (!tenantId) {
      setItems([]);
      setLoading(false);
      setError("Your signed-in account does not have a tenant context.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      const nextItems = await approvalDelegationService.listInbox(tenantId);
      setItems(nextItems);
      setSelectedId((current) => {
        const target = preferredId || current;
        return nextItems.some((item) => item.approvalRequestId === target)
          ? target
          : nextItems[0]?.approvalRequestId ?? "";
      });
    } catch {
      setError("The approval inbox could not be loaded. Check your connection and retry.");
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => { void loadInbox(); }, [loadInbox]);

  useEffect(() => {
    let active = true;
    if (!tenantId || !selectedId) {
      setRequest(null);
      return () => { active = false; };
    }
    setDetailLoading(true);
    setFeedback("");
    approvalDelegationService.getApprovalRequest(tenantId, selectedId)
      .then((result) => { if (active) setRequest(result); })
      .catch(() => { if (active) setRequest(null); })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [tenantId, selectedId]);

  const filteredItems = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return items;
    return items.filter((item) =>
      [item.processCode, item.subjectType, item.subjectId, item.requesterActiveRole, item.approverRoleCode]
        .some((value) => value.toLowerCase().includes(query)),
    );
  }, [items, search]);

  const decide = async (decision: Decision) => {
    if (!tenantId || !request || busy) return;
    if (decision === "REJECTED" && !remark.trim()) {
      setFeedback("A remark is required when rejecting an approval request.");
      return;
    }
    setBusy(true);
    setFeedback("");
    try {
      await approvalDelegationService.decideApproval(
        tenantId, request.approvalRequestId, decision, remark.trim() || undefined, request.currentStepNo,
      );
      setFeedback(decision === "APPROVED" ? "Approval decision recorded." : "Request rejected.");
      setRemark("");
      await loadInbox();
    } catch {
      setFeedback("The decision could not be recorded. The request may have changed or your approval scope may no longer be valid. Refresh and try again.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="app-page app-approval-page">
      <PageHeader title="Approval inbox" description="Review requests currently assigned to your active role and institution scope." />
      <div className="app-approval-summary">
        <div>
          <span className="app-eyebrow">PENDING WITH YOU</span>
          <strong>{loading ? "…" : items.length}</strong>
          <span>Current approval steps assigned to your account</span>
        </div>
        <button type="button" className="app-approval-refresh" onClick={() => void loadInbox()} disabled={loading || busy}>
          <Icon name="refresh" size={16} /> Refresh inbox
        </button>
      </div>
      {error && <div className="app-approval-alert" role="alert">{error}</div>}
      {feedback && <div className="app-approval-feedback" role="status">{feedback}</div>}

      <div className="app-approval-layout">
        <Panel title="Assigned requests" description="Only actionable pending requests are returned by the approval API.">
          <div className="app-approval-list-tools">
            <input aria-label="Search approval requests" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search type, role or reference" />
          </div>
          {loading ? (
            <p className="app-approval-muted" role="status">Loading approval requests…</p>
          ) : filteredItems.length === 0 ? (
            <div className="app-approval-empty">
              <Icon name="check-circle" size={26} />
              <strong>{items.length === 0 ? "You’re all caught up" : "No matching requests"}</strong>
              <span>{items.length === 0 ? "There are no pending approval steps assigned to your active role." : "Try a different search term."}</span>
            </div>
          ) : (
            <div className="app-approval-list">
              {filteredItems.map((item) => (
                <button type="button" key={item.approvalRequestId}
                  className={item.approvalRequestId === selectedId ? "app-approval-item is-selected" : "app-approval-item"}
                  onClick={() => setSelectedId(item.approvalRequestId)}>
                  <span className="app-approval-item__top">
                    <strong>{readable(item.processCode || item.subjectType)}</strong>
                    <span className="app-approval-status">Pending</span>
                  </span>
                  <span className="app-approval-item__meta">{readable(item.subjectType)} · Step {item.stepNo}</span>
                  <span className="app-approval-item__meta">Requested by {readable(item.requesterActiveRole)}</span>
                  <span className="app-approval-item__date">{formatDate(item.requestedAt)}</span>
                </button>
              ))}
            </div>
          )}
        </Panel>

        <Panel title="Request details" description="Review the approval chain before making a decision.">
          {!selectedId ? (
            <div className="app-approval-empty">
              <Icon name="file-text" size={26} />
              <strong>Select a request</strong>
              <span>Choose a pending item to review its details and decision history.</span>
            </div>
          ) : detailLoading ? (
            <p className="app-approval-muted" role="status">Loading request details…</p>
          ) : !request ? (
            <div className="app-approval-alert" role="alert">Request details are unavailable or the request is no longer in your scope. Refresh the inbox.</div>
          ) : (
            <div className="app-approval-detail">
              <div className="app-approval-detail__heading">
                <div>
                  <span className="app-eyebrow">{readable(request.processCode)}</span>
                  <h2>{readable(request.subject.type)}</h2>
                  <p>Reference: <code>{request.subject.id}</code></p>
                </div>
                <span className="app-approval-status">{readable(request.status)}</span>
              </div>
              <dl className="app-approval-facts">
                <div><dt>Request ID</dt><dd><code>{request.approvalRequestId}</code></dd></div>
                <div><dt>Current step</dt><dd>{request.currentStepNo}</dd></div>
              </dl>
              <h3>Approval chain</h3>
              <ol className="app-approval-timeline">
                {request.steps.map((step) => (
                  <li key={step.stepNo} className={step.stepNo === request.currentStepNo && step.decision === "PENDING" ? "is-current" : ""}>
                    <div className="app-approval-timeline__top">
                      <strong>{readable(step.approverRoleCode)}</strong>
                      <span>{readable(step.decision)}</span>
                    </div>
                    {step.actedActiveRole && <p>Acted in role: {readable(step.actedActiveRole)}</p>}
                    {step.remark && <p>Remark: {step.remark}</p>}
                    {step.decidedAt && <time>{formatDate(step.decidedAt)}</time>}
                    {step.skipReason && <p>Skipped: {step.skipReason}</p>}
                  </li>
                ))}
              </ol>
              {request.status === "PENDING" && (
                <div className="app-approval-decision">
                  <label htmlFor="approval-remark">Decision remark (required for rejection)</label>
                  <textarea id="approval-remark" rows={3} value={remark} onChange={(event) => setRemark(event.target.value)} placeholder="Add context for your decision" />
                  <div className="app-approval-decision__actions">
                    <button type="button" className="app-approval-reject" onClick={() => void decide("REJECTED")} disabled={busy}>
                      {busy ? "Saving…" : "Reject"}
                    </button>
                    <button type="button" className="app-approval-approve" onClick={() => void decide("APPROVED")} disabled={busy}>
                      {busy ? "Saving…" : "Approve"}
                    </button>
                  </div>
                  <p className="app-approval-muted">The server rechecks your active role, approval sequence, delegation and institution/department scope when you submit.</p>
                </div>
              )}
            </div>
          )}
        </Panel>
      </div>
    </div>
  );
}
