import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";

export interface DepartmentOption {
  departmentId: string;
  departmentCode: string;
  departmentName: string;
}

export interface AddProgramModalProps {
  isOpen: boolean;
  onClose: () => void;
  departments: DepartmentOption[];
  onSubmit: (data: {
    departmentId: string;
    programCode: string;
    programName: string;
    durationYears: number;
    semestersPerYear: number;
  }) => Promise<void>;
  submitting: boolean;
  error: string | null;
}

export function AddProgramModal({
  isOpen,
  onClose,
  departments,
  onSubmit,
  submitting,
  error,
}: AddProgramModalProps) {
  const [deptId, setDeptId] = useState(departments[0]?.departmentId ?? "");
  const [progCode, setProgCode] = useState("");
  const [progName, setProgName] = useState("");
  const [durationYears, setDurationYears] = useState(4);
  const [semestersPerYear, setSemestersPerYear] = useState(2);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    await onSubmit({
      departmentId: deptId || departments[0]?.departmentId || "",
      programCode: progCode.trim(),
      programName: progName.trim(),
      durationYears,
      semestersPerYear,
    });
    setProgCode("");
    setProgName("");
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Add Degree Program"
      titleId="add-prog-title"
      description="Create a degree program under a department [STRUCTURE_MANAGE]."
      size="md"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }}>
        {error && (
          <div style={{ marginBottom: "1rem" }}>
            <Notice tone="danger">{error}</Notice>
          </div>
        )}
        <div className="platform-form-field" style={{ marginBottom: "1rem" }}>
          <label htmlFor="prog-dept-select" style={{ display: "block", fontWeight: 600, marginBottom: "0.25rem" }}>
            Owning Department
          </label>
          <select
            id="prog-dept-select"
            value={deptId || departments[0]?.departmentId || ""}
            onChange={(e) => setDeptId(e.target.value)}
            style={{ width: "100%", padding: "0.5rem", borderRadius: "4px", border: "1px solid var(--color-border, #cbd5e1)" }}
          >
            {departments.map((d) => (
              <option key={d.departmentId} value={d.departmentId}>
                {d.departmentCode} — {d.departmentName}
              </option>
            ))}
          </select>
        </div>
        <div style={{ marginBottom: "1rem" }}>
          <TextField
            id="prog-code-input"
            label="Program Code"
            required
            value={progCode}
            onChange={(e) => setProgCode(e.target.value)}
            placeholder="e.g. BTECH_CSE"
          />
        </div>
        <div style={{ marginBottom: "1rem" }}>
          <TextField
            id="prog-name-input"
            label="Program Name"
            required
            value={progName}
            onChange={(e) => setProgName(e.target.value)}
            placeholder="e.g. Bachelor of Technology in Computer Science"
          />
        </div>
        <div className="platform-form-grid" style={{ marginBottom: "1rem" }}>
          <div>
            <TextField
              id="prog-duration-input"
              label="Duration (Years)"
              type="number"
              required
              value={String(durationYears)}
              onChange={(e) => setDurationYears(Number(e.target.value))}
            />
          </div>
          <div>
            <TextField
              id="prog-sems-input"
              label="Semesters / Year"
              type="number"
              required
              value={String(semestersPerYear)}
              onChange={(e) => setSemestersPerYear(Number(e.target.value))}
            />
          </div>
        </div>
        <footer style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" busy={submitting}>
            Create Program
          </Button>
        </footer>
      </form>
    </Modal>
  );
}

export default AddProgramModal;
