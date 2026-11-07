import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";

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
      description="Set semester dates or university holidays [CALENDAR_MASTER_SET, Rule CL-02]."
      size="md"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }}>
        {error && (
          <div style={{ marginBottom: "1rem" }}>
            <Notice tone="danger">{error}</Notice>
          </div>
        )}
        <div className="platform-form-field" style={{ marginBottom: "1rem" }}>
          <label htmlFor="master-event-session-select" style={{ display: "block", fontWeight: 600, marginBottom: "0.25rem" }}>
            Academic Session
          </label>
          {sessions && sessions.length > 0 ? (
            <select
              id="master-event-session-select"
              value={targetSessionId || sessions[0]?.academicSessionId || ""}
              onChange={(e) => setTargetSessionId(e.target.value)}
              style={{ width: "100%", padding: "0.5rem", borderRadius: "4px", border: "1px solid var(--color-border, #cbd5e1)" }}
            >
              {sessions.map((s) => (
                <option key={s.academicSessionId} value={s.academicSessionId}>
                  {s.sessionLabel} ({s.startDate} to {s.endDate})
                </option>
              ))}
            </select>
          ) : (
            <TextField
              id="master-event-session-input"
              label="Academic Session ID"
              required
              value={targetSessionId}
              onChange={(e) => setTargetSessionId(e.target.value)}
              placeholder="Enter academic session UUID"
            />
          )}
        </div>
        <div className="platform-form-field" style={{ marginBottom: "1rem" }}>
          <label htmlFor="master-event-type-select" style={{ display: "block", fontWeight: 600, marginBottom: "0.25rem" }}>
            Event Type
          </label>
          <select
            id="master-event-type-select"
            value={masterEventType}
            onChange={(e) => setMasterEventType(e.target.value)}
            style={{ width: "100%", padding: "0.5rem", borderRadius: "4px", border: "1px solid var(--color-border, #cbd5e1)" }}
          >
            <option value="SEMESTER_DATES">Semester Dates (SEMESTER_DATES)</option>
            <option value="UNIVERSITY_HOLIDAY">University Holiday (UNIVERSITY_HOLIDAY)</option>
          </select>
        </div>
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
        <div className="platform-form-grid" style={{ marginBottom: "1rem" }}>
          <label className="platform-form-field">
            Start Date
            <input type="date" value={masterEventStart} onChange={(e) => setMasterEventStart(e.target.value)} required />
          </label>
          <label className="platform-form-field">
            End Date
            <input type="date" value={masterEventEnd} onChange={(e) => setMasterEventEnd(e.target.value)} required />
          </label>
        </div>
        <footer style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" busy={submitting}>
            Add Master Event
          </Button>
        </footer>
      </form>
    </Modal>
  );
}

export default AddMasterEventModal;
