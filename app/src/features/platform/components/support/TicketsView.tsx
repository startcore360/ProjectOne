import { useCallback, useEffect, useState, type FormEvent } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import {
  Button,
  DataTable,
  MetricCard,
  Modal,
  Notice,
  Panel,
  SearchBox,
  StatusBadge,
} from "../../../../design-system/components";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import { Spinner } from "../../../../components/ui";
import {
  platformOpsService,
  type SupportTicketItem,
  type SupportAccessGrantItem,
} from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";
import { SUPPORT_SESSION_OPENED_TENANT_ORIGIN_KEY } from "../../../../services/axios";

export function TicketsView() {
  const { can } = usePlatformAccess();
  const canManageTickets = can("SUPPORT_TICKET_MANAGE");
  const canUseSupportSession = can("SUPPORT_ACCESS_USE");

  const [tickets, setTickets] = useState<SupportTicketItem[]>([]);
  const [grants, setGrants] = useState<SupportAccessGrantItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const [categoryFilter, setCategoryFilter] = useState("All");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loadError, setLoadError] = useState("");
  const [grantsError, setGrantsError] = useState("");

  // Resolve Ticket Modal
  const [resolvingTicket, setResolvingTicket] = useState<SupportTicketItem | null>(null);
  const [resolutionNote, setResolutionNote] = useState("");
  const [submittingResolve, setSubmittingResolve] = useState(false);
  const [triagingTicket, setTriagingTicket] = useState<SupportTicketItem | null>(null);
  const [triagePriority, setTriagePriority] = useState("MEDIUM");
  const [triageCategory, setTriageCategory] = useState("OTHER");
  const [submittingTriage, setSubmittingTriage] = useState(false);
  const [escalatingTicket, setEscalatingTicket] = useState<SupportTicketItem | null>(null);
  const [escalationTarget, setEscalationTarget] = useState<"ENGINEERING" | "RELEASE_MANAGER">("ENGINEERING");
  const [escalationNote, setEscalationNote] = useState("");
  const [submittingEscalation, setSubmittingEscalation] = useState(false);


  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    setLoadError("");
    setGrantsError("");
    try {
      const [tList, grantResult] = await Promise.all([
        platformOpsService.listSupportTickets(),
        platformOpsService.listActiveGrants().then((items) => ({ items, error: "" })).catch((err: unknown) => ({
          items: [] as SupportAccessGrantItem[],
          error: err instanceof Error ? err.message : "Unable to verify active support grants.",
        })),
      ]);
      setTickets(tList);
      setGrants(grantResult.items);
      setGrantsError(grantResult.error);
    } catch (err: unknown) {
      setLoadError(err instanceof Error ? err.message : "Failed to load support tickets");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    const onSupportSessionEnd = (event: MessageEvent) => {
      const expectedOrigin = window.sessionStorage.getItem(SUPPORT_SESSION_OPENED_TENANT_ORIGIN_KEY);
      if (event.origin !== expectedOrigin || event.data?.type !== "EDU360_SUPPORT_SESSION_END") return;
      void platformOpsService.endCurrentSupportSession().then(() => {
        window.sessionStorage.removeItem(SUPPORT_SESSION_OPENED_TENANT_ORIGIN_KEY);
        setMessage("The read-only support session has ended.");
      }).catch((err: unknown) => setError(err instanceof Error ? err.message : "Could not end support session."));
    };
    window.addEventListener("message", onSupportSessionEnd);
    return () => window.removeEventListener("message", onSupportSessionEnd);
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
      void loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update ticket");
    }
  };

  const handleOpenTriage = (ticket: SupportTicketItem) => {
    setTriagingTicket(ticket);
    setTriagePriority(ticket.priority || "MEDIUM");
    setTriageCategory(ticket.category || "OTHER");
  };

  const handleConfirmTriage = async (e: FormEvent) => {
    e.preventDefault();
    if (!triagingTicket) return;
    setSubmittingTriage(true);
    setError("");
    try {
      await platformOpsService.updateSupportTicket(triagingTicket.supportTicketId, {
        priority: triagePriority,
        category: triageCategory,
      });
      setMessage(`Ticket ${triagingTicket.ticketNumber} triaged as ${triageCategory} / ${triagePriority}.`);
      setTriagingTicket(null);
      void loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to triage ticket");
    } finally {
      setSubmittingTriage(false);
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
      void loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to resolve ticket");
    } finally {
      setSubmittingResolve(false);
    }
  };

  const handleConfirmEscalation = async (e: FormEvent) => {
    e.preventDefault(); if (!escalatingTicket) return;
    const note = escalationNote.trim(); if (!note) { setError("An escalation note is required."); return; }
    setSubmittingEscalation(true); setError("");
    try {
      await platformOpsService.escalateSupportTicket(escalatingTicket.supportTicketId, { to: escalationTarget, note });
      setMessage(`Ticket ${escalatingTicket.ticketNumber} escalated to ${escalationTarget.replace("_", " ")}.`);
      setEscalatingTicket(null); setEscalationNote(""); void loadData();
    } catch (err: unknown) { setError(err instanceof Error ? err.message : "Failed to escalate ticket"); }
    finally { setSubmittingEscalation(false); }
  };

  const handleAssumeSession = async (ticketId: string, ticketNumber: string) => {
    const opened = window.open("about:blank", "_blank");
    if (!opened) {
      setError("The tenant tab was blocked by the browser. Allow pop-ups and try again.");
      return;
    }
    try {
      const res = await platformOpsService.assumeSupportSession(ticketId);
      const tenantOrigin = new URL(res.tenantLoginAddress).origin;
      window.sessionStorage.setItem(SUPPORT_SESSION_OPENED_TENANT_ORIGIN_KEY, tenantOrigin);
      const fragment = new URLSearchParams({
        support_session: res.token,
        expires_at: res.expiresAt,
        ticket_number: ticketNumber,
        platform_origin: window.location.origin,
      });
      opened.location.replace(`${tenantOrigin}/support#${fragment.toString()}`);
      setMessage(`Opened a read-only session for ticket ${ticketNumber}. It expires at ${new Date(res.expiresAt).toLocaleTimeString()}.`);
    } catch (err: unknown) {
      opened.close();
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
    // E1-S5: e.g. "Billing" shows upgrade requests from tenants' Users pages.
    const matchesCategory = categoryFilter === "All" || t.category === categoryFilter;
    return matchesQuery && matchesStatus && matchesCategory;
  });

  const openTickets = tickets.filter((t) => ["NEW", "TRIAGED", "ASSIGNED", "ESCALATED"].includes(t.status)).length;
  const resolvedTickets = tickets.filter((t) => t.status === "RESOLVED" || t.status === "CLOSED").length;

  return (
    <div className="app-page">
      <PageHeader
        title="Support Tickets"
        description="Tenant problem tickets, resolution audit trails, and ticket-bound read-only diagnostic session assumption."
      />

      {message && <Notice tone="success">{message}</Notice>}
      {error && <Notice tone="danger">{error}</Notice>}
      {loadError && (
        <ErrorState
          message={loadError}
          action={<Button variant="secondary" size="sm" onClick={() => void loadData()}>Retry</Button>}
        />
      )}
      {grantsError && (
        <ErrorState
          message={`Active support grants could not be verified. Session-assumption actions are disabled until this is resolved. ${grantsError}`}
          action={<Button variant="secondary" size="sm" onClick={() => void loadData()}>Retry</Button>}
        />
      )}

      <div className="kpi-grid">
        <MetricCard label="Total Tickets" value={loadError ? "—" : tickets.length} note={loadError ? "Unavailable" : "app-wide"} mark="book" />
        <MetricCard label="Open Tickets" value={loadError ? "—" : openTickets} note={loadError ? "Unavailable" : "Awaiting resolution"} mark="alert" />
        <MetricCard label="Resolved Tickets" value={loadError ? "—" : resolvedTickets} note={loadError ? "Unavailable" : "SLA satisfied"} mark="check-circle" />
        <MetricCard label="Active Grants" value={grantsError ? "—" : grants.length} note={grantsError ? "Verification unavailable" : "Tenant approved sessions"} mark="shield" />
      </div>

      <Panel title="Platform Support Queue" description="Incoming issues raised by tenant representatives">
        <div className="app-toolbar">
          <SearchBox
            value={query}
            onChange={(val) => setQuery(val)}
            placeholder="Search ticket #, subject, or requester..."
          />
          <label className="app-select-label">
            Status{" "}
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="All">All</option>
              <option value="NEW">New</option>
              <option value="TRIAGED">Triaged</option>
              <option value="ASSIGNED">Assigned</option>
              <option value="ESCALATED">Escalated</option>
              <option value="RESOLVED">Resolved</option>
              <option value="CLOSED">Closed</option>
            </select>
          </label>
          <label className="app-select-label">
            Category{" "}
            <select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)}>
              <option value="All">All</option>
              {["LOGIN", "ACCESS", "DATA", "BILLING", "PERFORMANCE", "BUG", "OTHER"].map((category) => (
                <option key={category} value={category}>
                  {category.charAt(0) + category.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
          </label>
        </div>

        {loading ? (
          <div className="app-empty-inline">
            <Spinner label="Loading support tickets..." />
          </div>
        ) : loadError ? null : filteredTickets.length === 0 ? (
          <p className="app-empty-inline">No support tickets found matching criteria.</p>
        ) : (
          <DataTable headers={["Ticket #", "Subject", "Requester", "Priority", "Status", "Support Grant", "Actions"]}>
            {filteredTickets.map((t) => {
              const hasGrant = activeGrantsByTicket.has(t.supportTicketId);
              return (
                <tr key={t.supportTicketId}>
                  <td>
                    <strong>{t.ticketNumber}</strong>
                    <small className="app-cell-sub">
                      {new Date(t.createdAt).toLocaleDateString()}
                    </small>
                  </td>
                  <td style={{ maxWidth: "260px" }}>
                    <strong>{t.subject}</strong>
                    <small className="app-cell-sub">{t.description}</small>
                    <small className="app-cell-sub">Category: {t.category || "Not triaged"}</small>
                    {t.resolutionNote && (
                      <p style={{ margin: "4px 0 0", color: "var(--color-status-success-fg)", fontSize: "12px" }}>
                        Resolution: {t.resolutionNote}
                      </p>
                    )}
                  </td>
                  <td>
                    {t.raisedByName}
                    <small className="app-cell-sub">Tenant: {t.tenantId.substring(0, 8)}...</small>
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
                        <Button size="sm" variant="secondary" onClick={() => handleOpenTriage(t)}>
                          Triage
                        </Button>
                      )}
                      {t.status === "TRIAGED" && canManageTickets && (
                        <Button size="sm" variant="secondary" onClick={() => handleUpdateStatus(t, "ASSIGNED")}>
                          Assign
                        </Button>
                      )}
                      {(t.status === "TRIAGED" || t.status === "ASSIGNED") && canManageTickets && (
                        <Button size="sm" variant="secondary" onClick={() => { setEscalatingTicket(t); setEscalationTarget("ENGINEERING"); setEscalationNote(""); }}>Escalate</Button>
                      )}
                      {(t.status === "ASSIGNED" || t.status === "ESCALATED") && canManageTickets && (
                        <Button size="sm" variant="secondary" onClick={() => handleUpdateStatus(t, "RESOLVED")}>
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

      <Modal
        isOpen={!!resolvingTicket}
        onClose={() => setResolvingTicket(null)}
        kicker="SUPPORT TICKET"
        title={`Resolve · ${resolvingTicket?.ticketNumber ?? ""}`}
        description="Mandatory resolution remark required per Rule ck_support_ticket_r1."
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setResolvingTicket(null)} disabled={submittingResolve}>
              Cancel
            </Button>
            <Button form="resolve-ticket-form" type="submit" busy={submittingResolve}>
              Confirm resolution
            </Button>
          </>
        }
      >
        <form id="resolve-ticket-form" onSubmit={handleConfirmResolve}>
          <label style={{ display: "block", fontSize: "var(--fs-body-sm)", fontWeight: "var(--fw-medium)", marginBottom: "var(--space-1)" }}>
            Resolution note
          </label>
          <textarea
            rows={4}
            value={resolutionNote}
            onChange={(e) => setResolutionNote(e.target.value)}
            placeholder="Describe the diagnostics performed and fix applied..."
            required
            style={{
              width: "100%",
              padding: "var(--space-2) var(--space-3)",
              border: "1px solid var(--color-border-divider)",
              borderRadius: "var(--radius-md)",
              fontSize: "var(--fs-body)",
              resize: "vertical",
            }}
          />
        </form>
      </Modal>

      <Modal isOpen={!!escalatingTicket} onClose={() => setEscalatingTicket(null)} kicker="SUPPORT ESCALATION"
        title={`Escalate · ${escalatingTicket?.ticketNumber ?? ""}`}
        description="Route this ticket to Engineering or the Release Manager. The Product Owner is always notified."
        size="sm"
        footer={<><Button variant="secondary" onClick={() => setEscalatingTicket(null)} disabled={submittingEscalation}>Cancel</Button><Button form="escalate-ticket-form" type="submit" busy={submittingEscalation}>Confirm escalation</Button></>}>
        <form id="escalate-ticket-form" onSubmit={handleConfirmEscalation}>
          <label>Escalate to</label>
          <select value={escalationTarget} onChange={(e) => setEscalationTarget(e.target.value as "ENGINEERING" | "RELEASE_MANAGER")}>
            <option value="ENGINEERING">Engineering</option><option value="RELEASE_MANAGER">Release Manager</option>
          </select>
          <label>Escalation note</label>
          <textarea rows={4} value={escalationNote} onChange={(e) => setEscalationNote(e.target.value)} placeholder="Explain why this ticket needs escalation..." required />
        </form>
      </Modal>

      <Modal
        isOpen={!!triagingTicket}
        onClose={() => setTriagingTicket(null)}
        kicker="SUPPORT TRIAGE"
        title={`Triage · ${triagingTicket?.ticketNumber ?? ""}`}
        description={triagingTicket?.subject ?? "Set the ticket category and priority."}
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setTriagingTicket(null)} disabled={submittingTriage}>
              Cancel
            </Button>
            <Button form="triage-ticket-form" type="submit" busy={submittingTriage}>
              Save triage
            </Button>
          </>
        }
      >
        <form id="triage-ticket-form" onSubmit={handleConfirmTriage}>
          <label style={{ display: "block", fontSize: "var(--fs-body-sm)", fontWeight: "var(--fw-medium)", marginBottom: "var(--space-1)" }}>
            Category
          </label>
          <select value={triageCategory} onChange={(e) => setTriageCategory(e.target.value)} required>
            {["LOGIN", "ACCESS", "DATA", "BILLING", "PERFORMANCE", "BUG", "OTHER"].map((category) => (
              <option key={category} value={category}>{category}</option>
            ))}
          </select>
          <label style={{ display: "block", fontSize: "var(--fs-body-sm)", fontWeight: "var(--fw-medium)", margin: "12px 0 4px" }}>
            Priority
          </label>
          <select value={triagePriority} onChange={(e) => setTriagePriority(e.target.value)} required>
            {["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((priority) => (
              <option key={priority} value={priority}>{priority}</option>
            ))}
          </select>
        </form>
      </Modal>
    </div>
  );
}

export const TicketsPage = TicketsView;
