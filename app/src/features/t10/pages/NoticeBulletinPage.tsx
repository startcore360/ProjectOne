import { useCallback, useEffect, useState } from "react";
import { PageHeader, Button, EmptyState } from "../../../design-system/components";
import { useAppSelector } from "../../../store/hooks";
import { t10Api, type Notice } from "../api/t10Api";

export default function NoticeBulletinPage() {
  const tenant = useAppSelector((s) => s.auth.user?.tenantId ?? "");
  const institution = useAppSelector((s) => s.tenantPermissions.current?.institutionId ?? "");
  const actions = useAppSelector((s) => s.tenantPermissions.current?.allowedActions ?? []);
  const canPublish = ["NOTICE_PUBLISH_INSTITUTION","NOTICE_PUBLISH_CLASS_DEPT","NOTICE_PUBLISH_UNIVERSITY"].some((action) => actions.includes(action));
  const canPublishUrgent = actions.includes("NOTICE_EMERGENCY");
  const [items, setItems] = useState<Notice[]>([]);
  const [error, setError] = useState("");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [targetRolesText, setTargetRolesText] = useState("");
  const [targetKind, setTargetKind] = useState<"ROLE" | "INSTITUTION">("ROLE");
  const [urgent, setUrgent] = useState(false);
  const [requiresAcknowledgment, setRequiresAcknowledgment] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    if (tenant && institution) {
      t10Api.notices(tenant, institution).then(setItems).catch(() => setError("Notices could not be loaded."));
    }
  }, [tenant, institution]);
  useEffect(() => { load(); }, [load]);

  const publish = async () => {
    if (!tenant || !institution || !title.trim() || !body.trim()) return;
    setBusy(true);
    setError("");
    try {
      const targetRoles = targetRolesText.split(",").map((role) => role.trim().toUpperCase()).filter(Boolean);
      await t10Api.publishNotice(tenant, {
        institutionId: institution, title: title.trim(), body: body.trim(), targetRoles,
        targetKind, targetId: targetKind === "INSTITUTION" ? institution : null,
        urgent, requiresAcknowledgment,
      });
      setTitle(""); setBody(""); setTargetRolesText(""); setUrgent(false); setRequiresAcknowledgment(false);
      load();
    } catch {
      setError("Notice could not be published. Check your permission, target roles and institution scope.");
    } finally {
      setBusy(false);
    }
  };

  return <main className="app-page">
    <PageHeader title="Notice Board & Circulars" description="Publish role-targeted circulars, urgent alerts and notices requiring acknowledgment." />
    {error && <p role="alert">{error}</p>}
    {canPublish && <section className="app-card">
      <h2>Publish a notice</h2>
      <label>Title <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={160} required /></label>
      <label>Message <textarea value={body} onChange={(event) => setBody(event.target.value)} maxLength={4000} required /></label>
      <label>Target scope
        <select value={targetKind} onChange={(event) => setTargetKind(event.target.value as "ROLE" | "INSTITUTION")}>
          <option value="ROLE">Role-scoped</option><option value="INSTITUTION">Entire institution</option>
        </select>
      </label>
      <label>Target role codes (comma-separated; blank targets all roles)
        <input value={targetRolesText} onChange={(event) => setTargetRolesText(event.target.value)} placeholder="STUDENT, CLASS_TEACHER, PRINCIPAL_COLLEGE" />
      </label>
      {canPublishUrgent && <label><input type="checkbox" checked={urgent} onChange={(event) => setUrgent(event.target.checked)} /> Urgent alert</label>}
      <label><input type="checkbox" checked={requiresAcknowledgment} onChange={(event) => setRequiresAcknowledgment(event.target.checked)} /> Require read acknowledgment</label>
      <Button onClick={publish} disabled={busy || !title.trim() || !body.trim() || !institution}>
        {busy ? "Publishing…" : "Publish notice"}
      </Button>
    </section>}
    <section className="app-stack" aria-label="Published notices">
      {items.length === 0 ? <EmptyState title="No published notices" description="Published notices will appear here." /> :
        items.map((notice) => <article className="app-card" key={notice.notice_id}>
          <h2>{notice.title} {notice.is_urgent && <strong>Urgent</strong>}</h2>
          <p>{notice.body}</p>
          {notice.requires_acknowledgment && !notice.acknowledged &&
            <Button onClick={async () => {
              try { await t10Api.acknowledge(tenant, institution, notice.notice_id); load(); }
              catch { setError("Notice acknowledgment could not be saved. Please try again."); }
            }}>Acknowledge</Button>}
          {notice.requires_acknowledgment && notice.acknowledged && <p>Read acknowledgment recorded.</p>}
        </article>)}
    </section>
  </main>;
}
