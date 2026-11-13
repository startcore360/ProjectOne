import { useEffect, useState } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Icon } from "../../../../components/ui/Icon";
import { Panel, StatusBadge, DataTable, Button, Notice } from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import {
  platformOpsService,
  type SupportAccessGrantItem,
  type SupportTicketItem,
} from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";

export function SupportAccessView() {
  const { can } = usePlatformAccess();
  const canUseSupportSession = can("SUPPORT_ACCESS_USE");

  const [grants, setGrants] = useState<SupportAccessGrantItem[]>([]);
  const [tickets, setTickets] = useState<Record<string, SupportTicketItem>>({});
  const [loading, setLoading] = useState(false);
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

  const handleAssume = async (ticketId: string, ticketNumber: string) => {
    try {
      const res = await platformOpsService.assumeSupportSession(ticketId);
      setMessage(`Read-only session assumed for ${ticketNumber}. Token active until ${new Date(res.expiresAt).toLocaleTimeString()}.`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not assume session");
    }
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="Support access"
        description="Support access is ticket-bound, read-only, revocable and limited to 24 hours."
      />

      {message && <Notice tone="success">{message}</Notice>}
      {error && (
        <div className="eds-error-state" role="alert">
          <p className="eds-error-state__message">{error}</p>
          <Button variant="secondary" onClick={() => void loadGrants()}>Retry</Button>
        </div>
      )}

      <div className="platform-policy-callout">
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
        {loading ? (
          <div className="platform-empty-inline">
            <Spinner label="Loading active grants..." />
          </div>
        ) : error ? null : grants.length === 0 ? (
          <p className="platform-empty-inline">No active support sessions or grants currently open.</p>
        ) : (
          <DataTable headers={["Ticket #", "Tenant ID", "Granted At", "Access Ends", "Status", "Actions"]}>
            {grants.map((g) => {
              const ticket = tickets[g.ticketId];
              const isExpired = new Date(g.expiresAt).getTime() < Date.now();
              return (
                <tr key={g.supportAccessGrantId}>
                  <td>
                    <strong>{ticket?.ticketNumber ?? g.ticketId.substring(0, 8)}</strong>
                    {ticket && <small className="platform-cell-sub">{ticket.subject}</small>}
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
