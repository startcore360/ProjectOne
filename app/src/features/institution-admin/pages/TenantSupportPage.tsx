import { useCallback, useEffect, useState, type FormEvent } from "react";
import axios from "axios";
import api from "../../../services/axios";
import { getApiErrorMessage } from "../../../services/apiHelper";
import { useAppSelector } from "../../../store/hooks";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, Panel, TextField, TextAreaField, SelectField } from "../../../design-system/components";

type Ticket = {
  supportTicketId: string; ticketNumber: string; subject: string; description: string;
  priority: string; status: string; createdAt: string;
};
type Grant = {
  supportAccessGrantId: string; ticketId: string; grantedAt: string; expiresAt: string;
  withdrawnAt?: string | null; institutionId?: string | null;
};

export default function TenantSupportPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [grants, setGrants] = useState<Grant[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [subject, setSubject] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState("MEDIUM");
  const [ticketId, setTicketId] = useState("");
  const [durationHours, setDurationHours] = useState("24");
  const [institutionId, setInstitutionId] = useState("");

  const load = useCallback(async () => {
    if (!tenantId) { setLoading(false); return; }
    setLoading(true); setError("");
    try {
      const [ticketResponse, grantResponse] = await Promise.all([
        api.get<Ticket[]>(`/tenants/${tenantId}/support-tickets`),
        api.get<Grant[]>(`/tenants/${tenantId}/support-access-grants`),
      ]);
      setTickets(ticketResponse.data);
      setGrants(grantResponse.data);
      setTicketId((current) => current || ticketResponse.data[0]?.supportTicketId || "");
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally { setLoading(false); }
  }, [tenantId]);

  useEffect(() => { void load(); }, [load]);

  const raiseTicket = async (event: FormEvent) => {
    event.preventDefault(); setError(""); setMessage("");
    if (!subject.trim() || !description.trim()) {
      setError("Subject and description are required."); return;
    }
    setSubmitting(true);
    try {
      const response = await api.post<Ticket>(`/tenants/${tenantId}/support-tickets`, {
        subject: subject.trim(), description: description.trim(), priority,
      });
      setSubject(""); setDescription("");
      setMessage(`Support ticket ${response.data.ticketNumber} was submitted.`);
      await load();
    } catch (err) { setError(getApiErrorMessage(err)); }
    finally { setSubmitting(false); }
  };

  const grantAccess = async (event: FormEvent) => {
    event.preventDefault(); setError(""); setMessage("");
    const hours = Number(durationHours);
    if (!ticketId || !Number.isInteger(hours) || hours < 1 || hours > 24) {
      setError("Choose one of your tickets and a duration from 1 to 24 hours."); return;
    }
    setSubmitting(true);
    try {
      await api.post(`/tenants/${tenantId}/support-access-grants`, {
        ticketId, durationHours: hours, ...(institutionId.trim() ? { institutionId: institutionId.trim() } : {}),
      });
      setInstitutionId("");
      setMessage(`Read-only support access was granted for ${hours} hour(s).`);
      await load();
    } catch (err) { setError(getApiErrorMessage(err)); }
    finally { setSubmitting(false); }
  };

  const withdraw = async (grantId: string) => {
    setError(""); setMessage(""); setSubmitting(true);
    try {
      await api.post(`/tenants/${tenantId}/support-access-grants/${grantId}/withdrawal`);
      setMessage("Support access was withdrawn immediately.");
      await load();
    } catch (err) { setError(getApiErrorMessage(err)); }
    finally { setSubmitting(false); }
  };

  const forbidden = axios.isAxiosError(error) && (error.response?.status === 403 || error.response?.status === 404);
  return (
    <div className="platform-page">
      <PageHeader title="Tenant support" description="Raise support tickets and control temporary, read-only access to your institution's data." />
      {message && <div className="platform-inline-notice" role="status">{message}</div>}
      {error && <div className="eds-error-state" role="alert"><h3>{forbidden ? "Access unavailable" : "Could not complete this request"}</h3><p>{error}</p></div>}
      {!tenantId ? <div className="eds-error-state" role="alert">A tenant sign-in is required to use support.</div> : (
        <>
          <Panel title="Raise a support ticket" description="Describe the issue. Do not include passwords, one-time codes or other secrets.">
            <form onSubmit={raiseTicket} noValidate>
              <TextField id="tenant-support-subject" label="Subject" required value={subject} onChange={(e) => setSubject(e.target.value)} />
              <TextAreaField id="tenant-support-description" label="Description" required value={description} onChange={(e) => setDescription(e.target.value)} />
              <SelectField id="tenant-support-priority" label="Priority" value={priority} onChange={(e) => setPriority(e.target.value)} options={[
                { value: "LOW", label: "Low" }, { value: "MEDIUM", label: "Medium" }, { value: "HIGH", label: "High" }, { value: "URGENT", label: "Urgent" },
              ]} />
              <Button type="submit" busy={submitting}>Submit ticket</Button>
            </form>
          </Panel>

          <Panel title="My support tickets" description="Only tickets raised by your signed-in account are listed.">
            {loading ? <p role="status">Loading tickets…</p> : tickets.length === 0 ? <p>No support tickets have been raised by this account.</p> : (
              <div className="platform-table-wrap"><table className="platform-table">
                <thead><tr><th>Ticket</th><th>Subject</th><th>Status</th><th>Created</th></tr></thead>
                <tbody>{tickets.map((ticket) => <tr key={ticket.supportTicketId}>
                  <td>{ticket.ticketNumber}</td><td>{ticket.subject}</td><td>{ticket.status}</td><td>{new Date(ticket.createdAt).toLocaleString()}</td>
                </tr>)}</tbody>
              </table></div>
            )}
          </Panel>

          <Panel title="Grant temporary support access" description="Grants are ticket-bound, read-only, and expire automatically after at most 24 hours. You can withdraw them at any time.">
            <form onSubmit={grantAccess} noValidate>
              <SelectField id="support-grant-ticket" label="Support ticket" required value={ticketId} onChange={(e) => setTicketId(e.target.value)} options={tickets.map((ticket) => ({ value: ticket.supportTicketId, label: `${ticket.ticketNumber} — ${ticket.subject}` }))} />
              <SelectField id="support-grant-duration" label="Duration" value={durationHours} onChange={(e) => setDurationHours(e.target.value)} options={[1,2,4,8,12,24].map((hours) => ({ value: String(hours), label: `${hours} hour(s)` }))} />
              <TextField id="support-grant-institution" label="Institution ID (optional)" value={institutionId} onChange={(e) => setInstitutionId(e.target.value)} hint="Leave blank to grant tenant-wide read-only access." />
              <Button type="submit" busy={submitting} disabled={tickets.length === 0}>Grant read-only access</Button>
            </form>
          </Panel>

          <Panel title="Support access history" description="Review active, expired and withdrawn grants.">
            {loading ? <p role="status">Loading grants…</p> : grants.length === 0 ? <p>No support access grants have been issued.</p> : (
              <div className="platform-table-wrap"><table className="platform-table">
                <thead><tr><th>Ticket</th><th>Granted</th><th>Expires</th><th>Status</th><th>Action</th></tr></thead>
                <tbody>{grants.map((grant) => {
                  const active = !grant.withdrawnAt && new Date(grant.expiresAt).getTime() > Date.now();
                  const status = grant.withdrawnAt ? "WITHDRAWN" : active ? "ACTIVE" : "EXPIRED";
                  return <tr key={grant.supportAccessGrantId}>
                    <td>{tickets.find((t) => t.supportTicketId === grant.ticketId)?.ticketNumber ?? grant.ticketId}</td>
                    <td>{new Date(grant.grantedAt).toLocaleString()}</td>
                    <td>{new Date(grant.expiresAt).toLocaleString()}</td>
                    <td>{status}</td>
                    <td>{active && <Button type="button" size="sm" variant="secondary" disabled={submitting} onClick={() => void withdraw(grant.supportAccessGrantId)}>Withdraw now</Button>}</td>
                  </tr>;
                })}</tbody>
              </table></div>
            )}
          </Panel>
        </>
      )}
    </div>
  );
}
