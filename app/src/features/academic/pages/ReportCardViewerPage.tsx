import { useState } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";
import PublishGateBlockModal from "../components/PublishGateBlockModal";

type Card = { report_card_id: string; full_name: string; student_code: string; sgpa: number; cgpa: number; total_credits: number; grade_letter: string; status: string; verification_hash: string };

export default function ReportCardViewerPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [reportCardId, setReportCardId] = useState("");
  const [card, setCard] = useState<Card | null>(null);
  const [blocked, setBlocked] = useState(false);
  const [reason, setReason] = useState("");
  const [feedback, setFeedback] = useState("");

  async function load() {
    if (!tenantId || !reportCardId) return;
    try {
      const response = await api.get<Card>(`/api/tenants/${tenantId}/academic/report-cards/${reportCardId}`);
      setCard(response.data); setFeedback("");
    } catch (error) { setFeedback(error instanceof Error ? error.message : "Unable to load report card."); }
  }
  async function publish() {
    if (!tenantId || !card) return;
    try {
      const response = await api.post<Card>(`/api/tenants/${tenantId}/academic/report-cards/${card.report_card_id}/publish`);
      setCard(response.data); setFeedback("Report card published.");
    } catch (error) {
      const message = error instanceof Error ? error.message : "Publication blocked by a required gate.";
      setReason(message); setBlocked(true); setFeedback("Report card was not published.");
    }
  }

  return <main className="page-container">
    <header><h1>Report card &amp; official transcript</h1><p>View academic results and print the verified transcript.</p></header>
    <label>Report card ID<input value={reportCardId} onChange={(e) => setReportCardId(e.target.value)} /></label>
    <button type="button" onClick={load}>Load report card</button>
    {card && <article className="report-card-print">
      <h2>EDU 360 — Official Academic Transcript</h2>
      <p>Student: {card.full_name} ({card.student_code})</p>
      <dl><dt>SGPA</dt><dd>{card.sgpa}</dd><dt>CGPA</dt><dd>{card.cgpa}</dd><dt>Total credits</dt><dd>{card.total_credits}</dd><dt>Grade</dt><dd>{card.grade_letter}</dd><dt>Status</dt><dd>{card.status}</dd></dl>
      <p>Verification reference: {card.verification_hash}</p>
      <button type="button" onClick={() => window.print()}>Print transcript</button>
      {card.status !== "PUBLISHED" && <button type="button" onClick={publish}>Publish report card</button>}
    </article>}
    {feedback && <p role="status">{feedback}</p>}
    <PublishGateBlockModal open={blocked} reason={reason} onClose={() => setBlocked(false)} />
  </main>;
}
