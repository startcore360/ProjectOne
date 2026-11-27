import { useCallback, useEffect, useState } from "react";
import { PageHeader, Button, TextField, TextAreaField, CheckboxField, Notice, Spinner, Panel } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { t10Api } from "../api/t10Api";

const availableChannels = ["IN_APP", "PUSH", "SMS", "EMAIL"] as const;
type Channel = typeof availableChannels[number];

const NO_ACTIONS: string[] = [];

export default function MessageComposerPage() {
  const tenant = useAppSelector((s) => s.auth.user?.tenantId ?? "");
  const institution = useAppSelector((s) => s.tenantPermissions.current?.institutionId ?? "");
  const [recipient, setRecipient] = useState("");
  const [body, setBody] = useState("");
  const [channels, setChannels] = useState<Channel[]>([...availableChannels]);
  const [status, setStatus] = useState("");
  const actions = useAppSelector((s) => s.tenantPermissions.current?.allowedActions ?? NO_ACTIONS);
  const [history, setHistory] = useState<Array<{ id: string; channel: string; status: string; failure_reason: string | null; sent_at: string | null; delivered_at: string | null; created_at: string }>>([]);
  const [historyError, setHistoryError] = useState("");
  const [historyLoading, setHistoryLoading] = useState(false);
  const [busy, setBusy] = useState(false);

  const loadHistory = useCallback(async () => {
    if (!tenant || !institution || !actions.includes("DELIVERY_FAILURES_VIEW")) { setHistoryLoading(false); return; }
    setHistoryLoading(true); setHistory([]); setHistoryError("");
    try { setHistory(await t10Api.dispatchHistory(tenant, institution)); setHistoryError(""); }
    catch { setHistoryError("Dispatch history could not be loaded."); }
    finally { setHistoryLoading(false); }
  }, [tenant, institution, actions]);
  useEffect(() => { void loadHistory(); }, [loadHistory]);

  const send = async () => {
    if (!tenant || !institution || !recipient.trim() || !body.trim() || channels.length === 0) return;
    setBusy(true);
    setStatus("");
    try {
      const result = await t10Api.dispatch(tenant, {
        institutionId: institution,
        recipientUserId: recipient.trim(),
        body: body.trim(),
        channels,
      });
      setStatus(`Dispatch queued: ${result.campaignId}. Contact details are resolved from the recipient's tenant account.`);
      setBody("");
      await loadHistory();
    } catch {
      setStatus("Dispatch could not be queued. Check recipient status, permissions and selected channels.");
    } finally {
      setBusy(false);
    }
  };

  return <main className="app-page">
    <PageHeader title="Communications & Dispatch" description="Send a private message through ordered, configurable delivery channels." />
    <Panel title="Compose message" description="Send direct messages through multiple fall-through notification channels.">
      <form className="app-form" onSubmit={(e) => { e.preventDefault(); void send(); }}>
        <TextField
          id="msg-recipient"
          label="Recipient user ID *"
          value={recipient}
          onChange={(event) => setRecipient(event.target.value)}
          placeholder="Tenant user UUID"
          required
        />
        <TextAreaField
          id="msg-body"
          label="Message *"
          value={body}
          onChange={(event) => setBody(event.target.value)}
          maxLength={2000}
          rows={5}
          required
          placeholder="Write your message here..."
        />
        <div style={{ margin: "var(--space-2) 0" }}>
          <p style={{ fontWeight: "var(--fw-medium)", marginBottom: "var(--space-2)", fontSize: "var(--fs-label)" }}>
            Delivery channels (fallback order)
          </p>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "var(--space-4)" }}>
            {availableChannels.map((channel) => (
              <CheckboxField
                id={`channel-${channel}`}
                key={channel}
                label={channel.replace("_", " ")}
                checked={channels.includes(channel)}
                onChange={(event) =>
                  setChannels((current) =>
                    event.target.checked
                      ? availableChannels.filter((item) => item === channel || current.includes(item))
                      : current.filter((item) => item !== channel)
                  )
                }
              />
            ))}
          </div>
        </div>
        <p style={{ fontSize: "var(--fs-caption)", color: "var(--color-text-muted)" }}>
          Fallback follows In-app → Push → SMS → Email among the selected channels. External gateways must be configured by your deployment administrator.
        </p>
        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button
            type="submit"
            disabled={busy || !tenant || !institution || !recipient.trim() || !body.trim() || channels.length === 0}
            busy={busy}
          >
            Queue dispatch
          </Button>
        </div>
      </form>
      {status && (
        <Notice tone={status.startsWith("Dispatch queued") ? "success" : "danger"} style={{ marginTop: "var(--space-4)" }}>
          {status}
        </Notice>
      )}
    </Panel>
    {actions.includes("DELIVERY_FAILURES_VIEW") && (
      <Panel title="Dispatch history" description="Recent delivery attempts and statuses for this institution.">
        {historyLoading && <Spinner />}
        {historyError && <Notice tone="danger">{historyError}</Notice>}
        {history.length === 0 && !historyError && !historyLoading && (
          <p style={{ color: "var(--color-text-muted)" }}>No dispatch attempts recorded for this institution.</p>
        )}
        {history.length > 0 && (
          <div className="app-table-wrap">
            <table className="eds-table">
              <thead>
                <tr>
                  <th>Channel</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th>Sent</th>
                  <th>Delivered</th>
                  <th>Failure</th>
                </tr>
              </thead>
              <tbody>
                {history.map((item) => (
                  <tr key={item.id}>
                    <td>{item.channel}</td>
                    <td>{item.status}</td>
                    <td>{new Date(item.created_at).toLocaleString()}</td>
                    <td>{item.sent_at ? new Date(item.sent_at).toLocaleString() : "—"}</td>
                    <td>{item.delivered_at ? new Date(item.delivered_at).toLocaleString() : "—"}</td>
                    <td>{item.failure_reason ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    )}
  </main>;
}
