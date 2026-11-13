import { useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";

export interface AddDepartmentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (deptCode: string, deptName: string) => Promise<void>;
  submitting: boolean;
  error: string | null;
}

export function AddDepartmentModal({
  isOpen,
  onClose,
  onSubmit,
  submitting,
  error,
}: AddDepartmentModalProps) {
  const [deptCode, setDeptCode] = useState("");
  const [deptName, setDeptName] = useState("");

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    await onSubmit(deptCode, deptName);
    setDeptCode("");
    setDeptName("");
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={() => {
        setDeptCode("");
        setDeptName("");
        onClose();
      }}
      title="Add Department"
      titleId="add-dept-title"
      description="Create an academic department for this college."
      size="sm"
    >
      <form onSubmit={handleSubmit} style={{ padding: "0.5rem 0" }}>
        {error && (
          <div style={{ marginBottom: "1rem" }}>
            <Notice tone="danger">{error}</Notice>
          </div>
        )}
        <div style={{ marginBottom: "1rem" }}>
          <TextField
            id="dept-code-input"
            label="Department Code"
            required
            value={deptCode}
            onChange={(e) => setDeptCode(e.target.value)}
            placeholder="e.g. CSE"
          />
        </div>
        <div style={{ marginBottom: "1rem" }}>
          <TextField
            id="dept-name-input"
            label="Department Name"
            required
            value={deptName}
            onChange={(e) => setDeptName(e.target.value)}
            placeholder="e.g. Computer Science and Engineering"
          />
        </div>
        <footer style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              setDeptCode("");
              setDeptName("");
              onClose();
            }}
            disabled={submitting}
          >
            Cancel
          </Button>
          <Button type="submit" variant="primary" busy={submitting}>
            Create Department
          </Button>
        </footer>
      </form>
    </Modal>
  );
}

export default AddDepartmentModal;
