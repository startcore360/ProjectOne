import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";

export interface AllocateSubjectModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: {
    institutionId: string;
    courseOfferingId: string;
    sectionId?: string;
    staffAssignmentId: string;
  }) => Promise<void>;
  saving: boolean;
}

export function AllocateSubjectModal({
  isOpen,
  onClose,
  onSubmit,
  saving,
}: AllocateSubjectModalProps) {
  const [allocInstId, setAllocInstId] = useState("");
  const [allocCourseOfferingId, setAllocCourseOfferingId] = useState("");
  const [allocSectionId, setAllocSectionId] = useState("");
  const [allocTeacherId, setAllocTeacherId] = useState("");
  const [modalError, setModalError] = useState("");

  const handleClose = () => {
    setModalError("");
    onClose();
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setModalError("");
    try {
      await onSubmit({
        institutionId: allocInstId.trim(),
        courseOfferingId: allocCourseOfferingId.trim(),
        sectionId: allocSectionId.trim() || undefined,
        staffAssignmentId: allocTeacherId.trim(),
      });
      setAllocCourseOfferingId("");
      setAllocTeacherId("");
      handleClose();
    } catch (err: unknown) {
      setModalError(getApiErrorMessage(err));
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      kicker="ACADEMIC SETUP"
      title="Allocate Subject to Teacher"
      titleId="alloc-modal-title"
      size="sm"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }} noValidate>
        {modalError && (
          <div style={{ marginBottom: "var(--space-3)" }}>
            <Notice tone="danger">{modalError}</Notice>
          </div>
        )}
        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="alloc-inst-id"
            label="Institution UUID *"
            type="text"
            value={allocInstId}
            onChange={(e) => setAllocInstId(e.target.value)}
            required
            style={{ fontFamily: "monospace" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="alloc-course-offering-id"
            label="Course Offering UUID *"
            type="text"
            value={allocCourseOfferingId}
            onChange={(e) => setAllocCourseOfferingId(e.target.value)}
            required
            style={{ fontFamily: "monospace" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <TextField
            id="alloc-section-id"
            label="Section UUID (Optional)"
            type="text"
            value={allocSectionId}
            onChange={(e) => setAllocSectionId(e.target.value)}
            style={{ fontFamily: "monospace" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-4)" }}>
          <TextField
            id="alloc-teacher-id"
            label="Teacher Staff Assignment UUID *"
            type="text"
            value={allocTeacherId}
            onChange={(e) => setAllocTeacherId(e.target.value)}
            required
            style={{ fontFamily: "monospace" }}
          />
        </div>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button type="button" variant="secondary" onClick={handleClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? "Allocating..." : "Allocate Subject"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default AllocateSubjectModal;
