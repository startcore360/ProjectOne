import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import { Button, Notice, PageHeader, Panel, TextField } from "../../../design-system/components";
import { getApiErrorMessage } from "../../../services/apiHelper";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";

type Cycle = {
  id: string; institution_id: string; academic_year_id: string; name: string;
  start_date: string; end_date: string; status: string; row_version: number;
};

export default function StaffAppraisalPage() {
  const { institutionId } = useParams<{ institutionId: string }>();
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [cycles, setCycles] = useState<Cycle[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [feedback, setFeedback] = useState("");
  const [draft, setDraft] = useState({ academicYearId: "", name: "", startDate: "", endDate: "" });
  const [selectedCycle, setSelectedCycle] = useState("");
  const [selfScore, setSelfScore] = useState("");


  const load = useCallback(async () => {
    if (!tenantId || !institutionId) return;
    setLoading(true);
    setError("");
    try {
      const response = await api.get<Cycle[]>(`/api/tenants/${tenantId}/institutions/${institutionId}/appraisals/cycles`);
      setCycles(response.data ?? []);
    } catch (e) { setError(getApiErrorMessage(e)); }
    finally { setLoading(false); }
  }, [tenantId, institutionId]);

  useEffect(() => { void load(); }, [load]);

  const createCycle = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !institutionId) return;
    setSaving(true); setError(""); setFeedback("");
    try {
      await api.post(`/api/tenants/${tenantId}/institutions/${institutionId}/appraisals/cycles`, {
        academicYearId: draft.academicYearId, name: draft.name, startDate: draft.startDate, endDate: draft.endDate,
      });
      setDraft({ academicYearId: "", name: "", startDate: "", endDate: "" });
      setFeedback("Appraisal cycle created in Draft status.");
      await load();
    } catch (e) { setError(getApiErrorMessage(e)); }
    finally { setSaving(false); }
  };

  const submitSelfEvaluation = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !selectedCycle) return;
    setSaving(true); setError(""); setFeedback("");
    try {
      await api.post(`/api/tenants/${tenantId}/appraisals/cycles/${selectedCycle}/self-evaluation`, {
        score: Number(selfScore), commentsJson: JSON.stringify({ source: "staff-self-evaluation" }),
      });
      setFeedback("Self-evaluation submitted.");
      setSelfScore("");
    } catch (e) { setError(getApiErrorMessage(e)); }
    finally { setSaving(false); }
  };

  return (
    <div className="page-container">
      <PageHeader title="Staff Performance & Appraisals" description="Annual appraisal cycles, self-evaluations and review workflow." />
      {error && <Notice tone="danger">{error}</Notice>}
      {feedback && <Notice tone="success">{feedback}</Notice>}
      <Panel title="Appraisal cycles">
        {loading ? <p>Loading cycles…</p> : cycles.length === 0 ? <p>No appraisal cycles are configured for this institution.</p> : (
          <div className="table-responsive">
            <table><thead><tr><th>Cycle</th><th>Academic year</th><th>Dates</th><th>Status</th></tr></thead>
              <tbody>{cycles.map((cycle) => <tr key={cycle.id}>
                <td>{cycle.name}</td><td>{cycle.academic_year_id}</td>
                <td>{cycle.start_date} – {cycle.end_date}</td><td>{cycle.status}</td>
              </tr>)}</tbody>
            </table>
          </div>
        )}
      </Panel>
      <Panel title="Create annual cycle">
        <form onSubmit={createCycle} className="form-grid">
          <TextField id="academicYearId" label="Academic year ID" value={draft.academicYearId} onChange={(event) => setDraft((v) => ({ ...v, academicYearId: event.target.value }))} required />
          <TextField id="cycleName" label="Cycle name" value={draft.name} onChange={(event) => setDraft((v) => ({ ...v, name: event.target.value }))} required />
          <TextField id="cycleStartDate" label="Start date" type="date" value={draft.startDate} onChange={(event) => setDraft((v) => ({ ...v, startDate: event.target.value }))} required />
          <TextField id="cycleEndDate" label="End date" type="date" value={draft.endDate} onChange={(event) => setDraft((v) => ({ ...v, endDate: event.target.value }))} required />
          <Button type="submit" disabled={saving}>{saving ? "Saving…" : "Create cycle"}</Button>
        </form>
      </Panel>
      <Panel title="Self-evaluation">
        <form onSubmit={submitSelfEvaluation} className="form-grid">
          <label>Open cycle<select value={selectedCycle} onChange={(event) => setSelectedCycle(event.target.value)} required>
            <option value="">Select a cycle</option>
            {cycles.filter((cycle) => cycle.status === "OPEN").map((cycle) => <option key={cycle.id} value={cycle.id}>{cycle.name}</option>)}
          </select></label>
          <TextField id="selfScore" label="Self score (0–100)" type="number" min="0" max="100" value={selfScore} onChange={(event) => setSelfScore(event.target.value)} required />
          <Button type="submit" disabled={saving || !selectedCycle}>{saving ? "Submitting…" : "Submit self-evaluation"}</Button>
        </form>
      </Panel>
      <p className="muted">Scoring placeholder: 20% self-evaluation, 50% HOD score and 30% principal score. Peer feedback is qualitative and does not affect the numeric rating. This provisional weighting and promotion-readiness status require formal business-rule approval.</p>
    </div>
  );
}
