import { useEffect, useState, type FormEvent } from "react";
import { Button, Modal, Notice, Panel, SelectField, SkeletonText, StatusBadge, TextAreaField } from "../../../../design-system/components";
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
  const [statusLoading, setStatusLoading] = useState(true);
  const [statusLoadError, setStatusLoadError] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);

  useEffect(() => {
    if (!currentTenantId || !studentId) { setStatusLoading(false); return; }
    setStatusLoading(true);
    setStatusLoadError(null);
    academicService.getStudentStatus(currentTenantId, studentId)
      .then((data) => { setCurrentStatus(data.status); })
      .catch((err: unknown) => { setStatusLoadError(getApiErrorMessage(err)); })
      .finally(() => { setStatusLoading(false); });
  }, [currentTenantId, studentId]);
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
      <div className="app-panel__body">
        {!currentTenantId && <Notice tone="danger">A tenant identity is required to change student status.</Notice>}
        {feedback && <Notice tone={feedback.tone}>{feedback.message}</Notice>}
        <div className="app-panel__status">
          <span className="app-panel__status-label">Current Status:</span>
          {statusLoading && !currentStatus ? (
            <span role="status" aria-label="Loading student status"><SkeletonText height="1rem" width="7rem" /></span>
          ) : statusLoadError ? (
            <span className="app-panel__status-error">{statusLoadError}</span>
          ) : currentStatus ? (
            <StatusBadge status={currentStatus} label={formatStudentStatus(currentStatus)} />
          ) : (
            <span className="app-panel__status-label">—</span>
          )}
        </div>
      </div>

      <Modal
        isOpen={showModal && Boolean(currentTenantId)}
        onClose={() => setShowModal(false)}
        kicker="STUDENT ACADEMIC STATUS"
        title="Change Student Academic Status"
        size="sm"
      >
        <form className="app-form" onSubmit={handleSubmit}>
          <SelectField
            id="new-student-status"
            label="New Academic Status *"
            required
            value={newStatus}
            onChange={(e) => setNewStatus(e.target.value)}
          >
            <option value="ACTIVE">Active (normal standing)</option>
            <option value="LEAVE_OF_ABSENCE">Leave of absence (authorized break)</option>
            <option value="SUSPENDED">Suspended (disciplinary)</option>
            <option value="EXPELLED">Expelled (permanent exclusion)</option>
            <option value="WITHDRAWN">Withdrawn (voluntary discontinuation)</option>
            <option value="TRANSFERRED">Transferred (to another institution)</option>
            <option value="GRADUATED">Graduated (programme completed)</option>
          </SelectField>

          <TextAreaField
            id="status-reason"
            label="Statutory Justification / Reason *"
            required
            rows={3}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. Medical leave granted by Academic Council per UGC regulations."
          />

          <div className="app-form__actions">
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
