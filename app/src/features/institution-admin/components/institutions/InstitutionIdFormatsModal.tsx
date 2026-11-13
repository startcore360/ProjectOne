import { useEffect, useState, type FormEvent } from "react";
import { Button, Modal, Notice, TextField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import { institutionService, type InstitutionItem, type InstitutionIdFormats } from "../../api/institutionService";

export interface InstitutionIdFormatsModalProps {
  tenantId: string;
  institution: InstitutionItem | null;
  onClose: () => void;
  onSaved: (formats: InstitutionIdFormats) => void;
}

function preview(pattern: string, institutionCode: string): string {
  return pattern
    .replaceAll("{INST}", institutionCode || "INST")
    .replaceAll("{YYYY}", "2026")
    .replaceAll("{YY}", "26")
    .replace(/\{SEQ:(\d+)\}/g, (_match, width: string) => "1".padStart(Number(width), "0"));
}

export function InstitutionIdFormatsModal({ tenantId, institution, onClose, onSaved }: InstitutionIdFormatsModalProps) {
  const [studentFormat, setStudentFormat] = useState("");
  const [staffFormat, setStaffFormat] = useState("");
  const [formats, setFormats] = useState<InstitutionIdFormats | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!institution) return;
    let active = true;
    setLoading(true);
    setError("");
    void institutionService.getIdFormats(tenantId, institution.institutionId)
      .then((value) => {
        if (!active) return;
        setFormats(value);
        setStudentFormat(value.studentIdFormat ?? "");
        setStaffFormat(value.staffIdFormat ?? "");
      })
      .catch((cause: unknown) => {
        if (active) setError(getApiErrorMessage(cause) || "Could not load institution ID formats.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [tenantId, institution]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!institution) return;
    setSaving(true);
    setError("");
    try {
      const saved = await institutionService.setIdFormats(tenantId, institution.institutionId, {
        studentIdFormat: studentFormat.trim(),
        staffIdFormat: staffFormat.trim() || undefined,
      });
      setFormats(saved);
      onSaved(saved);
    } catch (cause: unknown) {
      setError(getApiErrorMessage(cause) || "Could not save institution ID formats.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal isOpen={Boolean(institution)} onClose={onClose} title="Institution ID formats" titleId="institution-id-formats-title" size="md">
      {institution && (
        <form onSubmit={submit}>
          <p className="platform-modal-note">{institution.institutionName} · {institution.institutionCode}</p>
          {error && <Notice tone="danger">{error}</Notice>}
          {loading ? <p role="status">Loading ID formats…</p> : (
            <>
              <TextField
                id="edit-student-id-format"
                label="Student ID format"
                required
                disabled={Boolean(formats?.studentFormatLocked)}
                value={studentFormat}
                onChange={(event) => setStudentFormat(event.target.value.toUpperCase())}
                hint={formats?.studentFormatLocked ? "Locked after the first student ID was generated." : "Use {YYYY}, {YY}, {INST}, and exactly one {SEQ:n} token (n = 3–10)."}
              />
              <p className="platform-modal-note" aria-live="polite">Preview only: {preview(studentFormat, institution.institutionCode)}</p>
              <TextField
                id="edit-staff-id-format"
                label="Staff ID format (optional)"
                disabled={Boolean(formats?.staffFormatLocked)}
                value={staffFormat}
                onChange={(event) => setStaffFormat(event.target.value.toUpperCase())}
                hint={formats?.staffFormatLocked ? "Locked after the first staff ID was generated." : "Leave blank when HR will enter staff codes manually."}
              />
              {staffFormat && <p className="platform-modal-note" aria-live="polite">Preview only: {preview(staffFormat, institution.institutionCode)}</p>}
            </>
          )}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)", marginTop: "var(--space-4)" }}>
            <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>Close</Button>
            <Button type="submit" busy={saving} disabled={loading || saving || Boolean(formats?.studentFormatLocked && formats?.staffFormatLocked)}>Save formats</Button>
          </div>
        </form>
      )}
    </Modal>
  );
}
