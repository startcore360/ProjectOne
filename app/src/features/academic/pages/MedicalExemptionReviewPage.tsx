import { useCallback, useState } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";

type Exemption = {
  attendance_medical_exemption_id: string;
  enrollment_id: string;
  student_profile_id: string;
  section_name: string;
  start_date: string;
  end_date: string;
  document_ref: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  requested_at: string;
  decision_note: string | null;
};

export default function MedicalExemptionReviewPage() {
  const tenantId = useAppSelector((s) => s.auth.user?.tenantId);
  const [institutionId, setInstitutionId] = useState("");
  const [departmentId, setDepartmentId] = useState("");
  const [rows, setRows] = useState<Exemption[]>([]);
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    if (!tenantId || !institutionId) return;
    setBusy(true);
    setError("");
    try {
      const response = await api.get<Exemption[]>("/api/tenants/" + tenantId + "/attendance/medical-exemptions", {
        params: { institutionId, departmentId: departmentId || undefined },
      });
      setRows(response.data);
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : "Unable to load medical exemption requests.");
    } finally {
      setBusy(false);
    }
  }, [tenantId, institutionId, departmentId]);

  async function decide(row: Exemption, decision: "APPROVED" | "REJECTED") {
    if (!tenantId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api.post("/api/tenants/" + tenantId + "/attendance/medical-exemptions/" +
        row.attendance_medical_exemption_id + "/decision", {
        institutionId,
        decision,
        note: notes[row.attendance_medical_exemption_id]?.trim() || null,
      });
      setMessage("Medical exemption " + decision.toLowerCase() + ".");
      await load();
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : "Unable to decide the medical exemption request.");
    } finally {
      setBusy(false);
    }
  }

  if (!tenantId) return <main className="page-container"><p>Loading account…</p></main>;
  return <main className="page-container">
    <header>
      <h1>Medical exemption review</h1>
      <p>Review doctor certificates and approve or reject requests. Decisions are blocked when the related academic term is locked.</p>
    </header>
    <div className="form-grid">
      <label>Institution ID<input value={institutionId} onChange={(event) => setInstitutionId(event.target.value)} /></label>
      <label>Department ID (blank for institution scope)<input value={departmentId} onChange={(event) => setDepartmentId(event.target.value)} /></label>
      <button type="button" disabled={busy || !institutionId} onClick={() => void load()}>Load requests</button>
    </div>
    {error && <p role="alert">{error}</p>}
    {message && <p role="status">{message}</p>}
    <div style={{ overflowX: "auto" }}>
      <table>
        <thead><tr><th>Student</th><th>Section</th><th>Absence period</th><th>Certificate reference</th><th>Status</th><th>Decision note</th><th>Actions</th></tr></thead>
        <tbody>
          {rows.map((row) => <tr key={row.attendance_medical_exemption_id}>
            <td>{row.student_profile_id.slice(0, 8)}</td>
            <td>{row.section_name}</td>
            <td>{row.start_date} – {row.end_date}</td>
            <td>{row.document_ref}</td>
            <td>{row.status}</td>
            <td>{row.status === "PENDING"
              ? <textarea value={notes[row.attendance_medical_exemption_id] ?? ""}
                  onChange={(event) => setNotes((current) => ({ ...current, [row.attendance_medical_exemption_id]: event.target.value }))}
                  maxLength={1000} />
              : row.decision_note ?? "—"}</td>
            <td>{row.status === "PENDING" && <>
              <button type="button" disabled={busy} onClick={() => void decide(row, "APPROVED")}>Approve</button>
              <button type="button" disabled={busy} onClick={() => void decide(row, "REJECTED")}>Reject</button>
            </>}</td>
          </tr>)}
          {rows.length === 0 && <tr><td colSpan={7}>No medical exemption requests loaded.</td></tr>}
        </tbody>
      </table>
    </div>
  </main>;
}
