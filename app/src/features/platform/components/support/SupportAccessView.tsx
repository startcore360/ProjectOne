import { useEffect, useState } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Icon } from "../../../../components/ui/Icon";
import { Panel, StatusBadge, DataTable, Button, Notice } from "../../../../design-system/components";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import {
  platformOpsService,
  type SupportAccessGrantItem,
  type SupportTicketItem,
} from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";
import { SUPPORT_SESSION_OPENED_TENANT_ORIGIN_KEY } from "../../../../services/axios";

export function SupportAccessView() {
  const { can } = usePlatformAccess();
  const canUseSupportSession = can("SUPPORT_ACCESS_USE");

  const [grants, setGrants] = useState<SupportAccessGrantItem[]>([]);
  const [tickets, setTickets] = useState<Record<string, SupportTicketItem>>({});
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const loadGrants = async () => {
    setLoading(true);
    setError("");
    try {
      const [gList, tList] = await Promise.all([
        platformOpsService.listActiveGrants(),
        platformOpsService.listSupportTickets(),
      ]);
      setGrants(gList);
      const ticketMap: Record<string, SupportTicketItem> = {};
      tList.forEach((t) => {
        ticketMap[t.supportTicketId] = t;
      });
      setTickets(ticketMap);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load support access grants");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadGrants();
  }, []);

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

  const handleAssume = async (ticketId: string, ticketNumber: string) => {
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
      setMessage(`Opened a read-only session for ${ticketNumber}. It expires at ${new Date(res.expiresAt).toLocaleTimeString()}.`);
    } catch (err: unknown) {
      opened.close();
      setError(err instanceof Error ? err.message : "Could not assume session");
    }
  };

  return (
    <div className="app-page">
      <PageHeader
        title="Support access"
        description="Support access is ticket-bound, read-only, revocable and limited to 24 hours."
      />

      {message && <Notice tone="success">{message}</Notice>}
      {error && (
        <ErrorState
          message={error}
          action={<Button variant="secondary" onClick={() => void loadGrants()}>Retry</Button>}
        />
      )}

      <div className="app-callout">
        <span aria-hidden="true">
          <Icon name="shield" size={15} />
        </span>
        <div>
          <strong>Tenant data remains protected</strong>
          <p>
            Platform roles do not browse tenant records. A support session requires a ticket, a stated purpose and
            explicit tenant approval.
          </p>
        </div>
      </div>

      <Panel title="Active Support Grants" description="Tenant-authorized diagnostic access windows">
        {!error && (
          <DataTable
            headers={["Ticket #", "Tenant ID", "Granted At", "Access Ends", "Status", "Actions"]}
            loading={loading && grants.length === 0}
            loadingLabel="Loading active grants"
            empty={!loading && grants.length === 0}
            emptyMessage="No active support sessions or grants currently open."
          >
            {grants.map((g) => {
              const ticket = tickets[g.ticketId];
              const isExpired = new Date(g.expiresAt).getTime() < Date.now();
              return (
                <tr key={g.supportAccessGrantId}>
                  <td>
                    <strong>{ticket?.ticketNumber ?? g.ticketId.substring(0, 8)}</strong>
                    {ticket && <small className="app-cell-sub">{ticket.subject}</small>}
                  </td>
                  <td>{g.tenantId}</td>
                  <td>{new Date(g.grantedAt).toLocaleString()}</td>
                  <td>{new Date(g.expiresAt).toLocaleString()}</td>
                  <td>
                    <StatusBadge value={isExpired ? "EXPIRED" : "ACTIVE"} />
                  </td>
                  <td>
                    {!isExpired && canUseSupportSession && (
                      <Button
                        size="sm"
                        variant="primary"
                        onClick={() => handleAssume(g.ticketId, ticket?.ticketNumber ?? g.ticketId)}
                      >
                        Assume Session
                      </Button>
                    )}
                  </td>
                </tr>
              );
            })}
          </DataTable>
        )}
      </Panel>
    </div>
  );
}

export const SupportAccessPage = SupportAccessView;
