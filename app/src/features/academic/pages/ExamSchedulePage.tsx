import { useState } from "react";
import type { FormEvent } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";

export default function ExamSchedulePage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [institutionId, setInstitutionId] = useState("");
  const [academicSessionId, setAcademicSessionId] = useState("");
  const [semesterId, setSemesterId] = useState("");
  const [cycleName, setCycleName] = useState("");
  const [cycleType, setCycleType] = useState("INTERNAL");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [cycleId, setCycleId] = useState("");
  const [courseOfferingId, setCourseOfferingId] = useState("");
  const [examDate, setExamDate] = useState("");
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [maxMarks, setMaxMarks] = useState("100");
  const [passMarks, setPassMarks] = useState("40");
  const [roomId, setRoomId] = useState("");
  const [staffUserId, setStaffUserId] = useState("");
  const [scheduleId, setScheduleId] = useState("");
  const [schedules, setSchedules] = useState<Record<string, unknown>[]>([]);
  const [feedback, setFeedback] = useState("");
  const [busy, setBusy] = useState(false);

  async function createCycle(event: FormEvent) {
    event.preventDefault(); if (!tenantId) return;
    setBusy(true); setFeedback("");
    try {
      const response = await api.post(`/api/tenants/${tenantId}/academic/exams/cycles`, {
        institutionId, academicSessionId, semesterId, name: cycleName, cycleType, startDate, endDate,
      });
      setCycleId(response.data.exam_cycle_id); setFeedback("Exam cycle created.");
    } catch (error) { setFeedback(error instanceof Error ? error.message : "Unable to create exam cycle."); }
    finally { setBusy(false); }
  }

  async function addSchedule(event: FormEvent) {
    event.preventDefault(); if (!tenantId) return;
    setBusy(true); setFeedback("");
    try {
      const response = await api.post(`/api/tenants/${tenantId}/academic/exams/schedules`, {
        examCycleId: cycleId, courseOfferingId, examDate, startTime, endTime,
        maxMarks: Number(maxMarks), passMarks: Number(passMarks), roomId: roomId || null,
      });
      setScheduleId(response.data.exam_schedule_id);
      setFeedback("Exam scheduled."); await loadSchedules();
    } catch (error) { setFeedback(error instanceof Error ? error.message : "Unable to schedule exam."); }
    finally { setBusy(false); }
  }

  async function loadSchedules() {
    if (!tenantId || !cycleId) return;
    const response = await api.get(`/api/tenants/${tenantId}/academic/exams/cycles/${cycleId}/schedules`);
    setSchedules(response.data);
  }

  async function assignInvigilator() {
    if (!tenantId || !scheduleId || !staffUserId) return;
    try {
      await api.post(`/api/tenants/${tenantId}/academic/exams/schedules/${scheduleId}/invigilators/${staffUserId}`);
      setFeedback("Invigilator assigned.");
    } catch (error) { setFeedback(error instanceof Error ? error.message : "Unable to assign invigilator."); }
  }

  return <main className="page-container">
    <header><h1>Examination scheduling</h1><p>Configure Internal, Mid-term and Final Semester examination cycles.</p></header>
    <form onSubmit={createCycle} className="form-grid">
      <h2>Exam cycle</h2>
      <label>Institution ID<input required value={institutionId} onChange={(e) => setInstitutionId(e.target.value)} /></label>
      <label>Academic session ID<input required value={academicSessionId} onChange={(e) => setAcademicSessionId(e.target.value)} /></label>
      <label>Semester ID<input required value={semesterId} onChange={(e) => setSemesterId(e.target.value)} /></label>
      <label>Cycle name<input required value={cycleName} onChange={(e) => setCycleName(e.target.value)} /></label>
      <label>Cycle type<select value={cycleType} onChange={(e) => setCycleType(e.target.value)}><option value="INTERNAL">Internal</option><option value="MID_TERM">Mid-term</option><option value="FINAL_SEMESTER">Final Semester</option></select></label>
      <label>Start date<input required type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} /></label>
      <label>End date<input required type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} /></label>
      <button disabled={busy}>Create cycle</button>
    </form>
    {cycleId && <form onSubmit={addSchedule} className="form-grid">
      <h2>Schedule a paper</h2><p>Cycle: {cycleId}</p>
      <label>Course offering ID<input required value={courseOfferingId} onChange={(e) => setCourseOfferingId(e.target.value)} /></label>
      <label>Exam date<input required type="date" value={examDate} onChange={(e) => setExamDate(e.target.value)} /></label>
      <label>Start time<input required type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} /></label>
      <label>End time<input required type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} /></label>
      <label>Maximum marks<input type="number" min="0.01" step="0.01" value={maxMarks} onChange={(e) => setMaxMarks(e.target.value)} /></label>
      <label>Pass marks<input type="number" min="0" step="0.01" value={passMarks} onChange={(e) => setPassMarks(e.target.value)} /></label>
      <label>Room ID (optional)<input value={roomId} onChange={(e) => setRoomId(e.target.value)} /></label>
      <button disabled={busy}>Add schedule</button><button type="button" onClick={loadSchedules}>Refresh timetable</button>
    </form>}
    {scheduleId && <section><h2>Invigilator assignment</h2><label>Staff user ID<input value={staffUserId} onChange={(e) => setStaffUserId(e.target.value)} /></label><button type="button" onClick={assignInvigilator}>Assign invigilator</button></section>}
    {feedback && <p role="status">{feedback}</p>}
    <h2>Timetable</h2><table><thead><tr><th>Date</th><th>Time</th><th>Course</th><th>Room</th></tr></thead><tbody>
      {schedules.map((item) => <tr key={String(item.exam_schedule_id)}><td>{String(item.exam_date)}</td><td>{String(item.start_time)}–{String(item.end_time)}</td><td>{String(item.course_offering_id)}</td><td>{String(item.room_id ?? "Unassigned")}</td></tr>)}
    </tbody></table>
  </main>;
}
