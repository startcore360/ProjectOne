import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, SelectField, TextField } from "../../../../design-system/components";

export interface AcademicSessionOption {
  academicSessionId: string;
  sessionLabel: string;
  startDate: string;
  endDate: string;
}

export interface AddMasterEventModalProps {
  isOpen: boolean;
  onClose: () => void;
  sessions: AcademicSessionOption[];
  onSubmit: (data: {
    academicSessionId: string;
    eventType: string;
    title: string;
    startDate: string;
    endDate: string;
  }) => Promise<void>;
  submitting: boolean;
  error: string | null;
}

export function AddMasterEventModal({
  isOpen,
  onClose,
  sessions,
  onSubmit,
  submitting,
  error,
}: AddMasterEventModalProps) {
  const [targetSessionId, setTargetSessionId] = useState(sessions[0]?.academicSessionId ?? "");
  const [masterEventType, setMasterEventType] = useState("SEMESTER_DATES");
  const [masterEventTitle, setMasterEventTitle] = useState("");
  const [masterEventStart, setMasterEventStart] = useState("");
  const [masterEventEnd, setMasterEventEnd] = useState("");

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    await onSubmit({
      academicSessionId: targetSessionId || sessions[0]?.academicSessionId || "",
      eventType: masterEventType,
      title: masterEventTitle.trim(),
      startDate: masterEventStart,
      endDate: masterEventEnd,
    });
    setMasterEventTitle("");
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Add Master Calendar Event"
      titleId="add-master-event-title"
      description="Set semester dates, university holidays or exam days [CALENDAR_MASTER_SET, CL-01/CL-02]."
      size="md"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }}>
        {error && (
          <div style={{ marginBottom: "1rem" }}>
            <Notice tone="danger">{error}</Notice>
          </div>
        )}
        {sessions && sessions.length > 0 ? (
          <SelectField
            id="master-event-session-select"
            label="Academic Session"
            value={targetSessionId || sessions[0]?.academicSessionId || ""}
            onChange={(e) => setTargetSessionId(e.target.value)}
          >
            {sessions.map((s) => (
              <option key={s.academicSessionId} value={s.academicSessionId}>
                {s.sessionLabel} ({s.startDate} to {s.endDate})
              </option>
            ))}
          </SelectField>
        ) : (
          <Notice tone="warning">No academic sessions are available yet. Create an academic session first, then return to add a master calendar event.</Notice>
        )}
        <SelectField
          id="master-event-type-select"
          label="Event Type"
          value={masterEventType}
          onChange={(e) => setMasterEventType(e.target.value)}
        >
          <option value="SEMESTER_DATES">Semester Dates (SEMESTER_DATES)</option>
          <option value="UNIVERSITY_HOLIDAY">University Holiday (UNIVERSITY_HOLIDAY)</option>
          <option value="EXAM_DAY">Exam day (EXAM_DAY)</option>
        </SelectField>
        <div style={{ marginBottom: "1rem" }}>
          <TextField
            id="master-event-title-input"
            label="Title"
            required
            value={masterEventTitle}
            onChange={(e) => setMasterEventTitle(e.target.value)}
            placeholder="e.g. Odd Semester 2026 or Diwali Break"
          />
        </div>
        <div className="app-form-grid" style={{ marginBottom: "1rem" }}>
          <TextField
            id="master-event-start-date"
            label="Start Date"
            type="date"
            value={masterEventStart}
            onChange={(e) => setMasterEventStart(e.target.value)}
            required
          />
          <TextField
            id="master-event-end-date"
            label="End Date"
            type="date"
            value={masterEventEnd}
            onChange={(e) => setMasterEventEnd(e.target.value)}
            required
          />
        </div>
        <footer style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" busy={submitting} disabled={submitting || sessions.length === 0}>
            Add Master Event
          </Button>
        </footer>
      </form>
    </Modal>
  );
}

export default AddMasterEventModal;
