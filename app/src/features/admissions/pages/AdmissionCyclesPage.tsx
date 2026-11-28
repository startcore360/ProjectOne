import { useCallback, useEffect, useState, type FormEvent } from "react";
import api from "../../../services/axios";
import { getApiErrorMessage } from "../../../services/apiHelper";
import { useAppSelector } from "../../../store/hooks";
import { PageHeader } from "../../../components/layout/PageHeader";
import {
  Panel,
  Button,
  TextField,
  SelectField,
  DateField,
  Notice,
  DataTable,
  EmptyState,
} from "../../../design-system/components";
import {
  institutionService,
  type InstitutionItem,
  type AcademicSessionItem,
  type ProgramItem,
} from "../../institution-admin/api/institutionService";

type Cycle = {
  id: string;
  cycle_name: string;
  status: string;
  start_date: string;
  end_date: string;
  total_seats: number;
  application_fee: number;
};

export default function AdmissionCyclesPage() {
  const tenantId = useAppSelector((s) => s.auth.user?.tenantId);
  const [rows, setRows] = useState<Cycle[]>([]);
  const [institutions, setInstitutions] = useState<InstitutionItem[]>([]);
  const [sessions, setSessions] = useState<AcademicSessionItem[]>([]);
  const [programs, setPrograms] = useState<ProgramItem[]>([]);

  const [institutionId, setInstitutionId] = useState("");
  const [academicYearId, setAcademicYearId] = useState("");
  const [programId, setProgramId] = useState("");
  const [name, setName] = useState("");
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [seats, setSeats] = useState("");
  const [fee, setFee] = useState("0");
  const [general, setGeneral] = useState("0");
  const [merit, setMerit] = useState("0");
  const [sports, setSports] = useState("0");
  const [management, setManagement] = useState("0");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const load = useCallback(async () => {
    if (!tenantId) return;
    try {
      const r = await api.get(`/tenants/${tenantId}/admissions/cycles`);
      setRows(r.data);
    } catch (e) {
      setError(getApiErrorMessage(e));
    }
  }, [tenantId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!tenantId) return;
    institutionService
      .listInstitutions(tenantId)
      .then((res) => setInstitutions(res.items ?? []))
      .catch(() => setInstitutions([]));
  }, [tenantId]);

  useEffect(() => {
    if (!tenantId || !institutionId) {
      setSessions([]);
      setPrograms([]);
      setAcademicYearId("");
      setProgramId("");
      return;
    }
    institutionService
      .listAcademicSessions(tenantId, institutionId)
      .then((res) => setSessions(Array.isArray(res) ? res : []))
      .catch(() => setSessions([]));
    institutionService
      .listPrograms(tenantId, institutionId)
      .then((res) => setPrograms(Array.isArray(res) ? res : []))
      .catch(() => setPrograms([]));
  }, [tenantId, institutionId]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      await api.post(`/tenants/${tenantId}/admissions/cycles`, {
        institutionId,
        academicYearId,
        programId,
        cycleName: name,
        startDate: start,
        endDate: end,
        totalSeats: Number(seats),
        applicationFee: Number(fee),
        quotaCapacity: {
          GENERAL: Number(general),
          MERIT: Number(merit),
          SPORTS: Number(sports),
          MANAGEMENT: Number(management),
        },
      });
      setSuccess("Admission cycle created.");
      setName("");
      await load();
    } catch (e) {
      setError(getApiErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="app-page">
      <PageHeader
        title="Admission cycles"
        description="Configure program intake windows, application fees and seat capacity."
      />
      {error && <Notice tone="danger">{error}</Notice>}
      {success && <Notice tone="success">{success}</Notice>}
      <Panel title="Create admission cycle">
        <form onSubmit={submit} className="app-form-grid">
          <SelectField
            id="admission-institution"
            label="Institution *"
            required
            value={institutionId}
            onChange={(e) => setInstitutionId(e.target.value)}
            options={[
              { value: "", label: "-- Select institution --" },
              ...institutions.map((i) => ({
                value: i.institutionId,
                label: i.institutionName || i.institutionCode,
              })),
            ]}
          />
          <SelectField
            id="admission-year"
            label="Academic session *"
            required
            disabled={!institutionId}
            value={academicYearId}
            onChange={(e) => setAcademicYearId(e.target.value)}
            options={[
              {
                value: "",
                label: institutionId ? "-- Select academic session --" : "-- Choose institution first --",
              },
              ...sessions.map((s) => ({
                value: s.academicSessionId,
                label: s.sessionLabel,
              })),
            ]}
          />
          <SelectField
            id="admission-program"
            label="Program *"
            required
            disabled={!institutionId}
            value={programId}
            onChange={(e) => setProgramId(e.target.value)}
            options={[
              {
                value: "",
                label: institutionId ? "-- Select program --" : "-- Choose institution first --",
              },
              ...programs.map((p) => ({
                value: p.programId,
                label: `${p.programName} (${p.programCode})`,
              })),
            ]}
          />
          <TextField
            id="admission-cycle-name"
            label="Cycle name"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <DateField
            id="admission-start"
            label="Application opens"
            required
            value={start}
            onChange={(e) => setStart(e.target.value)}
          />
          <DateField
            id="admission-end"
            label="Application closes"
            required
            value={end}
            onChange={(e) => setEnd(e.target.value)}
          />
          <TextField
            id="admission-seats"
            label="Total seats"
            type="number"
            required
            value={seats}
            onChange={(e) => {
              setSeats(e.target.value);
              if (Number(general) + Number(merit) + Number(sports) + Number(management) === 0) {
                setGeneral(e.target.value);
              }
            }}
          />
          <TextField
            id="admission-fee"
            label="Application fee"
            type="number"
            value={fee}
            onChange={(e) => setFee(e.target.value)}
          />
          <TextField
            id="admission-quota-general"
            label="General quota seats"
            type="number"
            value={general}
            onChange={(e) => setGeneral(e.target.value)}
          />
          <TextField
            id="admission-quota-merit"
            label="Merit quota seats"
            type="number"
            value={merit}
            onChange={(e) => setMerit(e.target.value)}
          />
          <TextField
            id="admission-quota-sports"
            label="Sports quota seats"
            type="number"
            value={sports}
            onChange={(e) => setSports(e.target.value)}
          />
          <TextField
            id="admission-quota-management"
            label="Management quota seats"
            type="number"
            value={management}
            onChange={(e) => setManagement(e.target.value)}
          />
          <p>Quota allocations must be non-negative and total no more than the overall seat capacity.</p>
          <Button
            type="submit"
            busy={busy}
            disabled={
              !institutionId ||
              !academicYearId ||
              !programId ||
              !name ||
              !start ||
              !end ||
              !seats ||
              [general, merit, sports, management].some((v) => Number(v) < 0) ||
              Number(general) + Number(merit) + Number(sports) + Number(management) > Number(seats)
            }
          >
            Create cycle
          </Button>
        </form>
      </Panel>
      <Panel title="Configured cycles">
        {rows.length === 0 ? (
          <EmptyState title="No admission cycles" description="Create a cycle to begin applicant intake." />
        ) : (
          <DataTable headers={["Cycle", "Opens", "Closes", "Seats", "Status"]}>
            {rows.map((row) => (
              <tr key={row.id}>
                <td>{row.cycle_name}</td>
                <td>{row.start_date}</td>
                <td>{row.end_date}</td>
                <td>{row.total_seats}</td>
                <td>{row.status}</td>
              </tr>
            ))}
          </DataTable>
        )}
      </Panel>
    </div>
  );
}
