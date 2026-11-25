import { useEffect, useState } from "react";
import api from "../../../services/axios";
import { useAppSelector } from "../../../store/hooks";
import MedicalExemptionModal from "../components/MedicalExemptionModal";

type Enrollment = {
  enrollment_id: string; institution_id: string; academic_year: string; section_name: string;
};

export default function StudentMedicalExemptionPage() {
  const tenantId = useAppSelector((s) => s.auth.user?.tenantId);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [selected, setSelected] = useState("");
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);

  useEffect(() => {
    if (!tenantId) return;
    void api.get<Enrollment[]>("/api/tenants/" + tenantId + "/attendance/medical-exemptions/my-enrollments")
      .then((response) => {
        setEnrollments(response.data);
        if (response.data.length) setSelected(response.data[0].enrollment_id);
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Unable to load your active enrolments."))
      .finally(() => setBusy(false));
  }, [tenantId]);

  if (!tenantId) return <main className="page-container"><p>Loading account…</p></main>;

  const enrollment = enrollments.find((item) => item.enrollment_id === selected);
  return <main className="page-container">
    <header><h1>Medical attendance exemption</h1><p>Submit a doctor certificate for an absence period. Approved exemptions are included in your adjusted attendance percentage.</p></header>
    {error && <p role="alert">{error}</p>}
    {submitted && <p role="status">Your medical exemption request has been submitted for review.</p>}
    {busy ? <p>Loading active enrolments…</p> : enrollments.length === 0
      ? <p>No active enrolments are available for this account.</p>
      : <>
        <label>Enrolment<select value={selected} onChange={(e) => setSelected(e.target.value)}>
          {enrollments.map((item) => <option key={item.enrollment_id} value={item.enrollment_id}>
            {item.academic_year} · {item.section_name}
          </option>)}
        </select></label>
        {enrollment && <button type="button" onClick={() => setModalOpen(true)}>Request medical exemption</button>}
        {modalOpen && enrollment && tenantId && <MedicalExemptionModal tenantId={tenantId}
          institutionId={enrollment.institution_id} enrollmentId={enrollment.enrollment_id}
          onClose={() => setModalOpen(false)} onSubmitted={() => setSubmitted(true)} />}
      </>}
  </main>;
}