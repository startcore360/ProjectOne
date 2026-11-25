import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent, KeyboardEvent } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";
import MedicalExemptionModal from "../components/MedicalExemptionModal";

type Student = { enrollment_id: string; student_profile_id: string; student_code: string; full_name: string };
type Status = "PRESENT" | "ABSENT" | "ON_APPROVED_LEAVE" | "NOT_HELD" | "";

export default function AttendanceRegisterPage() {
  const tenantId = useAppSelector((s) => s.auth.user?.tenantId);
  const [institutionId, setInstitutionId] = useState("");
  const [academicSessionId, setAcademicSessionId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [slotId, setSlotId] = useState("");
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [sessionType, setSessionType] = useState("DAILY");
  const [students, setStudents] = useState<Student[]>([]);
  const [marks, setMarks] = useState<Record<string, Status>>({});
  const [notHeld, setNotHeld] = useState(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [sessionId, setSessionId] = useState("");
  const [threshold, setThreshold] = useState("");
  const [departmentId, setDepartmentId] = useState("");
  const [medicalFor, setMedicalFor] = useState<string | null>(null);

  const loadRoster = useCallback(async () => {
    if (!tenantId || !institutionId || !sectionId) return;
    setBusy(true); setFeedback("");
    try {
      const res = await api.get<Student[]>("/api/tenants/" + tenantId + "/attendance/sections/" + sectionId + "/roster", { params: { institutionId } });
      setStudents(res.data);
      setMarks(Object.fromEntries(res.data.map((s) => [s.enrollment_id, ""])));
    } catch (e) { setFeedback(e instanceof Error ? e.message : "Unable to load class roster."); }
    finally { setBusy(false); }
  }, [tenantId, institutionId, sectionId]);

  useEffect(() => { void loadRoster(); }, [loadRoster]);

  const counts = useMemo(() => ({
    present: Object.values(marks).filter((v) => v === "PRESENT").length,
    absent: Object.values(marks).filter((v) => v === "ABSENT").length,
    leave: Object.values(marks).filter((v) => v === "ON_APPROVED_LEAVE").length,
  }), [marks]);

  function setMark(enrollmentId: string, status: Status) {
    setMarks((current) => ({ ...current, [enrollmentId]: status }));
  }

  function keyboard(enrollmentId: string, event: KeyboardEvent<HTMLTableRowElement>) {
    const key = event.key.toUpperCase();
    const map: Record<string, Exclude<Status, "">> = { P: "PRESENT", A: "ABSENT", O: "ON_APPROVED_LEAVE" };
    if (map[key]) { event.preventDefault(); setMark(enrollmentId, map[key]); }
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); if (!tenantId) return;
    setBusy(true); setFeedback("");
    try {
      const entries = students.map((student) => ({
        enrollmentId: student.enrollment_id, studentProfileId: student.student_profile_id,
        status: notHeld ? "NOT_HELD" : marks[student.enrollment_id],
      }));
      const response = await api.post("/api/tenants/" + tenantId + "/attendance/sessions", {
        institutionId, academicSessionId, sectionId, timetableSlotId: slotId || null,
        sessionDate: date, sessionType, notHeld, entries,
      });
      setSessionId(response.data.attendance_session_id);
      setFeedback("Attendance session saved as a draft. Submit it to lock the register.");
    } catch (e) { setFeedback(e instanceof Error ? e.message : "Unable to save attendance."); }
    finally { setBusy(false); }
  }

  async function saveThreshold(event: FormEvent) {
    event.preventDefault(); if (!tenantId) return;
    setBusy(true); setFeedback("");
    try {
      await api.put("/api/tenants/" + tenantId + "/attendance/thresholds", {
        institutionId, departmentId: departmentId || null, minimumAttendancePercentage: Number(threshold),
      });
      setFeedback("Attendance threshold settings saved.");
    } catch (e) { setFeedback(e instanceof Error ? e.message : "Unable to save attendance threshold."); }
    finally { setBusy(false); }
  }

  async function submitRegister() {
    if (!tenantId || !sessionId) return;
    setBusy(true);
    try {
      await api.post("/api/tenants/" + tenantId + "/attendance/sessions/" + sessionId + "/submit", null, { params: { institutionId } });
      setFeedback("Attendance submitted successfully.");
    } catch (e) { setFeedback(e instanceof Error ? e.message : "Unable to submit attendance."); }
    finally { setBusy(false); }
  }

  return <main className="page-container">
    <header><h1>Attendance register</h1><p>Capture daily or period attendance. Keyboard shortcuts: P = Present, A = Absent, O = On approved leave. Only the canonical V4.0 statuses are available.</p></header>
    <div className="form-grid">
      <label>Institution ID<input value={institutionId} onChange={(e) => setInstitutionId(e.target.value)} /></label>
      <label>Academic session ID<input value={academicSessionId} onChange={(e) => setAcademicSessionId(e.target.value)} /></label>
      <label>Section ID<input value={sectionId} onChange={(e) => setSectionId(e.target.value)} /></label>
      <label>Date<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
      <label>Session type<select value={sessionType} onChange={(e) => setSessionType(e.target.value)}><option value="DAILY">Daily</option><option value="PERIOD">Period</option></select></label>
      <label>Timetable slot ID (required for period)<input value={slotId} onChange={(e) => setSlotId(e.target.value)} /></label>
      <button type="button" onClick={() => void loadRoster()} disabled={busy}>Load class roster</button>
    </div>
    <section><h2>Exam eligibility threshold (PO-T02)</h2>
      <form onSubmit={saveThreshold} className="form-grid">
        <label>Department ID (leave blank for institution default)<input value={departmentId} onChange={(e) => setDepartmentId(e.target.value)} /></label>
        <label>Minimum attendance %<input type="number" min="0" max="100" step="0.01" required value={threshold} onChange={(e) => setThreshold(e.target.value)} /></label>
        <button disabled={busy || !institutionId}>Save threshold</button>
      </form>
    </section>
    <section><h2>Register summary</h2><p>Present: {counts.present} · Absent: {counts.absent} · On approved leave: {counts.leave} · Total: {students.length}</p></section>
    <form onSubmit={submit}>
      <label><input type="checkbox" checked={notHeld} onChange={(e) => setNotHeld(e.target.checked)} /> Session not held</label>
      <div style={{ overflowX: "auto" }}><table><thead><tr><th>Student</th><th>Present</th><th>Absent</th><th>Approved leave</th><th>Medical exemption</th></tr></thead>
        <tbody>{students.map((student) => <tr key={student.enrollment_id} tabIndex={0} onKeyDown={(event) => keyboard(student.enrollment_id, event)}>
          <td>{student.full_name} <small>({student.student_code})</small></td>
          {(["PRESENT", "ABSENT", "ON_APPROVED_LEAVE"] as Status[]).map((status) => <td key={status}>
            <input type="radio" name={student.enrollment_id} checked={notHeld ? false : marks[student.enrollment_id] === status}
              disabled={notHeld} onChange={() => setMark(student.enrollment_id, status)} aria-label={student.full_name + " " + status} />
          </td>)}
          <td><button type="button" onClick={() => setMedicalFor(student.enrollment_id)}>Request</button></td>
        </tr>)}</tbody></table></div>
      <button disabled={busy || students.length === 0 || !institutionId || !academicSessionId || !sectionId || (sessionType === "PERIOD" && !slotId) || (!notHeld && students.some((student) => !marks[student.enrollment_id]))}>
        {busy ? "Saving…" : "Save attendance"}
      </button>
    </form>
    {feedback && <p role="status">{feedback} {sessionId && feedback.includes("draft") && <button type="button" onClick={() => void submitRegister()} disabled={busy}>Submit register</button>}</p>}
    {medicalFor && tenantId && <MedicalExemptionModal tenantId={tenantId} institutionId={institutionId} enrollmentId={medicalFor}
      onClose={() => setMedicalFor(null)} onSubmitted={() => setFeedback("Medical exemption request submitted for approval.")} />}
  </main>;
}