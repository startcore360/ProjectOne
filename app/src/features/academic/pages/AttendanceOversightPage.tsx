import { useEffect, useMemo, useState } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";
import {
  Badge,
  Button,
  DateField,
  MetricCard,
  Notice,
  PageHeader,
  Panel,
  SelectField,
} from "../../../design-system/components";
import {
  institutionService,
  type InstitutionItem,
  type SectionItem,
} from "../../institution-admin/api/institutionService";

type AttendanceRow = {
  attendance_session_id: string;
  session_kind: string;
  attendance_date: string;
  held_status: "HELD" | "NOT_HELD";
  submitted_at?: string | null;
  enrollment_id: string | null;
  student_code: string | null;
  full_name: string | null;
  attendance_status: "PRESENT" | "ABSENT" | "ON_APPROVED_LEAVE" | "NOT_HELD" | null;
};

const todayLocal = () => {
  const now = new Date();
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, "0"), String(now.getDate()).padStart(2, "0")].join("-");
};

const statusLabel: Record<string, string> = {
  PRESENT: "Present",
  ABSENT: "Absent",
  ON_APPROVED_LEAVE: "On approved leave",
  NOT_HELD: "Not held",
};

export default function AttendanceOversightPage() {
  const tenantId = useAppSelector((s) => s.auth?.user?.tenantId);
  const activeInstitutionId = useAppSelector((s) => s.tenantPermissions?.current?.institutionId ?? "");
  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [sections, setSections] = useState<SectionItem[]>([]);
  const [institutionId, setInstitutionId] = useState(activeInstitutionId);
  const [sectionId, setSectionId] = useState("");
  const [date, setDate] = useState(todayLocal());
  const [rows, setRows] = useState<AttendanceRow[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!tenantId) return;
    void institutionService.listInstitutions(tenantId).then((res) => {
      const items = Array.isArray(res?.items) ? res.items : [];
      setInstitutions(items);
      if (!institutionId && items[0]) setInstitutionId(items[0].institutionId);
    }).catch(() => setError("Institutions could not be loaded."));
  }, [tenantId, institutionId]);

  useEffect(() => {
    if (!tenantId || !institutionId) {
      setSections([]);
      return;
    }
    void institutionService.listSections(tenantId, institutionId)
      .then((items) => setSections(Array.isArray(items) ? items.filter((item) => item?.sectionId) : []))
      .catch(() => setSections([]));
  }, [tenantId, institutionId]);

  async function loadReview() {
    if (!tenantId || !institutionId || !sectionId) return;
    setLoading(true);
    setError("");
    try {
      const response = await api.get<AttendanceRow[]>(
        `/api/tenants/${tenantId}/academic/attendance/register`,
        { params: { institutionId, sectionId, date } }
      );
      const result = Array.isArray(response.data) ? response.data : [];
      setRows(result);
      const sessionIds = Array.from(new Set(result.map((row) => row.attendance_session_id).filter(Boolean)));
      setSelectedSessionId((current) => sessionIds.includes(current) ? current : sessionIds[0] ?? "");
    } catch (cause) {
      setRows([]);
      setSelectedSessionId("");
      setError(cause instanceof Error ? cause.message : "Attendance review could not be loaded. Confirm that the selected section is within your department scope.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (sectionId) void loadReview();
    else {
      setRows([]);
      setSelectedSessionId("");
    }
  // loadReview intentionally follows the selected scope and date.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tenantId, institutionId, sectionId, date]);

  const sessions = useMemo(() => {
    const unique = new Map<string, AttendanceRow>();
    rows.forEach((row) => {
      if (row.attendance_session_id && !unique.has(row.attendance_session_id)) unique.set(row.attendance_session_id, row);
    });
    return Array.from(unique.values());
  }, [rows]);

  const selectedRows = useMemo(
    () => rows.filter((row) => row.attendance_session_id === selectedSessionId && row.enrollment_id),
    [rows, selectedSessionId]
  );
  const counts = useMemo(() => ({
    present: selectedRows.filter((row) => row.attendance_status === "PRESENT").length,
    absent: selectedRows.filter((row) => row.attendance_status === "ABSENT").length,
    leave: selectedRows.filter((row) => row.attendance_status === "ON_APPROVED_LEAVE").length,
    notHeld: selectedRows.filter((row) => row.attendance_status === "NOT_HELD").length,
    total: selectedRows.length,
  }), [selectedRows]);
  const heldCount = counts.present + counts.absent;
  const attendanceRate = heldCount ? Math.round((counts.present / heldCount) * 1000) / 10 : null;
  const activeSession = sessions.find((item) => item.attendance_session_id === selectedSessionId);

  return (
    <main className="app-page attendance-oversight">
      <PageHeader
        title="Department attendance review"
        description="Review submitted attendance for a section within your authorized department scope. Student-level marking remains with assigned teachers."
        actions={<a className="eds-button eds-button--secondary" href="/academic/attendance">Open teacher register</a>}
      />

      <Panel title="Review scope" description="Choose an institution, section and date. The API enforces your department-level access.">
        <div className="app-panel__body">
          <div className="app-form-grid">
            <SelectField id="hod-att-institution" label="Institution" value={institutionId}
              onChange={(event) => { setInstitutionId(event.target.value); setSectionId(""); setRows([]); }}
              options={[{ value: "", label: "Choose institution" }, ...institutions.map((item) => ({ value: item.institutionId, label: item.institutionName }))]} />
            <SelectField id="hod-att-section" label="Class / section" value={sectionId}
              onChange={(event) => setSectionId(event.target.value)}
              options={[{ value: "", label: "Choose section" }, ...sections.map((item) => ({ value: item.sectionId, label: item.sectionName }))]} />
            <DateField id="hod-att-date" label="Attendance date" value={date} onChange={(event) => setDate(event.target.value)} />
          </div>
          <div className="attendance-oversight__toolbar">
            <span className="eds-caption">Data shown is limited by the active role and department scope.</span>
            <Button type="button" variant="secondary" busy={loading} disabled={!sectionId || loading} onClick={() => void loadReview()}>Refresh review</Button>
          </div>
        </div>
      </Panel>

      {error && <Notice tone="danger">{error}</Notice>}

      {sectionId && !loading && !error && rows.length === 0 && (
        <Notice tone="info">No attendance sessions are recorded for this section on the selected date.</Notice>
      )}

      {sessions.length > 0 && (
        <>
          <Panel title="Session overview" description="Select a session to review its submitted register.">
            <div className="app-panel__body">
              <div className="app-form-grid">
                <SelectField id="hod-att-session" label="Attendance session" value={selectedSessionId}
                  onChange={(event) => setSelectedSessionId(event.target.value)}
                  options={sessions.map((item) => ({
                    value: item.attendance_session_id,
                    label: `${item.session_kind.replaceAll("_", " ")} · ${item.held_status === "NOT_HELD" ? "Not held" : "Held"}`,
                  }))} />
              </div>
              {activeSession && (
                <div className="attendance-oversight__session-meta">
                  <Badge tone={activeSession.submitted_at ? "success" : "warning"}>{activeSession.submitted_at ? "Submitted" : "Not confirmed submitted"}</Badge>
                  <span>{activeSession.session_kind.replaceAll("_", " ")}</span>
                  <span>{activeSession.held_status === "NOT_HELD" ? "Session not held" : "Teaching session"}</span>
                </div>
              )}
            </div>
          </Panel>

          <div className="kpi-grid">
            <MetricCard label="Present" value={counts.present} />
            <MetricCard label="Absent" value={counts.absent} />
            <MetricCard label="Approved leave" value={counts.leave} />
            <MetricCard label="Attendance rate" value={attendanceRate === null ? "—" : `${attendanceRate}%`} />
          </div>

          <Panel title="Student register" description="Read-only review. Corrections must follow the approved correction workflow; this screen does not edit teacher entries.">
            <div className="app-panel__body">
              <div className="attendance-oversight__table-wrap">
                <table className="eds-table">
                  <thead><tr><th>Roll / student ID</th><th>Student</th><th>Recorded status</th></tr></thead>
                  <tbody>
                    {selectedRows.map((row) => (
                      <tr key={row.enrollment_id ?? row.student_code}>
                        <td>{row.student_code || "—"}</td>
                        <td>{row.full_name || "Student record"}</td>
                        <td><Badge tone={row.attendance_status === "PRESENT" ? "success" : row.attendance_status === "ABSENT" ? "danger" : "neutral"}>{statusLabel[row.attendance_status ?? ""] ?? "Not recorded"}</Badge></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="eds-caption attendance-oversight__footnote">
                {counts.total} student records · {counts.notHeld} not-held entries excluded from attendance rate.
                {activeSession?.submitted_at ? " This session is submitted." : " Submission state is not confirmed by the current API response."}
              </p>
            </div>
          </Panel>
        </>
      )}

      <Panel title="Correction approvals" description="College corrections are approved by the HOD. The current AttendanceController does not yet expose the V4.0 correction-request endpoints.">
        <div className="app-panel__body">
          <Notice tone="warning">Approval actions are intentionally not shown until the correction-request API is implemented and verified. This prevents an apparent approval control from failing or bypassing the required original-value audit trail.</Notice>
        </div>
      </Panel>
    </main>
  );
}
