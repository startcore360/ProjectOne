import { useState, type FormEvent } from "react";
import { Button, Modal } from "../../../../design-system/components";

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

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    await onSubmit({
      institutionId: allocInstId.trim(),
      courseOfferingId: allocCourseOfferingId.trim(),
      sectionId: allocSectionId.trim() || undefined,
      staffAssignmentId: allocTeacherId.trim(),
    });
    setAllocCourseOfferingId("");
    setAllocTeacherId("");
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="SUBJECT_ALLOCATE • Action #36"
      title="Allocate Subject to Teacher"
      titleId="alloc-modal-title"
      size="sm"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }}>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <label htmlFor="alloc-inst-id" style={{ display: "block", fontSize: "0.875rem", fontWeight: 600 }}>
            Institution UUID *
          </label>
          <input
            id="alloc-inst-id"
            type="text"
            value={allocInstId}
            onChange={(e) => setAllocInstId(e.target.value)}
            required
            style={{ width: "100%", padding: "var(--space-2)", fontFamily: "monospace" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <label htmlFor="alloc-course-offering-id" style={{ display: "block", fontSize: "0.875rem", fontWeight: 600 }}>
            Course Offering UUID *
          </label>
          <input
            id="alloc-course-offering-id"
            type="text"
            value={allocCourseOfferingId}
            onChange={(e) => setAllocCourseOfferingId(e.target.value)}
            required
            style={{ width: "100%", padding: "var(--space-2)", fontFamily: "monospace" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-3)" }}>
          <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 600 }}>Section UUID</label>
          <input
            type="text"
            value={allocSectionId}
            onChange={(e) => setAllocSectionId(e.target.value)}
            style={{ width: "100%", padding: "var(--space-2)", fontFamily: "monospace" }}
          />
        </div>
        <div style={{ marginBottom: "var(--space-4)" }}>
          <label htmlFor="alloc-teacher-id" style={{ display: "block", fontSize: "0.875rem", fontWeight: 600 }}>
            Teacher Staff Assignment UUID *
          </label>
          <input
            id="alloc-teacher-id"
            type="text"
            value={allocTeacherId}
            onChange={(e) => setAllocTeacherId(e.target.value)}
            required
            style={{ width: "100%", padding: "var(--space-2)", fontFamily: "monospace" }}
          />
        </div>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? "Saving..." : "Allocate Subject"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default AllocateSubjectModal;
