import { useState, type FormEvent } from "react";
import { Button, DateField, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";

export interface AddAcademicSessionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: {
    sessionLabel: string;
    academicYear: number;
    startDate: string;
    endDate: string;
    status: string;
  }) => Promise<void>;
  submitting: boolean;
  error: string | null;
}

export function AddAcademicSessionModal({
  isOpen,
  onClose,
  onSubmit,
  submitting,
  error,
}: AddAcademicSessionModalProps) {
  const [sessionLabel, setSessionLabel] = useState("");
  const [academicYear, setAcademicYear] = useState(2026);
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [status, setStatus] = useState("PLANNED");

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    await onSubmit({
      sessionLabel: sessionLabel.trim(),
      academicYear,
      startDate,
      endDate,
      status,
    });
    setSessionLabel("");
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Create Academic Session"
      titleId="add-session-title"
      description="Define an academic session label and dates [CALENDAR_MASTER_SET]."
      size="md"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }}>
        {error && (
          <div style={{ marginBottom: "1rem" }}>
            <Notice tone="danger">{error}</Notice>
          </div>
        )}
        <div style={{ marginBottom: "1rem" }}>
          <TextField
            id="session-label-input"
            label="Session Label"
            required
            value={sessionLabel}
            onChange={(e) => setSessionLabel(e.target.value)}
            placeholder="e.g. 2026-27"
          />
        </div>
        <div className="platform-form-grid" style={{ marginBottom: "1rem" }}>
          <TextField
            id="session-academic-year"
            label="Academic year"
            type="number"
            value={academicYear}
            onChange={(e) => setAcademicYear(Number(e.target.value))}
            min={2000}
            max={2200}
            required
          />
          <SelectField
            id="session-status"
            label="Status"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            options={[
              { value: "PLANNED", label: "Planned" },
              { value: "ACTIVE", label: "Active" },
              { value: "CLOSED", label: "Closed" },
            ]}
          />
        </div>
        <div className="platform-form-grid" style={{ marginBottom: "1rem" }}>
          <DateField
            id="session-start-date"
            label="Start date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            required
          />
          <DateField
            id="session-end-date"
            label="End date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            required
          />
        </div>
        <footer style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" busy={submitting}>
            Create Session
          </Button>
        </footer>
      </form>
    </Modal>
  );
}

export default AddAcademicSessionModal;
