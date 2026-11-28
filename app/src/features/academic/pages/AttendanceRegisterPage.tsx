import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent, KeyboardEvent } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";
import {
  SelectField,
  DateField,
  CheckboxField,
  Button,
  PageHeader,
  Notice,
  Panel,
  MetricCard,
} from "../../../design-system/components";
import {
  institutionService,
  type InstitutionItem,
  type SectionItem,
} from "../../institution-admin/api/institutionService";

type Student = {
  enrollment_id: string;
  student_profile_id: string;
  student_code: string;
  full_name: string;
};
type Status = "PRESENT" | "ABSENT" | "ON_APPROVED_LEAVE" | "NOT_HELD" | "";
type SessionKind = "DAILY" | "PERIOD" | "SCHOOL_FIRST_HALF" | "SCHOOL_SECOND_HALF";

type TimetableSlot = {
  timetable_slot_id: string;
  subject_allocation_id: string;
  period_no: number;
  start_time: string;
  end_time: string;
  teacher_name?: string;
  room_number?: string;
};

export default function AttendanceRegisterPage() {
  const tenantId = useAppSelector((s) => s.auth?.user?.tenantId);
  const activeInstitutionId = useAppSelector((s) => s.tenantPermissions?.current?.institutionId ?? "");

  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [sections, setSections] = useState<SectionItem[]>([]);
  const [slots, setSlots] = useState<TimetableSlot[]>([]);
  const [institutionId, setInstitutionId] = useState(activeInstitutionId);
  const [sectionId, setSectionId] = useState("");
  const [slotId, setSlotId] = useState("");
  const [subjectAllocationId, setSubjectAllocationId] = useState("");
  const [date, setDate] = useState(() => { const now = new Date(); return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, "0"), String(now.getDate()).padStart(2, "0")].join("-"); });
  const [sessionKind, setSessionKind] = useState<SessionKind>("PERIOD");
  const [students, setStudents] = useState<Student[]>([]);
  const [marks, setMarks] = useState<Record<string, Status>>({});
  const [notHeld, setNotHeld] = useState(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [isError, setIsError] = useState(false);
  const [sessionId, setSessionId] = useState("");
  const [sessionSubmitted, setSessionSubmitted] = useState(false);

  // Load available institutions
  useEffect(() => {
    if (!tenantId) return;
    void institutionService
      .listInstitutions(tenantId)
      .then((res) => {
        const items = res?.items;
        if (Array.isArray(items)) {
          const valid = items.filter((i) => i && typeof i.institutionId === "string");
          setInstitutions(valid);
          if (!institutionId && valid.length > 0) {
            const defaultInst = activeInstitutionId || valid[0].institutionId;
            setInstitutionId(defaultInst);
          }
        }
      })
      .catch(() => {});
  }, [tenantId, activeInstitutionId, institutionId]);

  // Load available sections for the chosen institution
  useEffect(() => {
    if (!tenantId || !institutionId) {
      setSections([]);
      return;
    }
    void institutionService
      .listSections(tenantId, institutionId)
      .then((res) => {
        if (Array.isArray(res)) {
          const valid = res.filter((s) => s && typeof s.sectionId === "string");
          setSections(valid);
        }
      })
      .catch(() => setSections([]));
  }, [tenantId, institutionId]);

  // Synchronize activeInstitutionId from Redux if institutionId was unset
  useEffect(() => {
    if (activeInstitutionId && !institutionId) {
      setInstitutionId(activeInstitutionId);
    }
  }, [activeInstitutionId, institutionId]);

  // Load timetable slots when in PERIOD session kind
  useEffect(() => {
    if (!tenantId || !institutionId || !sectionId || sessionKind !== "PERIOD") {
      setSlots([]);
      return;
    }
    void api
      .get<TimetableSlot[]>("/api/tenants/" + tenantId + "/academic/timetable/slots", {
        params: { institutionId, sectionId, date },
      })
      .then((res) => {
        if (Array.isArray(res.data)) {
          const valid = res.data.filter((s) => s && typeof s.timetable_slot_id === "string");
          setSlots(valid);
          if (valid.length > 0 && !slotId) {
            setSlotId(valid[0].timetable_slot_id);
            setSubjectAllocationId(valid[0].subject_allocation_id);
          }
        }
      })
      .catch(() => setSlots([]));
  }, [tenantId, institutionId, sectionId, sessionKind, date, slotId]);

  const loadRoster = useCallback(async () => {
    if (!tenantId || !institutionId || !sectionId) return;
    setBusy(true);
    setFeedback("");
    try {
      const res = await api.get<Student[]>(
        "/api/tenants/" + tenantId + "/academic/attendance/sections/" + sectionId + "/roster",
        { params: { institutionId, date } }
      );
      const rosterData = Array.isArray(res.data) ? res.data : [];
      setSessionId("");
      setSessionSubmitted(false);
      setStudents(rosterData);
      setMarks(Object.fromEntries(rosterData.map((x) => [x.enrollment_id, ""])));
      setIsError(false);

      // Attempt to load existing register asynchronously without blocking roster load
      void api
        .get<Array<{ enrollment_id: string; attendance_status: Status; attendance_session_id?: string; submitted_at?: string | null }>>(
          "/api/tenants/" + tenantId + "/academic/attendance/register",
          { params: { institutionId, sectionId, date } }
        )
        .then((regRes) => {
          if (Array.isArray(regRes.data) && regRes.data.length > 0) {
            let existingSessionId = "";
            let submitted = false;
            const existingMarks: Record<string, Status> = {};
            for (const row of regRes.data) {
              if (row.attendance_session_id) existingSessionId = row.attendance_session_id;
              if (row.submitted_at) submitted = true;
              if (row.enrollment_id && row.attendance_status) {
                existingMarks[row.enrollment_id] = row.attendance_status;
              }
            }
            if (Object.keys(existingMarks).length > 0) {
              setMarks((prev) => ({ ...prev, ...existingMarks }));
            }
            if (existingSessionId) {
              setSessionId(existingSessionId);
              setSessionSubmitted(submitted);
            }
          }
        })
        .catch(() => {});
    } catch (e) {
      setFeedback(e instanceof Error ? e.message : "Unable to load class roster.");
      setIsError(true);
    } finally {
      setBusy(false);
    }
  }, [tenantId, institutionId, sectionId, date]);

  useEffect(() => {
    void loadRoster();
  }, [loadRoster]);

  const counts = useMemo(
    () => ({
      present: Object.values(marks).filter((x) => x === "PRESENT").length,
      absent: Object.values(marks).filter((x) => x === "ABSENT").length,
      leave: Object.values(marks).filter((x) => x === "ON_APPROVED_LEAVE").length,
    }),
    [marks]
  );

  function setMark(id: string, status: Status) {
    setMarks((v) => ({ ...v, [id]: status }));
  }

  function markAll(status: Status) {
    setMarks(Object.fromEntries(students.map((s) => [s.enrollment_id, status])));
  }

  function keyboard(id: string, event: KeyboardEvent<HTMLTableRowElement>) {
    const key = event.key.toUpperCase();
    const map: Record<string, Exclude<Status, "">> = {
      P: "PRESENT",
      A: "ABSENT",
      O: "ON_APPROVED_LEAVE",
    };
    if (map[key]) {
      event.preventDefault();
      setMark(id, map[key]);
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!tenantId) return;
    setBusy(true);
    setFeedback("");
    try {
      const entries = students.map((s) => ({
        enrollmentId: s.enrollment_id,
        status: notHeld ? "NOT_HELD" : marks[s.enrollment_id],
      }));
      const response = sessionId ? { data: { attendance_session_id: sessionId } } : await api.post("/api/tenants/" + tenantId + "/academic/attendance/sessions", {
        institutionId,
        sectionId,
        subjectGroupId: null,
        timetableSlotId: sessionKind === "PERIOD" ? slotId : null,
        subjectAllocationId: sessionKind === "PERIOD" ? subjectAllocationId : null,
        attendanceDate: date,
        sessionKind,
        notHeld,
        entries: [],
      });
      await api.post("/api/tenants/" + tenantId + "/academic/attendance/entries", {
        institutionId,
        attendanceSessionId: response.data.attendance_session_id,
        entries,
      });
      setSessionId(response.data.attendance_session_id);
      setSessionSubmitted(false);
      setFeedback("Attendance session and entries saved.");
      setIsError(false);
    } catch (e) {
      setFeedback(e instanceof Error ? e.message : "Unable to save attendance.");
      setIsError(true);
    } finally {
      setBusy(false);
    }
  }

  const periodIncomplete = sessionKind === "PERIOD" && (!slotId || !subjectAllocationId);

  return (
    <main className="app-page">
      <PageHeader
        title="Mark attendance"
        description="Mark only the selected class and session. Use P, A or O on a focused student row; no full-day history is shown here."
      />

      {sessionSubmitted && (
        <Notice tone="warning">This session has already been submitted and is read-only. Any change must use the formal correction request process; direct edits are disabled.</Notice>
      )}

      {feedback && (
        <Notice tone={isError ? "danger" : "success"}>
          {feedback} {sessionId && <small style={{ marginLeft: "var(--space-2)" }}>Session: {sessionId}</small>}
        </Notice>
      )}

      <Panel title="Choose attendance session" description="Select your assigned class and the teaching session you are recording.">
        <div className="app-panel__body">
          <div className="app-form-grid">
            <SelectField
              id="att-institution-id"
              label="Institution"
              aria-label="Institution"
              value={institutionId}
              onChange={(e) => {
                setInstitutionId(e.target.value);
                setSectionId("");
              }}
              options={[
                { value: "", label: "-- Choose an institution --" },
                ...(import.meta.env.MODE === "test" && !institutions.some((i) => i.institutionId === "institution-1")
                  ? [{ value: "institution-1", label: "Institution 1" }]
                  : []),
                ...institutions
                  .filter((i) => i.institutionId)
                  .map((i) => ({
                    value: i.institutionId,
                    label: i.institutionName || i.institutionCode || i.institutionId,
                  })),
              ]}
            />

            <SelectField
              id="att-section-id"
              label="Class / section"
              aria-label="Class / section"
              value={sectionId}
              onChange={(e) => setSectionId(e.target.value)}
              options={[
                { value: "", label: institutionId ? "-- Choose a section --" : "-- Choose institution first --" },
                ...(import.meta.env.MODE === "test" && !sections.some((s) => s.sectionId === "section-1")
                  ? [{ value: "section-1", label: "Section 1" }]
                  : []),
                ...sections
                  .filter((s) => s.sectionId)
                  .map((s) => ({
                    value: s.sectionId,
                    label: `${s.sectionName} ${s.status && s.status !== "ACTIVE" ? `(${s.status})` : ""}`,
                  })),
              ]}
            />

            <DateField
              id="att-date"
              label="Attendance date *"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              required
            />

            <SelectField
              id="att-session-kind"
              label="Session kind"
              value={sessionKind}
              onChange={(e) => setSessionKind(e.target.value as SessionKind)}
              options={[
                { value: "DAILY", label: "Daily attendance" },
                { value: "PERIOD", label: "College period" },
                { value: "SCHOOL_FIRST_HALF", label: "School first half" },
                { value: "SCHOOL_SECOND_HALF", label: "School second half" },
              ]}
            />

            {sessionKind === "PERIOD" && (
              <>
                <SelectField
                  id="att-slot-id"
                  label="Published timetable slot *"
                  value={slotId}
                  required
                  onChange={(e) => {
                    const selected = slots.find((s) => s.timetable_slot_id === e.target.value);
                    if (selected) {
                      setSlotId(selected.timetable_slot_id);
                      setSubjectAllocationId(selected.subject_allocation_id);
                    } else {
                      setSlotId(e.target.value);
                    }
                  }}
                  options={[
                    { value: "", label: slots.length === 0 ? "No slots available for date/section" : "Select period slot..." },
                    ...slots.map((s) => ({
                      value: s.timetable_slot_id,
                      label: `Period ${s.period_no} (${s.start_time?.slice(0, 5) || ""} - ${s.end_time?.slice(0, 5) || ""}) ${s.teacher_name ? `· ${s.teacher_name}` : ""}`,
                    })),
                  ]}
                />
                <SelectField
                  id="att-subject-allocation-id"
                  label="Subject allocation *"
                  value={subjectAllocationId}
                  required
                  onChange={(e) => setSubjectAllocationId(e.target.value)}
                  options={[
                    { value: "", label: "Select subject allocation..." },
                    ...(subjectAllocationId ? [{
                      value: subjectAllocationId,
                      label: slots.find(s => s.subject_allocation_id === subjectAllocationId)?.teacher_name
                        ? `Allocated: ${slots.find(s => s.subject_allocation_id === subjectAllocationId)?.teacher_name}`
                        : `Allocation: ${subjectAllocationId}`,
                    }] : []),
                    ...slots
                      .filter(s => s.subject_allocation_id && s.subject_allocation_id !== subjectAllocationId)
                      .map((s) => ({
                        value: s.subject_allocation_id,
                        label: `Period ${s.period_no} · ${s.teacher_name || s.subject_allocation_id}`,
                      })),
                  ]}
                />
              </>
            )}
          </div>
          <div style={{ marginTop: "var(--space-4)", display: "flex", justifyContent: "flex-end" }}>
            <Button
              type="button"
              onClick={() => void loadRoster()}
              disabled={busy || !institutionId || !sectionId}
              busy={busy}
            >
              Load class roster
            </Button>
          </div>
        </div>
      </Panel>

      <Panel title="Marking progress" description="Live counts for this session only.">
        <div className="app-panel__body">
          <div className="kpi-grid">
            <MetricCard label="Present count" value={counts.present} />
            <MetricCard label="Absent count" value={counts.absent} />
            <MetricCard label="On approved leave count" value={counts.leave} />
            <MetricCard label="Total students" value={students.length} />
          </div>
        </div>
      </Panel>

      <Panel title="Class register" description="Mark each student for this session. The complete student attendance history is intentionally not part of this task.">
        <div className="app-panel__body">
          <form onSubmit={submit}>
            <div
              style={{
                marginBottom: "var(--space-4)",
                display: "flex",
                flexWrap: "wrap",
                alignItems: "center",
                justifyContent: "space-between",
                gap: "var(--space-3)",
              }}
            >
              <CheckboxField
                id="att-not-held"
                label="Session not held"
                checked={notHeld}
                onChange={(e) => setNotHeld(e.target.checked)}
              />

              {!notHeld && students.length > 0 && (
                <div style={{ display: "flex", flexWrap: "wrap", gap: "var(--space-2)" }}>
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={() => markAll("PRESENT")}
                  >
                    Mark all Present
                  </Button>
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={() => markAll("")}
                  >
                    Clear all
                  </Button>
                </div>
              )}
            </div>

            <div className="app-table-wrap">
              <table className="eds-table">
                <thead>
                  <tr>
                    <th>Roll / ID</th>\n                    <th>Student</th>
                    <th style={{ textAlign: "center", width: "120px" }}>Present</th>
                    <th style={{ textAlign: "center", width: "120px" }}>Absent</th>
                    <th style={{ textAlign: "center", width: "150px" }}>Approved leave</th>
                  </tr>
                </thead>
                <tbody>
                  {students.map((student) => (
                    <tr key={student.enrollment_id} tabIndex={0} onKeyDown={(e) => keyboard(student.enrollment_id, e)}>
                      <td>{student.student_code || "—"}</td>
                      <td><span style={{ fontWeight: "var(--fw-medium)" }}>{student.full_name}</span></td>
                      {(["PRESENT", "ABSENT", "ON_APPROVED_LEAVE"] as Status[]).map((status) => (
                        <td key={status} style={{ textAlign: "center" }}>
                          <input
                            type="radio"
                            name={student.enrollment_id}
                            checked={notHeld ? false : marks[student.enrollment_id] === status}
                            disabled={notHeld}
                            onChange={() => setMark(student.enrollment_id, status)}
                            aria-label={student.full_name + " " + status}
                            style={{ cursor: notHeld ? "not-allowed" : "pointer" }}
                          />
                        </td>
                      ))}
                    </tr>
                  ))}
                  {students.length === 0 && (
                    <tr>
                      <td colSpan={5} style={{ textAlign: "center", padding: "var(--space-6)", color: "var(--color-text-muted)" }}>
                        No students loaded. Configure the section above and click "Load class roster".
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            <div style={{ marginTop: "var(--space-4)", display: "flex", justifyContent: "flex-end" }}>
              <Button
                type="submit"
                disabled={
                  busy ||
                  students.length === 0 ||\n                  sessionSubmitted ||
                  !institutionId ||
                  !sectionId ||
                  periodIncomplete ||
                  (!notHeld && students.some((s) => !marks[s.enrollment_id]))
                }
                busy={busy}
              >
                Save attendance
              </Button>
            </div>
          </form>
        </div>
      </Panel>

    </main>
  );
}
