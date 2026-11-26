import { useCallback, useEffect, useState, type FormEvent } from "react";
import Button from "../../../design-system/components/Button";
import PageHeader from "../../../design-system/components/PageHeader";
import { Panel } from "../../../design-system/components/Panel";
import { useTenantAccess } from "../../profile/hooks/useTenantAccess";
import { useAppSelector } from "../../../store/hooks";
import PrintableIdCard from "../components/PrintableIdCard";
import {
  documentsApi,
  type IdCard,
  type IdCardReplacement,
  type IdCardTemplate,
} from "../api/documentsApi";

const inputClass = "w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100";
const labelClass = "mb-1 block text-sm font-medium text-slate-700";

export default function IdCardManagementPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const { can } = useTenantAccess();
  const canIssue = can("ID_CARD_ISSUE");
  const canManageTemplate = can("ID_CARD_TEMPLATE_MANAGE");
  const canRequestReplacement = can("ID_CARD_REPLACEMENT_REQUEST");
  const canProcessReplacement = can("ID_CARD_REPLACE");
  const canRevoke = can("ID_CARD_REVOKE");
  const canExport = can("ID_CARD_EXPORT");

  const [institutionId, setInstitutionId] = useState("");
  const [templates, setTemplates] = useState<IdCardTemplate[]>([]);
  const [cards, setCards] = useState<IdCard[]>([]);
  const [replacements, setReplacements] = useState<IdCardReplacement[]>([]);
  const [templateName, setTemplateName] = useState("Standard identity card");
  const [layout, setLayout] = useState<"PORTRAIT" | "LANDSCAPE">("PORTRAIT");
  const [fieldsConfigJson, setFieldsConfigJson] = useState('{"showInstitution":true,"showProfileCode":true,"showCardNumber":true,"showIssueDate":true,"showExpiry":true,"showQr":true,"headerColor":"#172554","backgroundColor":"#ffffff","textColor":"#0f172a"}');
  const [subjectType, setSubjectType] = useState<"STUDENT" | "STAFF">("STUDENT");
  const [profileId, setProfileId] = useState("");
  const [templateId, setTemplateId] = useState("");
  const [expiryDate, setExpiryDate] = useState("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [previewSvg, setPreviewSvg] = useState("");
  const [previewTitle, setPreviewTitle] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!tenantId || !institutionId.trim()) return;
    setBusy(true);
    setError("");
    try {
      const tasks: Promise<unknown>[] = [];
      if (canIssue || canManageTemplate) {
        tasks.push(documentsApi.idCardTemplates(tenantId, institutionId).then(setTemplates));
      }
      if (canIssue || canRequestReplacement) {
        tasks.push(documentsApi.idCards(tenantId, institutionId).then(setCards));
      }
      if (canProcessReplacement) {
        tasks.push(documentsApi.replacementRequests(tenantId, institutionId).then(setReplacements));
      }
      await Promise.all(tasks);
    } catch (cause) {
      setError(messageOf(cause, "Unable to load identity-card records. Confirm the institution scope and active role."));
    } finally {
      setBusy(false);
    }
  }, [tenantId, institutionId, canIssue, canManageTemplate, canRequestReplacement, canProcessReplacement]);

  useEffect(() => {
    setTemplates([]);
    setCards([]);
    setReplacements([]);
  }, [institutionId]);

  async function createTemplate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const template = await documentsApi.createIdCardTemplate(tenantId, institutionId, {
        name: templateName, layout, fieldsConfigJson,
      });
      setNotice("Identity-card template created.");
      setTemplateId(template.templateId);
      await load();
    } catch (cause) { setError(messageOf(cause, "Unable to create the identity-card template.")); }
    finally { setBusy(false); }
  }

  async function issueCard(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const card = await documentsApi.issueIdCard(tenantId, {
        institutionId, subjectType, profileId, templateId,
        expiryDate: expiryDate || null,
      });
      setNotice(`Issued ${card.cardNumber} to ${card.fullName}. Save the QR code token securely; the card can be printed again by an authorized issuer.`);
      await load();
      await showCard(card);
    } catch (cause) { setError(messageOf(cause, "Unable to issue this identity card.")); }
    finally { setBusy(false); }
  }

  async function showCard(card: IdCard) {
    if (!tenantId) return;
    try {
      const svg = await documentsApi.idCardSvg(tenantId, card.issuanceId);
      setPreviewSvg(svg);
      setPreviewTitle(`${card.fullName} · ${card.cardNumber}`);
    } catch (cause) { setError(messageOf(cause, "The card was issued, but its printable view could not be loaded.")); }
  }

  async function requestReplacement(card: IdCard) {
    if (!tenantId) return;
    const reason = window.prompt("Describe why this card needs replacement (lost, damaged, or incorrect):");
    if (!reason?.trim()) return;
    const feeReceiptId = window.prompt("Enter the UUID of the paid, non-void fee receipt for this replacement:");
    if (!feeReceiptId?.trim()) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const replacement = await documentsApi.requestReplacement(tenantId, card.issuanceId, {
        reason: reason.trim(), feeReceiptId: feeReceiptId.trim(),
      });
      setNotice(`Replacement request ${replacement.replacementId} submitted.`);
      await load();
    } catch (cause) { setError(messageOf(cause, "Unable to submit the replacement request.")); }
    finally { setBusy(false); }
  }

  async function decideReplacement(replacement: IdCardReplacement, approve: boolean) {
    if (!tenantId) return;
    const note = window.prompt(approve ? "Approval note:" : "Reason for rejection:");
    if (!note?.trim()) return;
    setBusy(true); setError(""); setNotice("");
    try {
      await documentsApi.decideReplacement(tenantId, replacement.replacementId, { approve, note: note.trim() });
      setNotice(approve ? "Replacement approved and a new card issued." : "Replacement request rejected.");
      await load();
    } catch (cause) { setError(messageOf(cause, "Unable to process the replacement request.")); }
    finally { setBusy(false); }
  }

  async function revoke(card: IdCard) {
    if (!tenantId) return;
    const reason = window.prompt("Reason for revoking this card:");
    if (!reason?.trim()) return;
    setBusy(true); setError(""); setNotice("");
    try {
      await documentsApi.revokeIdCard(tenantId, card.issuanceId, reason.trim());
      setNotice(`Card ${card.cardNumber} revoked.`);
      await load();
    } catch (cause) { setError(messageOf(cause, "Unable to revoke this card.")); }
    finally { setBusy(false); }
  }

  async function downloadBatch() {
    if (!tenantId || selectedIds.length === 0) return;
    setBusy(true); setError("");
    try {
      const blob = await documentsApi.idCardsPdf(tenantId, institutionId, selectedIds);
      downloadBlob(blob, "edu360-identity-cards.pdf");
    } catch (cause) { setError(messageOf(cause, "Unable to create the identity-card PDF batch.")); }
    finally { setBusy(false); }
  }

  function printPreview() {
    if (!previewSvg) return;
    const blob = new Blob([previewSvg], { type: "image/svg+xml;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const opened = window.open(url, "_blank", "noopener,noreferrer");
    if (!opened) setError("Your browser blocked the print preview. Allow pop-ups and try again.");
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }

  const selectedCount = selectedIds.length;

  if (!tenantId) return <main className="page-container"><p>Loading account…</p></main>;
  return (
    <main className="mx-auto max-w-6xl space-y-6 p-4 md:p-6">
      <PageHeader title="Identity card management" description="Manage institution templates, issue and replace cards, revoke invalid cards, and print verified batches." />
      {error && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</p>}
      {notice && <p role="status" className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}

      <Panel title="Institution scope" description="All card operations are restricted to the selected institution.">
        <div className="flex flex-wrap items-end gap-3">
          <label className="min-w-[280px] flex-1"><span className={labelClass}>Institution ID</span>
            <input className={inputClass} value={institutionId} onChange={e => setInstitutionId(e.target.value)} placeholder="Institution UUID" />
          </label>
          <Button onClick={() => void load()} busy={busy} disabled={!institutionId.trim()}>Load records</Button>
        </div>
      </Panel>

      {canManageTemplate && <Panel title="Card template management" description="Configure portrait or landscape layouts, visible card fields, and validated hexadecimal theme colours.">
        <form className="grid gap-3 md:grid-cols-2" onSubmit={createTemplate}>
          <label><span className={labelClass}>Template name</span><input className={inputClass} required value={templateName} onChange={e => setTemplateName(e.target.value)} /></label>
          <label><span className={labelClass}>Layout</span><select className={inputClass} value={layout} onChange={e => setLayout(e.target.value as "PORTRAIT" | "LANDSCAPE")}><option value="PORTRAIT">Portrait</option><option value="LANDSCAPE">Landscape</option></select></label>
          <label className="md:col-span-2"><span className={labelClass}>Fields and theme configuration (JSON)</span><textarea className={inputClass + " min-h-24 font-mono"} value={fieldsConfigJson} onChange={e => setFieldsConfigJson(e.target.value)} /></label>
          <div><Button type="submit" busy={busy}>Create template</Button></div>
        </form>
        <ul className="mt-4 divide-y divide-slate-100">
          {templates.map(template => <li key={template.templateId} className="py-2 text-sm">{template.name} · {template.layout} · {template.status}</li>)}
        </ul>
      </Panel>}

      {canIssue && <Panel title="Issue a card" description="Issue an identity card to a student or staff member in this institution.">
        <form className="grid gap-3 md:grid-cols-2" onSubmit={issueCard}>
          <label><span className={labelClass}>Subject type</span><select className={inputClass} value={subjectType} onChange={e => setSubjectType(e.target.value as "STUDENT" | "STAFF")}><option value="STUDENT">Student</option><option value="STAFF">Staff</option></select></label>
          <label><span className={labelClass}>Student / staff profile ID</span><input className={inputClass} required value={profileId} onChange={e => setProfileId(e.target.value)} /></label>
          <label><span className={labelClass}>Template</span><select className={inputClass} required value={templateId} onChange={e => setTemplateId(e.target.value)}><option value="">Select a template</option>{templates.map(t => <option key={t.templateId} value={t.templateId}>{t.name}</option>)}</select></label>
          <label><span className={labelClass}>Expiry date (optional)</span><input className={inputClass} type="date" value={expiryDate} onChange={e => setExpiryDate(e.target.value)} /></label>
          <div><Button type="submit" busy={busy} disabled={!institutionId || !templateId || !profileId}>Issue card</Button></div>
        </form>
      </Panel>}

      {(canIssue || canRequestReplacement || canExport) && <Panel title="Issued cards" description="Cardholder details and status are scoped to this institution." action={canExport ? <Button size="sm" variant="secondary" onClick={() => void downloadBatch()} disabled={busy || selectedCount === 0}>Download PDF ({selectedCount})</Button> : undefined}>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead><tr className="border-b border-slate-200 text-slate-600"><th className="p-2">Select</th><th className="p-2">Card number</th><th className="p-2">Cardholder</th><th className="p-2">Profile</th><th className="p-2">Status</th><th className="p-2">Expiry</th><th className="p-2">Actions</th></tr></thead>
            <tbody>{cards.map(card => <tr key={card.issuanceId} className="border-b border-slate-100">
              <td className="p-2"><input aria-label={`Select ${card.cardNumber}`} type="checkbox" disabled={card.status!=="ACTIVE" || (!!card.expiryDate && card.expiryDate < new Date().toISOString().slice(0,10))} checked={selectedIds.includes(card.issuanceId)} onChange={e => setSelectedIds(ids => e.target.checked ? [...ids, card.issuanceId] : ids.filter(id => id !== card.issuanceId))} /></td>
              <td className="p-2 font-medium">{card.cardNumber}</td><td className="p-2">{card.fullName}</td><td className="p-2">{card.profileCode}</td><td className="p-2">{card.status}</td><td className="p-2">{card.expiryDate ?? "No expiry"}</td>
              <td className="p-2"><div className="flex flex-wrap gap-2">
                <Button size="sm" variant="secondary" onClick={() => void showCard(card)}>Preview</Button>
                {(canRequestReplacement || canProcessReplacement) && card.status === "ACTIVE" && <Button size="sm" variant="secondary" onClick={() => void requestReplacement(card)}>Request replacement</Button>}
                {canRevoke && card.status === "ACTIVE" && <Button size="sm" variant="danger" onClick={() => void revoke(card)}>Revoke</Button>}
              </div></td>
            </tr>)}
            {cards.length === 0 && <tr><td colSpan={7} className="p-4 text-center text-slate-500">No identity cards loaded.</td></tr>}</tbody>
          </table>
        </div>
      </Panel>}

      {canProcessReplacement && <Panel title="Replacement request queue" description="Approve or reject replacement requests after the backend revalidates the paid, non-void fee receipt.">
        <div className="space-y-3">
          {replacements.map(request => <div key={request.replacementId} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 p-3">
            <div><p className="font-medium">{request.replacementId}</p><p className="text-sm text-slate-600">{request.reason} · {request.status}</p></div>
            {request.status === "REQUESTED" && <div className="flex gap-2"><Button size="sm" onClick={() => void decideReplacement(request, true)} busy={busy}>Approve</Button><Button size="sm" variant="danger" onClick={() => void decideReplacement(request, false)} busy={busy}>Reject</Button></div>}
          </div>)}
          {replacements.length === 0 && <p className="text-sm text-slate-500">No replacement requests loaded.</p>}
        </div>
      </Panel>}

      {previewSvg && <div className="space-y-3"><PrintableIdCard svg={previewSvg} title={previewTitle} /><div className="flex justify-end"><Button onClick={printPreview}>Open print preview</Button></div></div>}
    </main>
  );
}

function messageOf(error: unknown, fallback: string) {
  return (error as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback;
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
