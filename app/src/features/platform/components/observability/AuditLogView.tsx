import { useEffect, useMemo, useState } from "react";
import { Spinner } from "../../../../components/ui";
import { PageHeader } from "../../../../components/layout/PageHeader";
import {
  Button,
  Modal,
  Panel,
  StatusBadge,
  DataTable,
  SearchBox,
} from "../../../../design-system/components";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import { formatTimestamp, titleCaseOutcome, readErrorMessage, type FetchStatus } from "../common/types";
import api from "../../../../services/axios";

export type AuditLogItem = {
  auditOutboxId: string;
  tenantId: string | null;
  occurredAt: string;
  actorKind: string;
  actorUserId: string | null;
  operation: string;
  actionCode: string | null;
  entityType: string | null;
  entityId: string | null;
  outcome: string;
  correlationId: string | null;
  eventType?: string | null;
  payload?: Record<string, unknown> | null;
};

type AuditFilters = {
  tenantId: string;
  actorUserId: string;
  actionCode: string;
  outcome: string;
  from: string;
  to: string;
};

const EMPTY_FILTERS: AuditFilters = {
  tenantId: "",
  actorUserId: "",
  actionCode: "",
  outcome: "All",
  from: "",
  to: "",
};
const PAGE_SIZE = 25;

function requestFilters(filters: AuditFilters) {
  return {
    ...(filters.tenantId.trim() ? { tenantId: filters.tenantId.trim() } : {}),
    ...(filters.actorUserId.trim() ? { actorUserId: filters.actorUserId.trim() } : {}),
    ...(filters.actionCode.trim() ? { actionCode: filters.actionCode.trim() } : {}),
    ...(filters.outcome !== "All" ? { outcome: filters.outcome.toUpperCase() } : {}),
    ...(filters.from ? { from: new Date(filters.from).toISOString() } : {}),
    ...(filters.to ? { to: new Date(filters.to).toISOString() } : {}),
  };
}

