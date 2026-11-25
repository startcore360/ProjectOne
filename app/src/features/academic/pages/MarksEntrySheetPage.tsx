import { FormEvent, useState } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";

type Mark = { exam_mark_id: string; student_profile_id: string; marks_obtained: number | null; is_absent: boolean; status: string };

export default function MarksEntrySheetPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [scheduleId, setScheduleId] = useState("");
  const [studentId, setStudentId] = useState("");
  const [marks, setMarks] = useState("");
  const [externalExaminerId, setExternalExaminerId] = useState("");
  const [absent, setAbsent] = useState(false);
  const [markId, setMarkId] = useState("");
  const [moderatedMarks, setModeratedMarks] = useState("");
  const [decision, setDecision] = useState("APPROVED");
  const [comment, setComment] = useState("");
  const [feedback, setFeedback] = useState("");
  const [result, setResult] = useState<Mark | null>(null);
  const [busy, setBusy] = useState(false);

  async function enter(event: FormEvent) {
    event.preventDefault(); if (!tenantId) return;
    setBusy(true); setFeedback("");
    try {
      const response = await api.post(`/api/tenants/${tenantId}/academic/exams/marks`, {
        examScheduleId: scheduleId, studentProfileId: studentId,
        marksObtained: absent ? null : Number(marks), isAbsent: absent,
        externalExaminerUserId: externalExaminerId,
      });
      setResult(response.data); setMarkId(response.data.exam_mark_id); setFeedback("Marks captured; awaiting course-teacher approval.");
    } catch (error) { setFeedback(error instanceof Error ? error.message : "Unable to capture marks."); }
    finally { setBusy(false); }
  }

  async function approveFirstTier() {
    if (!tenantId || !markId) return;
    try {
      const response = await api.post(`/api/tenants/${tenantId}/academic/exams/marks/${markId}/course-teacher-approve`);
      setResult(response.data); setFeedback("Course-teacher tier approved; external moderation is now required.");
    } catch (error) { setFeedback(error instanceof Error ? error.message : "Course-teacher approval failed."); }
  }

  async function moderate(event: FormEvent) {
    event.preventDefault(); if (!tenantId || !markId) return;
    try {
      const response = await api.post(`/api/tenants/${tenantId}/academic/exams/marks/${markId}/external-moderation`, {
        moderatedMarks: absent ? null : Number(moderatedMarks), decision, comment,
      });
      setResult(response.data); setFeedback(`External moderation ${decision.toLowerCase()}.`);
    } catch (error) { setFeedback(error instanceof Error ? error.message : "External moderation failed."); }
  }

  return <main className="page-container">
    <header><h1>Marks entry &amp; moderation</h1><p>Record marks and route them through independent course-teacher and external-examiner approval.</p></header>
    <form onSubmit={enter} className="form-grid">
      <label>Exam schedule ID<input required value={scheduleId} onChange={(e) => setScheduleId(e.target.value)} /></label>
      <label>Student profile ID<input required value={studentId} onChange={(e) => setStudentId(e.target.value)} /></label>
      <label>Marks obtained<input type="number" min="0" step="0.01" disabled={absent} value={marks} onChange={(e) => setMarks(e.target.value)} /></label>
      <label>External examiner user ID<input required value={externalExaminerId} onChange={(e) => setExternalExaminerId(e.target.value)} /></label>
      <label><input type="checkbox" checked={absent} onChange={(e) => setAbsent(e.target.checked)} /> Student absent</label>
      <button disabled={busy}>Save marks</button>
    </form>
    {markId && <section><h2>Moderation workflow</h2><p>Mark record: {markId}</p>
      <button type="button" onClick={approveFirstTier}>Course teacher: approve first tier</button>
      <form onSubmit={moderate} className="form-grid">
        <label>Moderated marks<input type="number" min="0" step="0.01" disabled={absent} value={moderatedMarks} onChange={(e) => setModeratedMarks(e.target.value)} /></label>
        <label>External decision<select value={decision} onChange={(e) => setDecision(e.target.value)}><option value="APPROVED">Approve</option><option value="REJECTED">Reject</option></select></label>
        <label>Examiner comment<textarea value={comment} onChange={(e) => setComment(e.target.value)} /></label>
        <button>Submit external moderation</button>
      </form>
    </section>}
    {result && <p>Current status: <strong>{result.status}</strong></p>}
    {feedback && <p role="status">{feedback}</p>}
  </main>;
}
