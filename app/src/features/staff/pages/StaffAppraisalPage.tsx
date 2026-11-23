import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import { Button, Notice, PageHeader, Panel, TextAreaField, TextField } from "../../../design-system/components";
import { getApiErrorMessage } from "../../../services/apiHelper";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";

type Cycle = { id: string; institution_id: string; academic_year_id: string; name: string; start_date: string; end_date: string; status: string; row_version: number };
type Submission = { id: string; staff_id: string; self_score: number | null; hod_score: number | null; principal_score: number | null; final_rating: number | null; status: string; comments_json?: Record<string, unknown> };
type Kpi = { label: string; score: number; weight: number };

const INITIAL_KPIS: Kpi[] = [
  { label: "KPI 1", score: 50, weight: 25 },
  { label: "KPI 2", score: 50, weight: 25 },
  { label: "KPI 3", score: 50, weight: 25 },
  { label: "KPI 4", score: 50, weight: 25 },
];

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
  const [selfScore, setSelfScore] = useState("50");
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [selectedSubmission, setSelectedSubmission] = useState("");
  const [peerFeedback, setPeerFeedback] = useState("");
  const [hodScore, setHodScore] = useState("50");
  const [hodComments, setHodComments] = useState("");
  const [principalScore, setPrincipalScore] = useState("50");
  const [principalComments, setPrincipalComments] = useState("");
  const [promotionReadiness, setPromotionReadiness] = useState("NOT_ASSESSED");
  const [kpis, setKpis] = useState<Kpi[]>(INITIAL_KPIS);

  const load = useCallback(async () => {
    if (!tenantId || !institutionId) return;
    setLoading(true); setError("");
    try {
      const response = await api.get<Cycle[]>(`/api/tenants/${tenantId}/institutions/${institutionId}/appraisals/cycles`);
      setCycles(response.data ?? []);
    } catch (e) { setError(getApiErrorMessage(e)); }
    finally { setLoading(false); }
  }, [tenantId, institutionId]);

  const loadSubmissions = useCallback(async () => {
    if (!tenantId || !selectedCycle) { setSubmissions([]); return; }
    try {
      const response = await api.get<Submission[]>(`/api/tenants/${tenantId}/appraisals/cycles/${selectedCycle}/submissions`);
      setSubmissions(response.data ?? []);
    } catch (e) { setError(getApiErrorMessage(e)); }
  }, [tenantId, selectedCycle]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { void loadSubmissions(); }, [loadSubmissions]);

  const weightedKpiScore = useMemo(() => {
    const totalWeight = kpis.reduce((sum, item) => sum + item.weight, 0);
    return totalWeight === 0 ? 0 : kpis.reduce((sum, item) => sum + item.score * item.weight, 0) / totalWeight;
  }, [kpis]);
  const totalWeight = kpis.reduce((sum, item) => sum + item.weight, 0);

  const mutate = async (action: () => Promise<unknown>, success: string, after?: () => Promise<void>) => {
    setSaving(true); setError(""); setFeedback("");
    try { await action(); setFeedback(success); if (after) await after(); }
    catch (e) { setError(getApiErrorMessage(e)); }
    finally { setSaving(false); }
  };

  const createCycle = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !institutionId) return;
    await mutate(() => api.post(`/api/tenants/${tenantId}/institutions/${institutionId}/appraisals/cycles`, {
      academicYearId: draft.academicYearId, name: draft.name, startDate: draft.startDate, endDate: draft.endDate,
    }), "Appraisal cycle created in Draft status.", async () => {
      setDraft({ academicYearId: "", name: "", startDate: "", endDate: "" }); await load();
    });
  };

  const setCycleStatus = async (cycleId: string, status: "OPEN" | "CLOSED") => {
    if (!tenantId) return;
    await mutate(() => api.patch(`/api/tenants/${tenantId}/appraisals/cycles/${cycleId}/status`, { status }),
      `Cycle ${status.toLowerCase()}.`, load);
  };

  const submitSelfEvaluation = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !selectedCycle) return;
    await mutate(() => api.post(`/api/tenants/${tenantId}/appraisals/cycles/${selectedCycle}/self-evaluation`, {
      score: Number(selfScore), commentsJson: JSON.stringify({ source: "staff-self-evaluation", provisionalKpiScore: weightedKpiScore, provisionalKpiWeights: kpis }),
    }), "Self-evaluation submitted.", loadSubmissions);
  };

  const submitPeerFeedback = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !selectedSubmission) return;
    await mutate(() => api.post(`/api/tenants/${tenantId}/appraisals/submissions/${selectedSubmission}/peer-reviews`, { feedback: peerFeedback }),
      "Qualitative peer feedback submitted.", async () => { setPeerFeedback(""); await loadSubmissions(); });
  };

  const submitHodScore = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !selectedSubmission) return;
    await mutate(() => api.post(`/api/tenants/${tenantId}/appraisals/submissions/${selectedSubmission}/hod-score`, {
      score: Number(hodScore), commentsJson: JSON.stringify({ reviewComments: hodComments, provisionalKpiScore: weightedKpiScore, provisionalKpiWeights: kpis }),
    }), "HOD score saved.", loadSubmissions);
  };

  const submitPrincipalReview = async (event: FormEvent) => {
    event.preventDefault();
    if (!tenantId || !selectedSubmission) return;
    await mutate(() => api.post(`/api/tenants/${tenantId}/appraisals/submissions/${selectedSubmission}/principal-review`, {
      score: Number(principalScore), commentsJson: JSON.stringify({ reviewComments: principalComments, provisionalKpiScore: weightedKpiScore, provisionalKpiWeights: kpis }),
      promotionReadiness,
    }), "Principal review saved.", loadSubmissions);
  };

  const updateKpi = (index: number, field: "score" | "weight" | "label", value: string) => {
    setKpis((current) => current.map((item, i) => i === index ? { ...item, [field]: field === "label" ? value : Number(value) } : item));
  };

  return (
    <div className="page-container appraisal-page">
      <PageHeader title="Staff Performance & Appraisals" description="Annual appraisal cycles, self-evaluations, peer feedback and review workflow." />
      {error && <Notice tone="danger">{error}</Notice>}
      {feedback && <Notice tone="success">{feedback}</Notice>}

      <Panel title="Appraisal cycles" description="Manage the annual appraisal window.">
        {loading ? <p>Loading cycles…</p> : cycles.length === 0 ? <p>No appraisal cycles are configured for this institution.</p> : (
          <div className="table-responsive"><table><thead><tr><th>Cycle</th><th>Academic year</th><th>Dates</th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>{cycles.map((cycle) => <tr key={cycle.id}>
              <td>{cycle.name}</td><td>{cycle.academic_year_id}</td><td>{cycle.start_date} – {cycle.end_date}</td><td>{cycle.status}</td>
              <td>{cycle.status === "DRAFT" && <Button size="sm" disabled={saving} onClick={() => void setCycleStatus(cycle.id, "OPEN")}>Open</Button>}
                {cycle.status === "OPEN" && <Button size="sm" variant="secondary" disabled={saving} onClick={() => void setCycleStatus(cycle.id, "CLOSED")}>Close</Button>}</td>
            </tr>)}</tbody></table></div>
        )}
      </Panel>

      <Panel title="Create annual cycle">
        <form onSubmit={(event) => void createCycle(event)} className="form-grid">
          <TextField id="academicYearId" label="Academic year ID" value={draft.academicYearId} onChange={(event) => setDraft((v) => ({ ...v, academicYearId: event.target.value }))} required />
          <TextField id="cycleName" label="Cycle name" value={draft.name} onChange={(event) => setDraft((v) => ({ ...v, name: event.target.value }))} required />
          <TextField id="cycleStartDate" label="Start date" type="date" value={draft.startDate} onChange={(event) => setDraft((v) => ({ ...v, startDate: event.target.value }))} required />
          <TextField id="cycleEndDate" label="End date" type="date" value={draft.endDate} onChange={(event) => setDraft((v) => ({ ...v, endDate: event.target.value }))} required />
          <Button type="submit" disabled={saving}>{saving ? "Saving…" : "Create cycle"}</Button>
        </form>
      </Panel>

      <Panel title="KPI scorecard" description="Provisional, editable score dimensions. Replace KPI 1–4 with institution-approved measures when defined.">
        <div className="appraisal-kpi-list">
          {kpis.map((item, index) => <div className="appraisal-kpi" key={index}>
            <TextField id={`kpi-label-${index}`} label={`KPI ${index + 1} label`} value={item.label} onChange={(event) => updateKpi(index, "label", event.target.value)} />
            <label>Score: <strong>{item.score}</strong>/100
              <input aria-label={`${item.label} score`} type="range" min="0" max="100" step="1" value={item.score} onChange={(event) => updateKpi(index, "score", event.target.value)} />
            </label>
            <label>Weight: <strong>{item.weight}%</strong>
              <input aria-label={`${item.label} weight`} type="range" min="0" max="100" step="1" value={item.weight} onChange={(event) => updateKpi(index, "weight", event.target.value)} />
            </label>
          </div>)}
        </div>
        <p><strong>Provisional weighted KPI score:</strong> {weightedKpiScore.toFixed(2)} / 100 · <strong>Weight total:</strong> {totalWeight}%</p>
        <Notice tone="warning">KPI labels and weights are placeholders, not an approved institutional framework. The score is normalized against the entered weight total and is stored as review context; it does not replace the provisional 20/50/30 final-rating formula.</Notice>
      </Panel>

      <Panel title="Self-evaluation">
        <form onSubmit={(event) => void submitSelfEvaluation(event)} className="form-grid">
          <label>Open cycle<select value={selectedCycle} onChange={(event) => setSelectedCycle(event.target.value)} required>
            <option value="">Select a cycle</option>{cycles.filter((cycle) => cycle.status === "OPEN").map((cycle) => <option key={cycle.id} value={cycle.id}>{cycle.name}</option>)}
          </select></label>
          <TextField id="selfScore" label="Self score (0–100)" type="number" min="0" max="100" value={selfScore} onChange={(event) => setSelfScore(event.target.value)} required />
          <Button type="submit" disabled={saving || !selectedCycle}>{saving ? "Submitting…" : "Submit self-evaluation"}</Button>
        </form>
      </Panel>

      <Panel title="Appraisal submissions" description="Select a submission to record peer feedback, HOD scoring or the principal review.">
        <label>Submission<select value={selectedSubmission} onChange={(event) => setSelectedSubmission(event.target.value)}>
          <option value="">Select a submission</option>{submissions.map((item) => <option key={item.id} value={item.id}>{item.staff_id} · {item.status}</option>)}
        </select></label>
        {submissions.length === 0 ? <p>No submissions are available for the selected cycle.</p> : (
          <div className="table-responsive"><table><thead><tr><th>Staff ID</th><th>Self</th><th>HOD</th><th>Principal</th><th>Final</th><th>Status</th></tr></thead>
            <tbody>{submissions.map((item) => <tr key={item.id}><td>{item.staff_id}</td><td>{item.self_score ?? "—"}</td><td>{item.hod_score ?? "—"}</td><td>{item.principal_score ?? "—"}</td><td>{item.final_rating ?? "—"}</td><td>{item.status}</td></tr>)}</tbody></table></div>
        )}
      </Panel>

      <Panel title="Peer feedback">
        <form onSubmit={(event) => void submitPeerFeedback(event)} className="form-grid">
          <TextAreaField id="peerFeedback" label="Qualitative peer feedback" value={peerFeedback} onChange={(event) => setPeerFeedback(event.target.value)} rows={4} required />
          <Button type="submit" disabled={saving || !selectedSubmission || !peerFeedback.trim()}>Submit peer feedback</Button>
        </form>
        <p className="muted">Peer feedback is qualitative and does not change the numeric final rating.</p>
      </Panel>

      <Panel title="HOD KPI review">
        <form onSubmit={(event) => void submitHodScore(event)} className="form-grid">
          <TextField id="hodScore" label="HOD score (0–100)" type="number" min="0" max="100" value={hodScore} onChange={(event) => setHodScore(event.target.value)} required />
          <TextAreaField id="hodComments" label="Qualitative HOD review" value={hodComments} onChange={(event) => setHodComments(event.target.value)} rows={3} />
          <Button type="submit" disabled={saving || !selectedSubmission}>Save HOD review</Button>
        </form>
      </Panel>

      <Panel title="Principal review and promotion readiness">
        <form onSubmit={(event) => void submitPrincipalReview(event)} className="form-grid">
          <TextField id="principalScore" label="Principal score (0–100)" type="number" min="0" max="100" value={principalScore} onChange={(event) => setPrincipalScore(event.target.value)} required />
          <label>Promotion readiness<select value={promotionReadiness} onChange={(event) => setPromotionReadiness(event.target.value)}>
            <option value="NOT_ASSESSED">Not assessed</option><option value="READY_FOR_REVIEW">Ready for review</option><option value="NOT_READY">Not ready</option>
          </select></label>
          <TextAreaField id="principalComments" label="Principal qualitative review" value={principalComments} onChange={(event) => setPrincipalComments(event.target.value)} rows={3} />
          <Button type="submit" disabled={saving || !selectedSubmission}>Save principal review</Button>
        </form>
        <Notice tone="warning">Promotion readiness is an explicit reviewer judgement. No numeric promotion threshold is implemented because none has been approved.</Notice>
      </Panel>

      <Panel title="Appraisal report">
        <p>Print or save the current appraisal workspace as PDF using the browser print dialog.</p>
        <Button variant="secondary" onClick={() => window.print()}>Export / Print to PDF</Button>
      </Panel>
      <p className="muted">Provisional final-rating formula: 20% self-evaluation + 50% HOD + 30% principal. This is a documented placeholder pending business-rule approval.</p>
    </div>
  );
}
