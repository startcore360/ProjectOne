import { useEffect, useState, type FormEvent } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import {
  Button,
  DataTable,
  MetricCard,
  Notice,
  Panel,
  SearchBox,
  StatusBadge,
} from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import {
  platformOpsService,
  type SupportTicketItem,
  type SupportAccessGrantItem,
} from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";

export function TicketsView() {
  const { can } = usePlatformAccess();
  const canManageTickets = can("SUPPORT_TICKET_MANAGE");
  const canUseSupportSession = can("SUPPORT_ACCESS_USE");

  const [tickets, setTickets] = useState<SupportTicketItem[]>([]);
  const [grants, setGrants] = useState<SupportAccessGrantItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  // Resolve Ticket Modal
  const [resolvingTicket, setResolvingTicket] = useState<SupportTicketItem | null>(null);
  const [resolutionNote, setResolutionNote] = useState("");
  const [submittingResolve, setSubmittingResolve] = useState(false);

  // Assumed Session Alert
  const [assumedToken, setAssumedToken] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    setError("");
    try {
      const [tList, gList] = await Promise.all([
        platformOpsService.listSupportTickets(),
        platformOpsService.listActiveGrants().catch(() => []),
      ]);
      setTickets(tList);
      setGrants(gList);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load support tickets");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleUpdateStatus = async (ticket: SupportTicketItem, newStatus: string) => {
    if (newStatus === "RESOLVED" || newStatus === "CLOSED") {
      setResolvingTicket(ticket);
      setResolutionNote("");
      return;
    }

    try {
      await platformOpsService.updateSupportTicket(ticket.supportTicketId, { status: newStatus });
      setMessage(`Ticket ${ticket.ticketNumber} updated to ${newStatus}.`);
      loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update ticket");
    }
  };

  const handleConfirmResolve = async (e: FormEvent) => {
    e.preventDefault();
    if (!resolvingTicket) return;
    setSubmittingResolve(true);
    setError("");
    try {
      await platformOpsService.updateSupportTicket(resolvingTicket.supportTicketId, {
        status: "RESOLVED",
        resolutionNote: resolutionNote.trim() || "Resolved by Platform Support Team.",
      });
      setMessage(`Ticket ${resolvingTicket.ticketNumber} marked as RESOLVED.`);
      setResolvingTicket(null);
      loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to resolve ticket");
    } finally {
      setSubmittingResolve(false);
    }
  };

  const handleAssumeSession = async (ticketId: string, ticketNumber: string) => {
    try {
      const res = await platformOpsService.assumeSupportSession(ticketId);
      setAssumedToken(res.token);
      setMessage(`Assumed read-only session for ticket ${ticketNumber}. Token active until ${new Date(res.expiresAt).toLocaleTimeString()}.`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to assume support session. Ensure tenant granted access.");
    }
  };

  const activeGrantsByTicket = new Set(grants.map((g) => g.ticketId));

  const filteredTickets = tickets.filter((t) => {
    const matchesQuery =
      t.ticketNumber.toLowerCase().includes(query.toLowerCase()) ||
      t.subject.toLowerCase().includes(query.toLowerCase()) ||
      t.raisedByName.toLowerCase().includes(query.toLowerCase());
    const matchesStatus = statusFilter === "All" || t.status === statusFilter;
    return matchesQuery && matchesStatus;
  });

  const openTickets = tickets.filter((t) => t.status === "NEW" || t.status === "ASSIGNED" || t.status === "IN_PROGRESS").length;
  const resolvedTickets = tickets.filter((t) => t.status === "RESOLVED" || t.status === "CLOSED").length;

  return (
    <div className="platform-page">
      <PageHeader
        title="Support Tickets"
        description="Tenant problem tickets, resolution audit trails, and ticket-bound read-only diagnostic session assumption."
      />

      {message && <Notice tone="success">{message}</Notice>}
      {error && <Notice tone="danger">{error}</Notice>}
      {assumedToken && (
        <Notice tone="info">
          <strong>Active Support Session:</strong> Temporary diagnostic session key is bound to this window. Access is logged in the tenant audit log.
        </Notice>
      )}

      <div className="kpi-grid">
        <MetricCard label="Total Tickets" value={tickets.length} note="Platform-wide" mark="book" />
        <MetricCard label="Open Tickets" value={openTickets} note="Awaiting resolution" mark="alert" />
        <MetricCard label="Resolved Tickets" value={resolvedTickets} note="SLA satisfied" mark="check-circle" />
        <MetricCard label="Active Grants" value={grants.length} note="Tenant approved sessions" mark="shield" />
      </div>

      <Panel title="Platform Support Queue" description="Incoming issues raised by tenant representatives">
        <div className="platform-toolbar" style={{ display: "flex", gap: "12px", marginBottom: "16px", flexWrap: "wrap" }}>
          <SearchBox
            value={query}
            onChange={(val) => setQuery(val)}
            placeholder="Search ticket #, subject, or requester..."
          />
          <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
            <span style={{ fontSize: "var(--fs-body-sm)", color: "var(--color-text-muted)" }}>Status:</span>
            {["All", "NEW", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED"].map((st) => (
              <Button
                key={st}
                size="sm"
                variant={statusFilter === st ? "primary" : "secondary"}
                onClick={() => setStatusFilter(st)}
              >
                {st}
              </Button>
            ))}
          </div>
        </div>

        {loading ? (
          <div className="platform-empty-inline">
            <Spinner label="Loading support tickets..." />
          </div>
        ) : filteredTickets.length === 0 ? (
          <p className="platform-empty-inline">No support tickets found matching criteria.</p>
        ) : (
          <DataTable headers={["Ticket #", "Subject", "Requester", "Priority", "Status", "Support Grant", "Actions"]}>
            {filteredTickets.map((t) => {
              const hasGrant = activeGrantsByTicket.has(t.supportTicketId);
              return (
                <tr key={t.supportTicketId}>
                  <td>
                    <strong>{t.ticketNumber}</strong>
                    <small className="platform-cell-sub">
                      {new Date(t.createdAt).toLocaleDateString()}
                    </small>
                  </td>
                  <td style={{ maxWidth: "260px" }}>
                    <strong>{t.subject}</strong>
                    <small className="platform-cell-sub">{t.description}</small>
                    {t.resolutionNote && (
                      <p style={{ margin: "4px 0 0", color: "var(--color-status-success-fg)", fontSize: "12px" }}>
                        Resolution: {t.resolutionNote}
                      </p>
                    )}
                  </td>
                  <td>
                    {t.raisedByName}
                    <small className="platform-cell-sub">Tenant: {t.tenantId.substring(0, 8)}...</small>
                  </td>
                  <td>
                    <span style={{ fontWeight: "bold", color: t.priority === "HIGH" ? "var(--color-status-danger-fg)" : "inherit" }}>
                      {t.priority}
                    </span>
                  </td>
                  <td>
                    <StatusBadge value={t.status} />
                  </td>
                  <td>
                    {hasGrant ? (
                      <span style={{ color: "var(--color-status-success-fg)", fontWeight: "bold", fontSize: "12px" }}>
                        ✓ GRANTED
                      </span>
                    ) : (
                      <span style={{ color: "var(--color-text-muted)", fontSize: "12px" }}>Not granted</span>
                    )}
                  </td>
                  <td>
                    <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                      {hasGrant && canUseSupportSession && (
                        <Button
                          size="sm"
                          variant="primary"
                          onClick={() => handleAssumeSession(t.supportTicketId, t.ticketNumber)}
                        >
                          Assume Session
                        </Button>
                      )}
                      {t.status === "NEW" && canManageTickets && (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => handleUpdateStatus(t, "IN_PROGRESS")}
                        >
                          Start
                        </Button>
                      )}
                      {(t.status === "NEW" || t.status === "IN_PROGRESS") && canManageTickets && (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => handleUpdateStatus(t, "RESOLVED")}
                        >
                          Resolve
                        </Button>
                      )}
                      {t.status === "RESOLVED" && canManageTickets && (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => handleUpdateStatus(t, "CLOSED")}
                        >
                          Close
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </DataTable>
        )}
      </Panel>

      {/* Resolve Modal */}
      {resolvingTicket && (
        <div className="platform-modal-backdrop">
          <div className="platform-modal" role="dialog" aria-label="Resolve Ticket">
            <header>
              <h2>Resolve Ticket · {resolvingTicket.ticketNumber}</h2>
              <p>Mandatory resolution remark required per Rule ck_support_ticket_r1.</p>
            </header>
            <form onSubmit={handleConfirmResolve}>
              <label className="field-label">
                <span>Resolution Note</span>
                <textarea
                  className="platform-input"
                  rows={4}
                  value={resolutionNote}
                  onChange={(e) => setResolutionNote(e.target.value)}
                  placeholder="Describe the diagnostics performed and fix applied..."
                  required
                />
              </label>
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
                <Button variant="secondary" onClick={() => setResolvingTicket(null)} type="button">
                  Cancel
                </Button>
                <Button type="submit" disabled={submittingResolve}>
                  {submittingResolve ? "Saving..." : "Confirm Resolution"}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export const TicketsPage = TicketsView;
