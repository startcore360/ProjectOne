import { useEffect, useMemo, useState } from "react";
import { Spinner } from "../../../../components/ui";
import { PageHeader } from "../../../../components/layout/PageHeader";
import {
  Button,
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
};

const PAGE_SIZE = 25;

export function AuditLogView() {
  const [status, setStatus] = useState<FetchStatus>("loading");
  const [events, setEvents] = useState<AuditLogItem[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [errorMessage, setErrorMessage] = useState("");
  const [query, setQuery] = useState("");
  const [outcome, setOutcome] = useState("All");
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    let active = true;
    setStatus("loading");
    api
      .get("/platform/audit-logs", { params: { page, size: PAGE_SIZE } })
      .then((res) => {
        if (!active) return;
        setEvents(res.data?.items ?? []);
        setTotalCount(res.data?.totalCount ?? res.data?.items?.length ?? 0);
        setStatus("success");
      })
      .catch((err) => {
        if (!active) return;
        setErrorMessage(readErrorMessage(err, "Could not load audit events."));
        setStatus("error");
      });
    return () => {
      active = false;
    };
  }, [retryCount, page]);

  const filtered = useMemo(
    () =>
      events.filter(
        (e) =>
          (outcome === "All" || titleCaseOutcome(e.outcome) === outcome) &&
          [e.actorKind, e.actorUserId, e.operation, e.actionCode, e.entityType, e.auditOutboxId]
            .filter(Boolean)
            .join(" ")
            .toLowerCase()
            .includes(query.toLowerCase())
      ),
    [events, outcome, query]
  );

  return (
    <div className="app-page">
      <PageHeader
        title="Audit log"
        description="Review app-scope activity, including successful actions and denied attempts."
      />
      <Panel title="Platform audit events" description="Tenant-level events remain within each tenant.">
        {status === "error" && (
          <ErrorState
            message={`Could not load audit events: ${errorMessage}`}
            action={<Button variant="secondary" onClick={() => setRetryCount((c) => c + 1)}>Retry</Button>}
          />
        )}
        <div className="app-toolbar">
          <SearchBox value={query} onChange={setQuery} placeholder="Search actor, action or target" />
          <label className="app-select-label">
            Outcome{" "}
            <select value={outcome} onChange={(e) => setOutcome(e.target.value)}>
              <option>All</option>
              <option>Success</option>
              <option>Denied</option>
            </select>
          </label>
        </div>
        {status === "loading" ? (
          <div className="app-empty-inline">
            <Spinner label="Loading audit events" />
          </div>
        ) : (
          <>
            <DataTable
              headers={["When", "Actor", "Action", "Target", "Outcome"]}
              empty={!filtered.length}
              emptyMessage={
                status === "error" ? "Audit events could not be loaded." : "No platform audit events recorded yet."
              }
            >
              {filtered.map((e) => (
                <tr key={e.auditOutboxId}>
                  <td>{formatTimestamp(e.occurredAt)}</td>
                  <td>
                    <strong>{e.actorKind}</strong>
                    {e.actorUserId && <small className="app-cell-sub">{e.actorUserId}</small>}
                  </td>
                  <td>
                    <strong>{e.actionCode ?? e.operation}</strong>
                    <small className="app-cell-sub">{e.operation}</small>
                  </td>
                  <td>
                    {e.entityType ?? "—"}
                    {e.entityId && <small className="app-cell-sub">{e.entityId}</small>}
                  </td>
                  <td>
                    <StatusBadge value={titleCaseOutcome(e.outcome)} />
                  </td>
                </tr>
              ))}
            </DataTable>
            {(page > 0 || totalCount >= PAGE_SIZE) && (
              <div className="app-pagination">
                <span>Page {page + 1}{totalCount > 0 ? ` · ${totalCount} total` : ""}</span>
                <div className="app-pagination__actions">
                  <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                    Previous
                  </Button>
                  <Button variant="secondary" size="sm" disabled={events.length < PAGE_SIZE} onClick={() => setPage((p) => p + 1)}>
                    Next
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </Panel>
    </div>
  );
}

// Export alias
export const AuditLogPage = AuditLogView;
