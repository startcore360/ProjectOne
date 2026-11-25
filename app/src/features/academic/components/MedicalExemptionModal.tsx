import { useState } from "react";
import type { FormEvent } from "react";
import api from "../../../services/axios";

type Props = { tenantId: string; institutionId: string; enrollmentId: string; onClose: () => void; onSubmitted: () => void };

export default function MedicalExemptionModal({ tenantId, institutionId, enrollmentId, onClose, onSubmitted }: Props) {
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!file || !startDate || !endDate) { setError("Choose the medical certificate and exemption dates."); return; }
    setBusy(true); setError("");
    try {
      const data = new FormData();
      data.append("file", file);
      data.append("purpose", "ATTENDANCE_MEDICAL_EXEMPTION");
      data.append("institutionId", institutionId);
      const uploaded = await api.post("/api/tenants/" + tenantId + "/files", data, { headers: { "Content-Type": "multipart/form-data" } });
      if (uploaded.data.scanStatus !== "CLEAN" || uploaded.data.status !== "ACTIVE") {
        throw new Error("The certificate must pass the file security scan before it can be submitted.");
      }
      await api.post("/api/tenants/" + tenantId + "/attendance/medical-exemptions", {
        institutionId, enrollmentId, startDate, endDate, documentRef: uploaded.data.fileId,
      });
      onSubmitted(); onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to submit the medical exemption.");
    } finally { setBusy(false); }
  }

  return <div role="dialog" aria-modal="true" aria-labelledby="medical-exemption-title" className="modal-backdrop">
    <section className="modal-card">
      <header><h2 id="medical-exemption-title">Request medical attendance exemption</h2><p>Attach a scanned doctor certificate. The request remains pending until reviewed.</p></header>
      <form onSubmit={submit} className="form-grid">
        <label>From date<input type="date" required value={startDate} onChange={(e) => setStartDate(e.target.value)} /></label>
        <label>To date<input type="date" required min={startDate || undefined} value={endDate} onChange={(e) => setEndDate(e.target.value)} /></label>
        <label>Doctor certificate<input type="file" required accept=".pdf,image/png,image/jpeg" onChange={(e) => setFile(e.target.files?.[0] ?? null)} /></label>
        {error && <p role="alert">{error}</p>}
        <div><button disabled={busy}>{busy ? "Submitting…" : "Submit request"}</button> <button type="button" onClick={onClose}>Cancel</button></div>
      </form>
    </section>
  </div>;
}