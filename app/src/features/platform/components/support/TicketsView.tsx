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
  SelectField,
  StatusBadge,
  TextAreaField,
  toast,
} from "../../../../design-system/components";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import {
  platformOpsService,
  type SupportTicketItem,
  type SupportAccessGrantItem,
} from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";
import { SUPPORT_SESSION_OPENED_TENANT_ORIGIN_KEY } from "../../../../services/axios";
import { readErrorMessage } from "../common/types";

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
  const [loadError, setLoadError] = useState("");
  const [grantsError, setGrantsError] = useState("");

  // Resolve / Close Ticket Modal
  const [resolvingTicket, setResolvingTicket] = useState<SupportTicketItem | null>(null);
  const [resolveTargetStatus, setResolveTargetStatus] = useState<"RESOLVED" | "CLOSED">("RESOLVED");
  const [resolutionNote, setResolutionNote] = useState("");
  const [submittingResolve, setSubmittingResolve] = useState(false);
  const [resolveError, setResolveError] = useState("");

  const [triagingTicket, setTriagingTicket] = useState<SupportTicketItem | null>(null);
  const [triagePriority, setTriagePriority] = useState("MEDIUM");
  const [triageCategory, setTriageCategory] = useState("OTHER");
  const [submittingTriage, setSubmittingTriage] = useState(false);
  const [triageError, setTriageError] = useState("");

  const [escalatingTicket, setEscalatingTicket] = useState<SupportTicketItem | null>(null);
  const [escalationTarget, setEscalationTarget] = useState<"ENGINEERING" | "RELEASE_MANAGER">("ENGINEERING");
  const [escalationNote, setEscalationNote] = useState("");
  const [submittingEscalation, setSubmittingEscalation] = useState(false);
  const [escalateError, setEscalateError] = useState("");


  const loadData = useCallback(async () => {
    setLoading(true);
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
        toast.info("Support session ended", "The read-only support session has ended.");
      }).catch((err: unknown) =>
        toast.error("Could not end support session", err instanceof Error ? err.message : undefined)
      );
    };
    window.addEventListener("message", onSupportSessionEnd);
    return () => window.removeEventListener("message", onSupportSessionEnd);
  }, []);

  const handleOpenResolve = (ticket: SupportTicketItem, targetStatus: "RESOLVED" | "CLOSED" = "RESOLVED") => {
    setResolvingTicket(ticket);
    setResolveTargetStatus(targetStatus);
    setResolutionNote(ticket.resolutionNote || "");
    setResolveError("");
  };

  const handleUpdateStatus = async (ticket: SupportTicketItem, newStatus: string) => {
    if (newStatus === "RESOLVED" || newStatus === "CLOSED") {
      handleOpenResolve(ticket, newStatus as "RESOLVED" | "CLOSED");
      return;
    }

    try {
      await platformOpsService.updateSupportTicket(ticket.supportTicketId, { status: newStatus });
      const msg = `Ticket ${ticket.ticketNumber} updated to ${newStatus}.`;
      toast.success("Ticket updated", msg);
      void loadData();
    } catch (err: unknown) {
      const msg = readErrorMessage(err, "Failed to update ticket");
      toast.error("Update failed", msg);
    }
  };

  const handleOpenTriage = (ticket: SupportTicketItem) => {
    setTriagingTicket(ticket);
    setTriagePriority(ticket.priority || "MEDIUM");
    setTriageCategory(ticket.category || "OTHER");
    setTriageError("");
  };

  const handleOpenEscalate = (ticket: SupportTicketItem) => {
    setEscalatingTicket(ticket);
    setEscalationTarget("ENGINEERING");
    setEscalationNote("");
    setEscalateError("");
  };

  const handleConfirmTriage = async (e: FormEvent) => {
    e.preventDefault();
    if (!triagingTicket) return;
    setSubmittingTriage(true);
    setTriageError("");
    try {
      await platformOpsService.updateSupportTicket(triagingTicket.supportTicketId, {
        priority: triagePriority,
        category: triageCategory,
      });
      const msg = `Ticket ${triagingTicket.ticketNumber} triaged as ${triageCategory} / ${triagePriority}.`;
      toast.success("Ticket triaged", msg);
      setTriagingTicket(null);
      void loadData();
    } catch (err: unknown) {
      const msg = readErrorMessage(err, "Failed to triage ticket. Please verify your permissions and try again.");
      setTriageError(msg);
      toast.error("Triage ticket failed", msg);
    } finally {
      setSubmittingTriage(false);
    }
  };

  const handleConfirmResolve = async (e: FormEvent) => {
    e.preventDefault();
    if (!resolvingTicket) return;
    const note = resolutionNote.trim();
    if (!note) {
      setResolveError(
        resolveTargetStatus === "CLOSED"
          ? "A closing note is required before closing this ticket."
          : "A resolution note is required before marking this ticket as resolved."
      );
      return;
    }
    setSubmittingResolve(true);
    setResolveError("");
    try {
      await platformOpsService.updateSupportTicket(resolvingTicket.supportTicketId, {
        status: resolveTargetStatus,
        resolutionNote: note,
      });
      const msg = `Ticket ${resolvingTicket.ticketNumber} marked as ${resolveTargetStatus}.`;
      toast.success("Ticket updated", msg);
      setResolvingTicket(null);
      void loadData();
    } catch (err: unknown) {
      const msg = readErrorMessage(
        err,
        `Failed to ${resolveTargetStatus === "CLOSED" ? "close" : "resolve"} ticket. Check that a valid note is provided.`
      );
      setResolveError(msg);
      toast.error("Update ticket failed", msg);
    } finally {
      setSubmittingResolve(false);
    }
  };

  const handleConfirmEscalation = async (e: FormEvent) => {
    e.preventDefault();
    if (!escalatingTicket) return;
    const note = escalationNote.trim();
    if (!note) {
      setEscalateError("An escalation note is required to explain why this ticket requires attention.");
      return;
    }
    setSubmittingEscalation(true);
    setEscalateError("");
    try {
      await platformOpsService.escalateSupportTicket(escalatingTicket.supportTicketId, { to: escalationTarget, note });
      const msg = `Ticket ${escalatingTicket.ticketNumber} escalated to ${escalationTarget.replace("_", " ")}.`;
      toast.success("Ticket escalated", msg);
      setEscalatingTicket(null);
      setEscalationNote("");
      void loadData();
    } catch (err: unknown) {
      const msg = readErrorMessage(
        err,
        "Failed to escalate ticket. Verify that the ticket is open and you have permission to escalate."
      );
      setEscalateError(msg);
      toast.error("Escalation failed", msg);
    } finally {
      setSubmittingEscalation(false);
    }
  };

  const handleAssumeSession = async (ticketId: string, ticketNumber: string) => {
    const opened = window.open("about:blank", "_blank");
    if (!opened) {
      toast.error("Pop-up blocked", "The tenant tab was blocked by the browser. Allow pop-ups and try again.");
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
      toast.success(
        "Support session opened",
        `Opened a read-only session for ticket ${ticketNumber}. It expires at ${new Date(res.expiresAt).toLocaleTimeString()}.`
      );
    } catch (err: unknown) {
      opened.close();
      toast.error(
        "Support session failed",
        readErrorMessage(
          err,
          "Failed to assume support session. Ensure the tenant has granted active support access."
        )
      );
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
        <div style={{ display: "flex", gap: "var(--space-3)", flexWrap: "wrap", alignItems: "center", marginBottom: "var(--space-4)" }}>
          <div className="app-filter-tabs" role="tablist" aria-label="Filter tickets by status">
            {(["All", "NEW", "TRIAGED", "ASSIGNED", "ESCALATED", "RESOLVED", "CLOSED"] as const).map((st) => {
              const count = st === "All" ? tickets.length : tickets.filter((t) => t.status === st).length;
              const label = st === "All" ? "All" : st.charAt(0) + st.slice(1).toLowerCase();
              return (
                <button
                  key={st}
                  type="button"
                  role="tab"
                  aria-selected={statusFilter === st}
                  className={`app-filter-tab ${statusFilter === st ? "app-filter-tab--active" : ""}`}
                  onClick={() => setStatusFilter(st)}
                >
                  {label} ({count})
                </button>
              );
            })}
          </div>
        </div>

        <div className="app-toolbar">
          <SearchBox
            value={query}
            onChange={(val) => setQuery(val)}
            placeholder="Search ticket #, subject, or requester..."
          />
          <label className="app-select-label" style={{ display: "none" }}>
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

        {!loadError && (
          <DataTable
            headers={["Ticket #", "Subject", "Requester", "Priority", "Status", "Support Grant", "Actions"]}
            loading={loading && tickets.length === 0}
            loadingLabel="Loading support tickets"
            empty={!loading && filteredTickets.length === 0}
            emptyMessage="No support tickets found matching criteria."
          >
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
                      <p style={{ margin: "var(--space-1) 0 0", color: "var(--color-status-success-fg)", fontSize: "12px" }}>
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
                    <div style={{ display: "flex", gap: "var(--space-1)", flexWrap: "wrap" }}>
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
                        <Button size="sm" variant="secondary" onClick={() => handleOpenEscalate(t)}>
                          Escalate
                        </Button>
                      )}
                      {(t.status === "ASSIGNED" || t.status === "ESCALATED") && canManageTickets && (
                        <Button size="sm" variant="secondary" onClick={() => handleOpenResolve(t, "RESOLVED")}>
                          Resolve
                        </Button>
                      )}
                      {t.status === "RESOLVED" && canManageTickets && (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => handleOpenResolve(t, "CLOSED")}
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
        title={`${resolveTargetStatus === "CLOSED" ? "Close" : "Resolve"} · ${resolvingTicket?.ticketNumber ?? ""}`}
        titleId="resolve-ticket-title"
        description={
          resolveTargetStatus === "CLOSED"
            ? "Mandatory closing remarks required to close this ticket (Rule ck_support_ticket_r1)."
            : "Mandatory resolution remark required per Rule ck_support_ticket_r1."
        }
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setResolvingTicket(null)} disabled={submittingResolve}>
              Cancel
            </Button>
            <Button form="resolve-ticket-form" type="submit" busy={submittingResolve}>
              {resolveTargetStatus === "CLOSED" ? "Confirm closure" : "Confirm resolution"}
            </Button>
          </>
        }
      >
        <form id="resolve-ticket-form" onSubmit={handleConfirmResolve}>
          {resolveError && (
            <div style={{ marginBottom: "var(--space-3)" }}>
              <Notice tone="danger" onDismiss={() => setResolveError("")}>{resolveError}</Notice>
            </div>
          )}
          <TextAreaField
            id="resolve-resolution-note"
            label={resolveTargetStatus === "CLOSED" ? "Closing note" : "Resolution note"}
            rows={4}
            value={resolutionNote}
            onChange={(e) => setResolutionNote(e.target.value)}
            placeholder={
              resolveTargetStatus === "CLOSED"
                ? "Describe any final closing remarks, confirmation from user, or resolution outcome..."
                : "Describe the diagnostics performed and fix applied..."
            }
            required
          />
        </form>
      </Modal>

      <Modal
        isOpen={!!escalatingTicket}
        onClose={() => setEscalatingTicket(null)}
        kicker="SUPPORT ESCALATION"
        title={`Escalate · ${escalatingTicket?.ticketNumber ?? ""}`}
        titleId="escalate-ticket-title"
        description="Route this ticket to Engineering or the Release Manager. The Product Owner is always notified."
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setEscalatingTicket(null)} disabled={submittingEscalation}>
              Cancel
            </Button>
            <Button form="escalate-ticket-form" type="submit" busy={submittingEscalation}>
              Confirm escalation
            </Button>
          </>
        }
      >
        <form id="escalate-ticket-form" onSubmit={handleConfirmEscalation}>
          {escalateError && (
            <div style={{ marginBottom: "var(--space-3)" }}>
              <Notice tone="danger" onDismiss={() => setEscalateError("")}>{escalateError}</Notice>
            </div>
          )}
          <div style={{ display: "grid", gap: "var(--space-3)" }}>
            <SelectField
              id="escalate-target"
              label="Escalate to"
              value={escalationTarget}
              onChange={(e) => setEscalationTarget(e.target.value as "ENGINEERING" | "RELEASE_MANAGER")}
              options={[
                { value: "ENGINEERING", label: "Engineering" },
                { value: "RELEASE_MANAGER", label: "Release Manager" },
              ]}
            />
            <TextAreaField
              id="escalate-note"
              label="Escalation note"
              rows={4}
              value={escalationNote}
              onChange={(e) => setEscalationNote(e.target.value)}
              placeholder="Explain why this ticket needs escalation..."
              required
            />
          </div>
        </form>
      </Modal>

      <Modal
        isOpen={!!triagingTicket}
        onClose={() => setTriagingTicket(null)}
        kicker="SUPPORT TRIAGE"
        title={`Triage · ${triagingTicket?.ticketNumber ?? ""}`}
        titleId="triage-ticket-title"
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
          {triageError && (
            <div style={{ marginBottom: "var(--space-3)" }}>
              <Notice tone="danger" onDismiss={() => setTriageError("")}>{triageError}</Notice>
            </div>
          )}
          <div style={{ display: "grid", gap: "var(--space-3)" }}>
            <SelectField
              id="triage-category"
              label="Category"
              value={triageCategory}
              onChange={(e) => setTriageCategory(e.target.value)}
              required
              options={["LOGIN", "ACCESS", "DATA", "BILLING", "PERFORMANCE", "BUG", "OTHER"].map((cat) => ({
                value: cat,
                label: cat.charAt(0) + cat.slice(1).toLowerCase(),
              }))}
            />
            <SelectField
              id="triage-priority"
              label="Priority"
              value={triagePriority}
              onChange={(e) => setTriagePriority(e.target.value)}
              required
              options={["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((pri) => ({
                value: pri,
                label: pri.charAt(0) + pri.slice(1).toLowerCase(),
              }))}
            />
          </div>
        </form>
      </Modal>
    </div>
  );
}

export const TicketsPage = TicketsView;
