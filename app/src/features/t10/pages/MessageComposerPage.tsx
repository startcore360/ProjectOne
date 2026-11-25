import { useState } from "react";
import { PageHeader, Button } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { t10Api } from "../api/t10Api";

const availableChannels = ["IN_APP", "PUSH", "SMS", "EMAIL"] as const;
type Channel = typeof availableChannels[number];

export default function MessageComposerPage() {
  const tenant = useAppSelector((s) => s.auth.user?.tenantId ?? "");
  const institution = useAppSelector((s) => s.tenantPermissions.current?.institutionId ?? "");
  const [recipient, setRecipient] = useState("");
  const [body, setBody] = useState("");
  const [channels, setChannels] = useState<Channel[]>([...availableChannels]);
  const [status, setStatus] = useState("");
  const [busy, setBusy] = useState(false);

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
    } catch {
      setStatus("Dispatch could not be queued. Check recipient status, permissions and selected channels.");
    } finally {
      setBusy(false);
    }
  };

  return <main className="app-page">
    <PageHeader title="Communications & Dispatch" description="Send a private message through ordered, configurable delivery channels." />
    <section className="app-card">
      <label>Recipient user ID
        <input value={recipient} onChange={(event) => setRecipient(event.target.value)} placeholder="Tenant user UUID" />
      </label>
      <label>Message
        <textarea value={body} onChange={(event) => setBody(event.target.value)} maxLength={2000} rows={5} />
      </label>
      <fieldset>
        <legend>Delivery channels (fallback order)</legend>
        {availableChannels.map((channel) => <label key={channel}>
          <input type="checkbox" checked={channels.includes(channel)} onChange={(event) => setChannels((current) =>
            event.target.checked ? availableChannels.filter((item) => item === channel || current.includes(item))
              : current.filter((item) => item !== channel))} />
          {channel.replace("_", " ")}
        </label>)}
      </fieldset>
      <p>Fallback follows In-app → Push → SMS → Email among the selected channels. External gateways must be configured by your deployment administrator.</p>
      <Button onClick={send} disabled={busy || !tenant || !institution || !recipient.trim() || !body.trim() || channels.length === 0}>
        {busy ? "Queueing…" : "Queue dispatch"}
      </Button>
      {status && <p role="status">{status}</p>}
    </section>
  </main>;
}
