import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";

type Assignment = { assignment_id: string; title: string; max_marks: number; due_at: string; status: string; section_id: string };

export default function AssignmentManagementPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [rows, setRows] = useState<Assignment[]>([]);
  const [institutionId, setInstitutionId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [courseOfferingId, setCourseOfferingId] = useState("");
  const [title, setTitle] = useState("");
  const [maxMarks, setMaxMarks] = useState("100");
  const [dueAt, setDueAt] = useState("");
  const [allowLate, setAllowLate] = useState(false);
  const [penalty, setPenalty] = useState("0");
  const [feedback, setFeedback] = useState("");
  const [busy, setBusy] = useState(false);

  async function load() {
    if (!tenantId) return;
    try {
      const response = await api.get<Assignment[]>(`/api/tenants/${tenantId}/academic/assignments`);
      setRows(response.data);
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : "Unable to load assignments.");
    }
  }
  useEffect(() => { void load(); }, [tenantId]);

  async function create(event: FormEvent) {
    event.preventDefault();
    if (!tenantId) return;
    setBusy(true); setFeedback("");
    try {
      await api.post(`/api/tenants/${tenantId}/academic/assignments`, {
        institutionId, sectionId, courseOfferingId, title, maxMarks: Number(maxMarks),
        dueAt: new Date(dueAt).toISOString(), allowLateSubmission: allowLateSubmission,
        latePenaltyPercentPerDay: Number(penalty), rubricJson: JSON.stringify([{ criterion: "Overall", weight: 100 }]),
      });
      setTitle(""); await load(); setFeedback("Assignment published.");
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : "Unable to create assignment.");
    } finally { setBusy(false); }
  }

  return <main className="page-container">
    <header><h1>Assignments &amp; Homework</h1><p>Create coursework, monitor deadlines and review submissions.</p></header>
    <form onSubmit={create} className="form-grid">
      <label>Institution ID<input required value={institutionId} onChange={(e) => setInstitutionId(e.target.value)} /></label>
      <label>Section ID<input required value={sectionId} onChange={(e) => setSectionId(e.target.value)} /></label>
      <label>Course offering ID<input required value={courseOfferingId} onChange={(e) => setCourseOfferingId(e.target.value)} /></label>
      <label>Assignment title<input required maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} /></label>
      <label>Maximum marks<input required type="number" min="0.01" step="0.01" value={maxMarks} onChange={(e) => setMaxMarks(e.target.value)} /></label>
      <label>Due date and time<input required type="datetime-local" value={dueAt} onChange={(e) => setDueAt(e.target.value)} /></label>
      <label><input type="checkbox" checked={allowLate} onChange={(e) => setAllowLate(e.target.checked)} /> Allow late submissions</label>
      <label>Late penalty (% of maximum marks per day)<input type="number" min="0" max="100" step="0.01" value={penalty} onChange={(e) => setPenalty(e.target.value)} /></label>
      <button type="submit" disabled={busy}>{busy ? "Publishing…" : "Publish assignment"}</button>
    </form>
    {feedback && <p role="status">{feedback}</p>}
    <h2>Published assignments</h2>
    <table><thead><tr><th>Title</th><th>Section</th><th>Maximum</th><th>Due</th><th>Status</th></tr></thead>
      <tbody>{rows.map((row) => <tr key={row.assignment_id}><td>{row.title}</td><td>{row.section_id}</td><td>{row.max_marks}</td><td>{new Date(row.due_at).toLocaleString()}</td><td>{row.status}</td></tr>)}</tbody>
    </table>
  </main>;
}