export function AuditLogView() {
  const [status, setStatus] = useState<FetchStatus>("loading");
  const [events, setEvents] = useState<AuditLogItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [errorMessage, setErrorMessage] = useState("");
  const [query, setQuery] = useState("");
  const [draftFilters, setDraftFilters] = useState<AuditFilters>(EMPTY_FILTERS);
  const [filters, setFilters] = useState<AuditFilters>(EMPTY_FILTERS);
  const [retryCount, setRetryCount] = useState(0);
  const [selectedEvent, setSelectedEvent] = useState<AuditLogItem | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState("");

  const params = useMemo(() => requestFilters(filters), [filters]);

  useEffect(() => {
    let active = true;
    api
      .get("/platform/audit-logs", { params: { page, size: PAGE_SIZE, ...params } })
      .then((res) => {
        if (!active) return;
        const items: AuditLogItem[] = res.data?.items ?? [];
        const total = Number(res.data?.totalElements ?? items.length);
        setEvents(items);
        setTotalElements(total);
        setTotalPages(Number(res.data?.totalPages ?? Math.ceil(total / PAGE_SIZE)));
        setStatus("success");
      })
      .catch((err) => {
        if (!active) return;
        setErrorMessage(readErrorMessage(err, "Could not load audit events."));
        setStatus("error");
      });
    return () => { active = false; };
  }, [retryCount, page, params]);

  const filtered = useMemo(
    () => events.filter((event) =>
      [event.actorKind, event.actorUserId, event.operation, event.actionCode, event.entityType,
        event.entityId, event.eventType, event.auditOutboxId]
        .filter(Boolean).join(" ").toLowerCase().includes(query.toLowerCase())),
    [events, query],
  );

  const updateDraft = (field: keyof AuditFilters, value: string) => {
    setDraftFilters((current) => ({ ...current, [field]: value }));
  };

  const applyFilters = () => {
    setStatus("loading");
    setPage(0);
    setFilters({ ...draftFilters });
  };

  const exportCsv = async () => {
    setExporting(true);
    setExportError("");
    try {
      const response = await api.get("/platform/audit-logs/export", {
        params,
        responseType: "blob",
      });
      const blob = new Blob([response.data], { type: "text/csv;charset=utf-8" });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "platform-audit-logs.csv";
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      const statusCode = (error as { response?: { status?: number } })?.response?.status;
      setExportError(statusCode === 413
        ? "More than 50,000 events match. Narrow the filters before exporting."
        : readErrorMessage(error, "Could not export audit events."));
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="app-page">
      <PageHeader
        title="Audit log"
        description="Review platform actions, filtered by tenant, actor, action, outcome and time range."
      />
      <Panel title="Platform audit events" description="Only platform.audit_outbox is queried; tenant audit logs remain isolated.">
        {status === "error" && (
          <ErrorState
            message={`Could not load audit events: ${errorMessage}`}
            action={<Button variant="secondary" onClick={() => { setStatus("loading"); setRetryCount((c) => c + 1); }}>Retry</Button>}
          />
        )}
        <div className="app-toolbar" style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", alignItems: "end" }}>
          <SearchBox value={query} onChange={setQuery} placeholder="Search this page" />
          <label className="app-select-label">
            Tenant ID
            <input aria-label="Tenant ID" value={draftFilters.tenantId} onChange={(e) => updateDraft("tenantId", e.target.value)} />
          </label>
          <label className="app-select-label">
            Actor user ID
            <input aria-label="Actor user ID" value={draftFilters.actorUserId} onChange={(e) => updateDraft("actorUserId", e.target.value)} />
          </label>
          <label className="app-select-label">
            Action code
            <input aria-label="Action code" value={draftFilters.actionCode} onChange={(e) => updateDraft("actionCode", e.target.value)} />
          </label>
          <label className="app-select-label">
            Outcome
            <select aria-label="Outcome" value={draftFilters.outcome} onChange={(e) => updateDraft("outcome", e.target.value)}>
              <option>All</option>
              <option value="SUCCESS">Success</option>
              <option value="DENIED">Denied</option>
              <option value="FAILED">Failed</option>
            </select>
          </label>
          <label className="app-select-label">
            From
            <input aria-label="From" type="datetime-local" value={draftFilters.from} onChange={(e) => updateDraft("from", e.target.value)} />
          </label>
          <label className="app-select-label">
            To
            <input aria-label="To" type="datetime-local" value={draftFilters.to} onChange={(e) => updateDraft("to", e.target.value)} />
          </label>
          <Button variant="secondary" onClick={applyFilters}>Apply filters</Button>
          <Button variant="secondary" onClick={exportCsv} busy={exporting} disabled={exporting}>Export CSV</Button>
        </div>
        {exportError && <p className="notice notice--danger" role="alert">{exportError}</p>}
        {status === "loading" ? (
          <div className="app-empty-inline"><Spinner label="Loading audit events" /></div>
        ) : (
          <>
            <DataTable
              headers={["When", "Actor", "Action", "Target", "Outcome", "Details"]}
              ariaLabel="Platform audit events"
              pagination={false}
              pageSize={PAGE_SIZE}
              empty={!filtered.length}
              emptyMessage={status === "error" ? "Audit events could not be loaded." : "No platform audit events recorded yet."}
            >
              {filtered.map((event) => (
                <tr key={event.auditOutboxId}>
                  <td>{formatTimestamp(event.occurredAt)}</td>
                  <td>
                    <strong>{event.actorKind}</strong>
                    {event.actorUserId && <small className="app-cell-sub">{event.actorUserId}</small>}
                  </td>
                  <td>
                    <strong>{event.actionCode ?? event.operation}</strong>
                    <small className="app-cell-sub">{event.operation}</small>
                  </td>
                  <td>
                    {event.entityType ?? "—"}
                    {event.entityId && <small className="app-cell-sub">{event.entityId}</small>}
                  </td>
                  <td><StatusBadge value={titleCaseOutcome(event.outcome)} /></td>
                  <td><Button variant="secondary" size="sm" onClick={() => setSelectedEvent(event)}>Details</Button></td>
                </tr>
              ))}
            </DataTable>
            {totalPages > 1 && (
              <nav className="app-pagination" aria-label="Audit log pagination">
                <span className="app-pagination__range">
                  Showing {page * PAGE_SIZE + 1}–{page * PAGE_SIZE + events.length} of {totalElements}
                </span>
                <div className="app-pagination__actions">
                  <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => { setStatus("loading"); setPage((p) => Math.max(0, p - 1)); }}>Previous</Button>
                  <span aria-live="polite">Page {page + 1} of {totalPages}</span>
                  <Button variant="secondary" size="sm" disabled={page + 1 >= totalPages} onClick={() => { setStatus("loading"); setPage((p) => p + 1); }}>Next</Button>
                </div>
              </nav>
            )}
          </>
        )}
      </Panel>

      {selectedEvent && (
        <Modal isOpen onClose={() => setSelectedEvent(null)} title="Audit event details" kicker="Platform audit" size="lg">
          <dl className="app-detail-list">
            <dt>Event type</dt><dd>{selectedEvent.eventType ?? "—"}</dd>
            <dt>Audit ID</dt><dd>{selectedEvent.auditOutboxId}</dd>
            <dt>Correlation ID</dt><dd>{selectedEvent.correlationId ?? "—"}</dd>
            <dt>Tenant ID</dt><dd>{selectedEvent.tenantId ?? "—"}</dd>
            <dt>Actor</dt><dd>{selectedEvent.actorUserId ?? selectedEvent.actorKind}</dd>
          </dl>
          <h3>Payload</h3>
          <pre style={{ maxHeight: "22rem", overflow: "auto", whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
            {selectedEvent.payload ? JSON.stringify(selectedEvent.payload, null, 2) : "No payload recorded."}
          </pre>
        </Modal>
      )}
    </div>
  );
}

export const AuditLogPage = AuditLogView;
