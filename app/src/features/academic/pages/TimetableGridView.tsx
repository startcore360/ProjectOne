import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent, DragEvent } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";
import ClashWarningToast from "../components/ClashWarningToast";

type Slot = {
  timetable_slot_id: string; section_id: string; course_offering_id: string; teacher_id: string;
  room_id: string | null; room_number?: string; day_of_week: number; start_time: string; end_time: string;
  online_meeting_url: string | null; status: string;
};
const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const TIMES = Array.from({ length: 20 }, (_, i) => String(8 + Math.floor(i / 2)).padStart(2, "0") + (i % 2 ? ":30" : ":00"));

export default function TimetableGridView() {
  const tenantId = useAppSelector((s) => s.auth.user?.tenantId);
  const [institutionId, setInstitutionId] = useState("");
  const [sessionId, setSessionId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [slots, setSlots] = useState<Slot[]>([]);
  const [courseOfferingId, setCourseOfferingId] = useState("");
  const [teacherId, setTeacherId] = useState("");
  const [roomId, setRoomId] = useState("");
  const [day, setDay] = useState("1");
  const [start, setStart] = useState("09:00");
  const [end, setEnd] = useState("10:00");
  const [roomNumber, setRoomNumber] = useState("");
  const [roomCapacity, setRoomCapacity] = useState("40");
  const [roomType, setRoomType] = useState("LECTURE_HALL");
  const [meetingUrl, setMeetingUrl] = useState("");
  const [meetingProvider, setMeetingProvider] = useState("ZOOM");
  const [tokenExpiry, setTokenExpiry] = useState(() => new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 16));
  const [dragged, setDragged] = useState<Slot | null>(null);
  const [toast, setToast] = useState("");
  const [feedback, setFeedback] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!tenantId || !institutionId) return;
    try {
      const res = await api.get<Slot[]>("/api/tenants/" + tenantId + "/timetable", {
        params: { institutionId, sectionId: sectionId || undefined, academicSessionId: sessionId || undefined },
      });
      setSlots(res.data);
    } catch (e) { setFeedback(e instanceof Error ? e.message : "Unable to load timetable."); }
  }, [tenantId, institutionId, sectionId, sessionId]);
  useEffect(() => { void load(); }, [load]);

  async function createSlot(event: FormEvent) {
    event.preventDefault(); if (!tenantId) return;
    setBusy(true); setToast(""); setFeedback("");
    try {
      const slotResponse = await api.post("/api/tenants/" + tenantId + "/timetable/slots", {
        institutionId, academicSessionId: sessionId, sectionId, courseOfferingId, teacherId,
        roomId: roomId || null, dayOfWeek: Number(day), startTime: start + ":00", endTime: end + ":00",
        onlineMeetingUrl: meetingUrl || null,
      });
      if (meetingUrl) {
        const online = await api.post("/api/tenants/" + tenantId + "/timetable/slots/" + slotResponse.data.timetable_slot_id + "/online-session", {
          institutionId, provider: meetingProvider, meetingUrl, tokenExpiresAt: new Date(tokenExpiry).toISOString(),
        });
        setFeedback("Timetable slot created. One-time student access token: " + online.data.accessToken);
      } else setFeedback("Timetable slot created.");
      await load();
    } catch (e) {
      const failure = e as { response?: { data?: { code?: string; message?: string } } };
      const code = failure.response?.data?.code ?? (e instanceof Error ? e.message : "");
      const message = failure.response?.data?.message ?? (e instanceof Error ? e.message : "Unable to create timetable slot.");
      setToast(code.includes("TEACHER_DOUBLE_BOOKED") ? "Teacher already has a slot at this time."
        : code.includes("ROOM_OCCUPIED") ? "Room is already occupied at this time." : message);
    } finally { setBusy(false); }
  }

  async function createRoom(event: FormEvent) {
    event.preventDefault(); if (!tenantId) return;
    setBusy(true); setFeedback("");
    try {
      const res = await api.post("/api/tenants/" + tenantId + "/timetable/rooms", {
        institutionId, roomNumber, capacity: Number(roomCapacity), roomType,
      });
      setRoomId(res.data.room_id ?? res.data.roomId); setFeedback("Room created.");
    } catch (e) { setFeedback(e instanceof Error ? e.message : "Unable to create room."); }
    finally { setBusy(false); }
  }

  function dropOn(dayOfWeek: number, time: string, event: DragEvent<HTMLElement>) {
    event.preventDefault();
    if (!dragged || !tenantId) return;
    const startAt = time + ":00";
    const oldStart = dragged.start_time.slice(0, 5).split(":").map(Number);
    const duration = (Number(dragged.end_time.slice(0, 2)) * 60 + Number(dragged.end_time.slice(3, 5))) - (oldStart[0] * 60 + oldStart[1]);
    const endMinutes = Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5)) + duration;
    const newEnd = String(Math.floor(endMinutes / 60)).padStart(2, "0") + ":" + String(endMinutes % 60).padStart(2, "0") + ":00";
    const clashes = slots.some((slot) => slot.timetable_slot_id !== dragged.timetable_slot_id && slot.day_of_week === dayOfWeek
      && (slot.teacher_id === dragged.teacher_id || (dragged.room_id && slot.room_id === dragged.room_id))
      && startAt < slot.end_time && newEnd > slot.start_time);
    if (clashes) { setToast("Move blocked: the teacher or room is already booked in that time range."); setDragged(null); return; }
    void api.put("/api/tenants/" + tenantId + "/timetable/slots/" + dragged.timetable_slot_id, {
      institutionId, dayOfWeek, startTime: startAt, endTime: newEnd, roomId: dragged.room_id,
    }).then(() => { setToast(""); setFeedback("Timetable slot moved."); void load(); })
      .catch((e: unknown) => setToast(e instanceof Error ? e.message : "The timetable engine rejected this move."));
    setDragged(null);
  }

  const byCell = useMemo(() => {
    const map = new Map<string, Slot[]>();
    slots.forEach((slot) => {
      const key = slot.day_of_week + "-" + slot.start_time.slice(0, 2) + ":00";
      map.set(key, [...(map.get(key) ?? []), slot]);
    });
    return map;
  }, [slots]);

  return <main className="page-container">
    <header><h1>Weekly timetable</h1><p>Allocate sections, courses, teachers and rooms. PostgreSQL GiST constraints provide the final concurrency-safe clash guard.</p></header>
    <div className="form-grid">
      <label>Institution ID<input value={institutionId} onChange={(e) => setInstitutionId(e.target.value)} /></label>
      <label>Academic session ID<input value={sessionId} onChange={(e) => setSessionId(e.target.value)} /></label>
      <label>Section ID<input value={sectionId} onChange={(e) => setSectionId(e.target.value)} /></label>
      <button type="button" onClick={() => void load()}>Refresh timetable</button>
    </div>
    <ClashWarningToast message={toast} onDismiss={() => setToast("")} />
    {feedback && <p role="status">{feedback}</p>}
    <section><h2>Create timetable slot</h2><form onSubmit={createSlot} className="form-grid">
      <label>Course offering ID<input required value={courseOfferingId} onChange={(e) => setCourseOfferingId(e.target.value)} /></label>
      <label>Teacher profile ID<input required value={teacherId} onChange={(e) => setTeacherId(e.target.value)} /></label>
      <label>Room ID<input value={roomId} onChange={(e) => setRoomId(e.target.value)} /></label>
      <label>Day<select value={day} onChange={(e) => setDay(e.target.value)}>{DAYS.map((name, i) => <option key={name} value={i + 1}>{name}</option>)}</select></label>
      <label>Start<input type="time" value={start} onChange={(e) => setStart(e.target.value)} /></label>
      <label>End<input type="time" value={end} onChange={(e) => setEnd(e.target.value)} /></label>
      <label>Online meeting URL (optional)<input type="url" placeholder="https://" value={meetingUrl} onChange={(e) => setMeetingUrl(e.target.value)} /></label>
      {meetingUrl && <>
        <label>Provider<select value={meetingProvider} onChange={(e) => setMeetingProvider(e.target.value)}><option value="ZOOM">Zoom</option><option value="GOOGLE_MEET">Google Meet</option><option value="TEAMS">Microsoft Teams</option><option value="CUSTOM">Other</option></select></label>
        <label>Access token expiry<input type="datetime-local" value={tokenExpiry} onChange={(e) => setTokenExpiry(e.target.value)} /></label>
      </>}
      <button disabled={busy || !institutionId || !sessionId || !sectionId}>Add slot</button>
    </form></section>
    <section><h2>Register room</h2><form onSubmit={createRoom} className="form-grid">
      <label>Room number<input required value={roomNumber} onChange={(e) => setRoomNumber(e.target.value)} /></label>
      <label>Capacity<input type="number" min="1" max="10000" value={roomCapacity} onChange={(e) => setRoomCapacity(e.target.value)} /></label>
      <label>Room type<select value={roomType} onChange={(e) => setRoomType(e.target.value)}><option value="LECTURE_HALL">Lecture hall</option><option value="LAB">Lab</option><option value="AUDITORIUM">Auditorium</option></select></label>
      <button disabled={busy || !institutionId}>Create room</button>
    </form></section>
    <h2>Weekly grid</h2>
    <div style={{ overflowX: "auto" }}><table><thead><tr><th>Time</th>{DAYS.map((d) => <th key={d}>{d}</th>)}</tr></thead><tbody>
      {TIMES.map((time) => <tr key={time}><th>{time}</th>{DAYS.map((d, index) => {
        const cell = byCell.get((index + 1) + "-" + time) ?? [];
        return <td key={d} onDragOver={(e) => e.preventDefault()} onDrop={(e) => dropOn(index + 1, time, e)} style={{ minWidth: 130, verticalAlign: "top" }}>
          {cell.map((slot) => <div key={slot.timetable_slot_id} draggable onDragStart={() => setDragged(slot)}
            style={{ border: "1px solid var(--border, #ccc)", borderRadius: 6, padding: 6, marginBottom: 4, cursor: "grab" }}>
            <strong>{slot.start_time.slice(0, 5)}–{slot.end_time.slice(0, 5)}</strong><br />
            <small>{slot.course_offering_id.slice(0, 8)} · {slot.room_number ?? slot.room_id?.slice(0, 8) ?? "Online"}</small>
          </div>)}
        </td>;
      })}</tr>)}
    </tbody></table></div>
  </main>;
}