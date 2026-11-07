import { useEffect, useMemo, useState } from "react";
import { Spinner } from "../../../../components/ui";
import { PageHeader } from "../../../../components/layout/PageHeader";
import {
  Panel,
  StatusBadge,
  DataTable,
  SearchBox,
  Notice,
} from "../../../../design-system/components";
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

export function AuditLogView() {
  const [status, setStatus] = useState<FetchStatus>("loading");
  const [events, setEvents] = useState<AuditLogItem[]>([]);
  const [errorMessage, setErrorMessage] = useState("");
  const [query, setQuery] = useState("");
  const [outcome, setOutcome] = useState("All");

  useEffect(() => {
    let active = true;
    api
      .get("/platform/audit-logs", { params: { page: 0, size: 100 } })
      .then((res) => {
        if (!active) return;
        setEvents(res.data?.items ?? []);
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
  }, []);

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
    <div className="platform-page">
      <PageHeader
        title="Audit log"
        description="Review platform-scope activity, including successful actions and denied attempts."
      />
      <Panel title="Platform audit events" description="Tenant-level events remain within each tenant.">
        {status === "error" && <Notice tone="danger">Could not load audit events: {errorMessage}</Notice>}
        <div className="platform-toolbar">
          <SearchBox value={query} onChange={setQuery} placeholder="Search actor, action or target" />
          <label className="platform-select-label">
            Outcome{" "}
            <select value={outcome} onChange={(e) => setOutcome(e.target.value)}>
              <option>All</option>
              <option>Success</option>
              <option>Denied</option>
            </select>
          </label>
        </div>
        {status === "loading" ? (
          <div className="platform-empty-inline">
            <Spinner label="Loading audit events" />
          </div>
        ) : (
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
                  {e.actorUserId && <small className="platform-cell-sub">{e.actorUserId}</small>}
                </td>
                <td>
                  <strong>{e.actionCode ?? e.operation}</strong>
                  <small className="platform-cell-sub">{e.operation}</small>
                </td>
                <td>
                  {e.entityType ?? "—"}
                  {e.entityId && <small className="platform-cell-sub">{e.entityId}</small>}
                </td>
                <td>
                  <StatusBadge value={titleCaseOutcome(e.outcome)} />
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Panel>
    </div>
  );
}

// Export alias
export const AuditLogPage = AuditLogView;
