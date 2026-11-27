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

type Student = {
  enrollment_id: string;
  student_profile_id: string;
  student_code: string;
  full_name: string;
  has_medical_exemption?: boolean;
};
type Status = "PRESENT" | "ABSENT" | "ON_APPROVED_LEAVE" | "NOT_HELD" | "";
type SessionKind = "DAILY" | "PERIOD" | "SCHOOL_FIRST_HALF" | "SCHOOL_SECOND_HALF";

export default function AttendanceRegisterPage() {
  const tenantId = useAppSelector((s) => s.auth.user?.tenantId);
  const activeInstitutionId = useAppSelector((s) => s.tenantPermissions.current?.institutionId ?? "");

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

  useEffect(() => {
    if (activeInstitutionId && !institutionId) {
      setInstitutionId(activeInstitutionId);
    }
  }, [activeInstitutionId, institutionId]);

  const loadRoster = useCallback(async () => {
    if (!tenantId || !institutionId || !sectionId) return;
    setBusy(true);
    setFeedback("");
    try {
      const res = await api.get<Student[]>(
        "/api/tenants/" + tenantId + "/academic/attendance/sections/" + sectionId + "/roster",
        { params: { institutionId, date } }
      );
      setStudents(res.data);
      setMarks(Object.fromEntries(res.data.map((x) => [x.enrollment_id, ""])));
      setIsError(false);
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
        <div className="app-form-grid">
          <TextField
            id="att-institution-id"
            label="Institution ID *"
            value={institutionId}
            onChange={(e) => setInstitutionId(e.target.value)}
            required
          />
          <TextField
            id="att-section-id"
            label="Section ID *"
            value={sectionId}
            onChange={(e) => setSectionId(e.target.value)}
            required
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
              <TextField
                id="att-slot-id"
                label="Published timetable slot ID *"
                value={slotId}
                onChange={(e) => setSlotId(e.target.value)}
                required
              />
              <TextField
                id="att-subject-allocation-id"
                label="Subject allocation ID *"
                value={subjectAllocationId}
                onChange={(e) => setSubjectAllocationId(e.target.value)}
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
      </Panel>

      <Panel title="Register summary" description="Summary count of marked attendance.">
        <div className="kpi-grid">
          <MetricCard label="Present" value={counts.present} />
          <MetricCard label="Absent" value={counts.absent} />
          <MetricCard label="On approved leave" value={counts.leave} />
          <MetricCard label="Total students" value={students.length} />
        </div>
      </Panel>

      <Panel title="Roster & attendance sheet" description="Mark attendance per student or mark the entire session not held.">
        <form onSubmit={submit}>
          <div style={{ marginBottom: "var(--space-4)" }}>
            <CheckboxField
              id="att-not-held"
              label="Session not held"
              checked={notHeld}
              onChange={(e) => setNotHeld(e.target.checked)}
            />
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
      </Panel>

      <Panel title="Exam eligibility threshold (PO-T02)" description="Configure minimum attendance requirements for exam eligibility.">
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
      </Panel>

      <p style={{ fontSize: "var(--fs-caption)", color: "var(--color-text-muted)" }}>
        Students and primary guardians submit medical certificates from the student portal. Authorized approvers review requests in the medical exemption review workspace.
      </p>
    </main>
  );
}
