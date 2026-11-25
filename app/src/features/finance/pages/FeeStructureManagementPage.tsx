import { useCallback, useEffect, useState } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";

type FeeStructure = {
  feeStructureId: string;
  institutionId: string;
  academicSessionId: string;
  versionNo: number;
  status: string;
  totalAmount: number;
  installmentCount: number;
  appliesFromDate: string | null;
};

const inputClass = "w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100";
const labelClass = "mb-1 block text-sm font-medium text-slate-700";

export default function FeeStructureManagementPage() {
  const tenantId = useAppSelector((state) => state.auth.user?.tenantId);
  const [institutionId, setInstitutionId] = useState("");
  const [academicSessionId, setAcademicSessionId] = useState("");
  const [programYearId, setProgramYearId] = useState("");
  const [classId, setClassId] = useState("");
  const [feePolicyId, setFeePolicyId] = useState("");
  const [appliesFromDate, setAppliesFromDate] = useState("");
  const [linesJson, setLinesJson] = useState('[{"feeHeadId":"","amount":0}]');
  const [installmentsJson, setInstallmentsJson] = useState('[{"installmentNo":1,"dueDate":"","amount":0}]');
  const [structures, setStructures] = useState<FeeStructure[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    if (!tenantId) return;
    try {
      const response = await api.get<FeeStructure[]>(`/tenants/${tenantId}/fee-structures`, {
        params: institutionId ? { institutionId } : undefined,
      });
      setStructures(response.data);
      setError("");
    } catch {
      setError("Unable to load fee structures. Check your role and institution scope.");
    }
  }, [tenantId, institutionId]);

  useEffect(() => { void load(); }, [load]);

  async function createStructure(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const lines = JSON.parse(linesJson) as Array<{ feeHeadId: string; amount: number }>;
      const installments = JSON.parse(installmentsJson) as Array<{ installmentNo: number; dueDate: string; amount: number }>;
      await api.post(`/tenants/${tenantId}/fee-structures`, {
        institutionId, academicSessionId, programYearId: programYearId || null,
        classId: classId || null, feePolicyId, appliesFromDate: appliesFromDate || null,
        lines, installments,
      });
      setNotice("Draft fee structure created.");
      await load();
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message;
      setError(message || "Could not create the fee structure. Check the JSON and required identifiers.");
    } finally {
      setBusy(false);
    }
  }

  async function submitForApproval(id: string) {
    if (!tenantId) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await api.post(`/tenants/${tenantId}/fee-structures/${id}/submit`);
      setNotice("Fee structure submitted for Principal approval.");
      await load();
    } catch {
      setError("Unable to submit this fee structure. Only an authorized Accountant can submit a draft.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto max-w-6xl space-y-6 p-4 md:p-6">
      <header>
        <p className="text-sm font-semibold uppercase tracking-wide text-indigo-600">Finance · Configuration</p>
        <h1 className="mt-1 text-2xl font-semibold text-slate-900">Fee structure management</h1>
        <p className="mt-2 text-sm text-slate-600">Create versioned fee structures, define fee-head lines and installment dates, then submit drafts for Principal approval.</p>
      </header>

      {error && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>}
      {notice && <div role="status" className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</div>}

      <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm md:p-6">
        <h2 className="text-lg font-semibold text-slate-900">Create a draft structure</h2>
        <form className="mt-4 space-y-4" onSubmit={createStructure}>
          <div className="grid gap-4 md:grid-cols-2">
            <label><span className={labelClass}>Institution ID</span><input className={inputClass} required value={institutionId} onChange={(e) => setInstitutionId(e.target.value)} /></label>
            <label><span className={labelClass}>Academic session ID</span><input className={inputClass} required value={academicSessionId} onChange={(e) => setAcademicSessionId(e.target.value)} /></label>
            <label><span className={labelClass}>Program year ID</span><input className={inputClass} value={programYearId} onChange={(e) => setProgramYearId(e.target.value)} placeholder="Use for college / program fee" /></label>
            <label><span className={labelClass}>Class ID</span><input className={inputClass} value={classId} onChange={(e) => setClassId(e.target.value)} placeholder="Use for school fee" /></label>
            <label><span className={labelClass}>Fee policy ID</span><input className={inputClass} required value={feePolicyId} onChange={(e) => setFeePolicyId(e.target.value)} /></label>
            <label><span className={labelClass}>Effective from</span><input className={inputClass} type="date" value={appliesFromDate} onChange={(e) => setAppliesFromDate(e.target.value)} /></label>
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <label><span className={labelClass}>Fee lines (JSON)</span><textarea className={inputClass + " min-h-36 font-mono"} value={linesJson} onChange={(e) => setLinesJson(e.target.value)} spellCheck={false} /><span className="mt-1 block text-xs text-slate-500">Each item: feeHeadId and amount. The line total must equal the installment total.</span></label>
            <label><span className={labelClass}>Installments (JSON)</span><textarea className={inputClass + " min-h-36 font-mono"} value={installmentsJson} onChange={(e) => setInstallmentsJson(e.target.value)} spellCheck={false} /><span className="mt-1 block text-xs text-slate-500">Each item: installmentNo, dueDate (YYYY-MM-DD) and amount.</span></label>
          </div>
          <button disabled={busy} className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50">{busy ? "Saving…" : "Create draft"}</button>
        </form>
      </section>

      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3 md:px-6">
          <h2 className="font-semibold text-slate-900">Existing fee structures</h2>
          <button onClick={() => void load()} className="text-sm font-medium text-indigo-700 hover:underline">Refresh</button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="px-4 py-3">Structure</th><th className="px-4 py-3">Version</th><th className="px-4 py-3">Total (INR)</th><th className="px-4 py-3">Installments</th><th className="px-4 py-3">Status</th><th className="px-4 py-3">Action</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {structures.map((item) => <tr key={item.feeStructureId}>
                <td className="max-w-48 truncate px-4 py-3 font-mono text-xs">{item.feeStructureId}</td>
                <td className="px-4 py-3">{item.versionNo}</td>
                <td className="px-4 py-3 tabular-nums">{Number(item.totalAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                <td className="px-4 py-3">{item.installmentCount}</td>
                <td className="px-4 py-3"><span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700">{item.status}</span></td>
                <td className="px-4 py-3">{item.status === "DRAFT" ? <button disabled={busy} onClick={() => void submitForApproval(item.feeStructureId)} className="font-medium text-indigo-700 hover:underline disabled:opacity-50">Submit for approval</button> : "—"}</td>
              </tr>)}
              {structures.length === 0 && <tr><td colSpan={6} className="px-4 py-8 text-center text-slate-500">No fee structures found for this scope.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  );
}
