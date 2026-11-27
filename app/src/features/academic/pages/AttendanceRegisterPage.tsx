import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent, KeyboardEvent } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";
import {
  TextField,
  SelectField,
  DateField,
  CheckboxField,
  Button,
  PageHeader,
  Notice,
  Panel,
  MetricCard,
  Badge,
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
  has_medical_exemption?: boolean;
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
  const [departmentId, setDepartmentId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [slotId, setSlotId] = useState("");
  const [subjectAllocationId, setSubjectAllocationId] = useState("");
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [sessionKind, setSessionKind] = useState<SessionKind>("PERIOD");
  const [students, setStudents] = useState<Student[]>([]);
  const [marks, setMarks] = useState<Record<string, Status>>({});
  const [notHeld, setNotHeld] = useState(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [isError, setIsError] = useState(false);
  const [sessionId, setSessionId] = useState("");
  const [threshold, setThreshold] = useState("");

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
      setStudents(rosterData);
      setMarks(Object.fromEntries(rosterData.map((x) => [x.enrollment_id, ""])));
      setIsError(false);

      // Attempt to load existing register asynchronously without blocking roster load
      void api
        .get<Array<{ enrollment_id: string; attendance_status: Status; attendance_session_id?: string }>>(
          "/api/tenants/" + tenantId + "/academic/attendance/register",
          { params: { institutionId, sectionId, date } }
        )
        .then((regRes) => {
          if (Array.isArray(regRes.data) && regRes.data.length > 0) {
            let existingSessionId = "";
            const existingMarks: Record<string, Status> = {};
            for (const row of regRes.data) {
              if (row.attendance_session_id) existingSessionId = row.attendance_session_id;
              if (row.enrollment_id && row.attendance_status) {
                existingMarks[row.enrollment_id] = row.attendance_status;
              }
            }
            if (Object.keys(existingMarks).length > 0) {
              setMarks((prev) => ({ ...prev, ...existingMarks }));
            }
            if (existingSessionId) {
              setSessionId(existingSessionId);
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

  useEffect(() => {
    if (!tenantId || !institutionId) return;
    void api
      .get("/api/tenants/" + tenantId + "/academic/attendance/thresholds", {
        params: { institutionId, departmentId: departmentId || undefined },
      })
      .then((r) => setThreshold(String(r.data.minimum_attendance_percentage)))
      .catch(() => setThreshold(""));
  }, [tenantId, institutionId, departmentId]);

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
      const response = await api.post("/api/tenants/" + tenantId + "/academic/attendance/sessions", {
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
      setFeedback("Attendance session and entries saved.");
      setIsError(false);
    } catch (e) {
      setFeedback(e instanceof Error ? e.message : "Unable to save attendance.");
      setIsError(true);
    } finally {
      setBusy(false);
    }
  }

  async function saveThreshold(event: FormEvent) {
    event.preventDefault();
    if (!tenantId) return;
    setBusy(true);
    setFeedback("");
    try {
      await api.put("/api/tenants/" + tenantId + "/academic/attendance/thresholds", {
        institutionId,
        departmentId: departmentId || null,
        minimumAttendancePercentage: Number(threshold),
      });
      setFeedback("Attendance threshold settings saved.");
      setIsError(false);
    } catch (e) {
      setFeedback(e instanceof Error ? e.message : "Unable to save attendance threshold.");
      setIsError(true);
    } finally {
      setBusy(false);
    }
  }

  const periodIncomplete = sessionKind === "PERIOD" && (!slotId || !subjectAllocationId);

  return (
    <main className="app-page">
      <PageHeader
        title="Attendance register"
        description="Capture school half-day or college period attendance. Keyboard shortcuts: P = Present, A = Absent, O = On approved leave. Only canonical statuses are available."
      />

      {feedback && (
        <Notice tone={isError ? "danger" : "success"}>
          {feedback} {sessionId && <small style={{ marginLeft: "var(--space-2)" }}>Session: {sessionId}</small>}
        </Notice>
      )}

      <Panel title="Session configuration" description="Select the institution, section, and date to record attendance.">
        <div className="app-panel__body">
          <div className="app-form-grid">
            {institutions.some((i) => i.institutionName) && (
              <SelectField
                id="att-institution-quick-select"
                label="Quick select institution"
                value={institutionId}
                onChange={(e) => {
                  setInstitutionId(e.target.value);
                  setSectionId("");
                }}
                options={[
                  { value: "", label: "-- Choose an institution --" },
                  ...institutions
                    .filter((i) => i.institutionId && i.institutionName)
                    .map((i) => ({
                      value: i.institutionId,
                      label: i.institutionName,
                    })),
                ]}
              />
            )}

            <TextField
              id="att-institution-id"
              label="Institution ID"
              value={institutionId}
              onChange={(e) => {
                setInstitutionId(e.target.value);
                setSectionId("");
              }}
              placeholder="e.g. institution UUID"
              list="institution-options"
            />
            <datalist id="institution-options">
              {institutions.filter((i) => i.institutionId).map((i) => (
                <option key={i.institutionId} value={i.institutionId}>
                  {i.institutionName || i.institutionId}
                </option>
              ))}
            </datalist>

            {sections.some((s) => s.sectionName) && (
              <SelectField
                id="att-section-quick-select"
                label="Quick select class section"
                value={sectionId}
                onChange={(e) => setSectionId(e.target.value)}
                options={[
                  { value: "", label: "-- Choose a section --" },
                  ...sections
                    .filter((s) => s.sectionId && s.sectionName)
                    .map((s) => ({
                      value: s.sectionId,
                      label: `${s.sectionName} ${s.status && s.status !== "ACTIVE" ? `(${s.status})` : ""}`,
                    })),
                ]}
              />
            )}

            <TextField
              id="att-section-id"
              label="Section ID"
              value={sectionId}
              onChange={(e) => setSectionId(e.target.value)}
              placeholder="e.g. section UUID"
              list="section-options"
            />
            <datalist id="section-options">
              {sections.filter((s) => s.sectionId && s.sectionName).map((s) => (
                <option key={s.sectionId} value={s.sectionId}>
                  {s.sectionName}
                </option>
              ))}
            </datalist>

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
                {slots.length > 0 && (
                  <SelectField
                    id="att-slot-select"
                    label="Choose scheduled slot"
                    value={slotId}
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
                      { value: "", label: "Select period slot..." },
                      ...slots.map((s) => ({
                        value: s.timetable_slot_id,
                        label: `Period ${s.period_no} (${s.start_time?.slice(0, 5) || ""} - ${s.end_time?.slice(0, 5) || ""}) ${s.teacher_name ? `· ${s.teacher_name}` : ""}`,
                      })),
                    ]}
                  />
                )}
                <TextField
                  id="att-slot-id"
                  label="Published timetable slot ID *"
                  value={slotId}
                  onChange={(e) => setSlotId(e.target.value)}
                  placeholder="e.g. timetable slot UUID"
                  required
                />
                <TextField
                  id="att-subject-allocation-id"
                  label="Subject allocation ID *"
                  value={subjectAllocationId}
                  onChange={(e) => setSubjectAllocationId(e.target.value)}
                  placeholder="e.g. subject allocation UUID"
                  required
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

      <Panel title="Register summary" description="Summary count of marked attendance.">
        <div className="app-panel__body">
          <div className="kpi-grid">
            <MetricCard label="Present count" value={counts.present} />
            <MetricCard label="Absent count" value={counts.absent} />
            <MetricCard label="On approved leave count" value={counts.leave} />
            <MetricCard label="Total students" value={students.length} />
          </div>
        </div>
      </Panel>

      <Panel title="Roster & attendance sheet" description="Mark attendance per student or mark the entire session not held.">
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
                    onClick={() => markAll("ABSENT")}
                  >
                    Mark all Absent
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
                    <th>Student</th>
                    <th style={{ textAlign: "center", width: "120px" }}>Present</th>
                    <th style={{ textAlign: "center", width: "120px" }}>Absent</th>
                    <th style={{ textAlign: "center", width: "150px" }}>Approved leave</th>
                  </tr>
                </thead>
                <tbody>
                  {students.map((student) => (
                    <tr key={student.enrollment_id} tabIndex={0} onKeyDown={(e) => keyboard(student.enrollment_id, e)}>
                      <td>
                        <span style={{ fontWeight: "var(--fw-medium)" }}>{student.full_name}</span>{" "}
                        <small style={{ color: "var(--color-text-muted)" }}>({student.student_code})</small>
                        {student.has_medical_exemption && (
                          <Badge tone="warning" style={{ marginLeft: "var(--space-2)" }}>
                            Medical exemption
                          </Badge>
                        )}
                      </td>
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
                      <td colSpan={4} style={{ textAlign: "center", padding: "var(--space-6)", color: "var(--color-text-muted)" }}>
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
                  students.length === 0 ||
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

      <Panel title="Exam eligibility threshold (PO-T02)" description="Configure minimum attendance requirements for exam eligibility.">
        <div className="app-panel__body">
          <form onSubmit={saveThreshold}>
            <div className="app-form-grid">
              <TextField
                id="att-dept-id"
                label="Department ID (blank = institution default)"
                value={departmentId}
                onChange={(e) => setDepartmentId(e.target.value)}
              />
              <TextField
                id="att-threshold"
                label="Minimum attendance % *"
                type="number"
                min="0"
                max="100"
                step="0.01"
                required
                value={threshold}
                onChange={(e) => setThreshold(e.target.value)}
              />
            </div>
            <div style={{ marginTop: "var(--space-4)", display: "flex", justifyContent: "flex-end" }}>
              <Button type="submit" disabled={busy || !institutionId} busy={busy}>
                Save threshold
              </Button>
            </div>
          </form>
        </div>
      </Panel>

      <p style={{ fontSize: "var(--fs-caption)", color: "var(--color-text-muted)" }}>
        Students and primary guardians submit medical certificates from the student portal. Authorized approvers review requests in the medical exemption review workspace.
      </p>
    </main>
  );
}
