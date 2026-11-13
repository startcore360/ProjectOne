import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, Panel, StatusBadge } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import { academicService } from "../../../institution-admin/api/academicService";

function formatStudentStatus(value: string): string {
  const labels: Record<string, string> = {
    ACTIVE: "Active",
    LEAVE_OF_ABSENCE: "Leave of absence",
    SUSPENDED: "Suspended",
    EXPELLED: "Expelled",
    WITHDRAWN: "Withdrawn",
    TRANSFERRED: "Transferred",
    GRADUATED: "Graduated",
  };
  return labels[value] ?? value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export interface StudentStatusPanelProps {
  tenantId?: string;
  studentId: string;
}

export function StudentStatusPanel({
  tenantId,
  studentId,
}: StudentStatusPanelProps) {
  const currentTenantId = tenantId ?? "";
  const [currentStatus, setCurrentStatus] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [newStatus, setNewStatus] = useState("LEAVE_OF_ABSENCE");
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; message: string } | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!currentTenantId) return;
    setSaving(true);
    setFeedback(null);
    try {
      const res = await academicService.changeStudentStatus(currentTenantId, studentId, {
        status: newStatus,
        reason: reason.trim(),
      });
      setCurrentStatus(res.status);
      setFeedback({ tone: "success", message: `Student status successfully changed to ${formatStudentStatus(res.status)}.` });
      setShowModal(false);
      setReason("");
    } catch (err: unknown) {
      setFeedback({ tone: "danger", message: getApiErrorMessage(err) });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Panel
      title="Student Academic Status"
      description="Manage a student's academic standing, including leave, suspension, transfer and graduation. A reason is required for each change."
      action={
        <Button onClick={() => setShowModal(true)} disabled={!currentTenantId}>
          Change Student Status
        </Button>
      }
    >
      <div style={{ padding: "0 var(--space-5) var(--space-5)" }}>
        {!currentTenantId && <Notice tone="danger">A tenant identity is required to change student status.</Notice>}
        {feedback && (
          <div style={{ marginBottom: "var(--space-3)" }}>
            <Notice tone={feedback.tone}>{feedback.message}</Notice>
          </div>
        )}
        <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)" }}>
          <span style={{ fontSize: "0.875rem", color: "var(--color-text-muted, #666)" }}>Current Status:</span>
          {currentStatus ? <StatusBadge status={currentStatus} /> : <span style={{ fontSize: "0.875rem", color: "var(--color-text-muted)" }}>Not available from the current student-status endpoint</span>}
        </div>
      </div>

      <Modal
        isOpen={showModal && Boolean(currentTenantId)}
        onClose={() => setShowModal(false)}
        kicker="STUDENT ACADEMIC STATUS"
        title="Change Student Academic Status"
        size="sm"
      >
        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: "var(--space-3)" }}>
            <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "4px" }}>
              New Academic Status *
            </label>
            <select
              value={newStatus}
              onChange={(e) => setNewStatus(e.target.value)}
              style={{
                width: "100%",
                padding: "8px",
                border: "1px solid var(--color-border, #ccc)",
                borderRadius: "var(--radius-md, 4px)",
              }}
            >
              <option value="ACTIVE">Active (normal standing)</option>
              <option value="LEAVE_OF_ABSENCE">Leave of absence (authorized break)</option>
              <option value="SUSPENDED">Suspended (disciplinary)</option>
              <option value="EXPELLED">Expelled (permanent exclusion)</option>
              <option value="WITHDRAWN">Withdrawn (voluntary discontinuation)</option>
              <option value="TRANSFERRED">Transferred (to another institution)</option>
              <option value="GRADUATED">Graduated (programme completed)</option>
            </select>
          </div>

          <div style={{ marginBottom: "var(--space-4)" }}>
            <label
              htmlFor="status-reason"
              style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "4px" }}
            >
              Statutory Justification / Reason *
            </label>
            <textarea
              id="status-reason"
              required
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Medical leave granted by Academic Council per UGC regulations."
              style={{
                width: "100%",
                padding: "8px",
                border: "1px solid var(--color-border, #ccc)",
                borderRadius: "var(--radius-md, 4px)",
              }}
            />
          </div>

          <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
            <Button variant="secondary" onClick={() => setShowModal(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" busy={saving}>
              Update Status
            </Button>
          </div>
        </form>
      </Modal>
    </Panel>
  );
}

export default StudentStatusPanel;
