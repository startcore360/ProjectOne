import { useState, type FormEvent } from "react";
import { Badge, Button, DataTable, Modal, Notice, TextField } from "../../../../design-system/components";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import {
  userManagementService,
  type ImportJobResponse,
  type ImportRowErrorResponse,
} from "../../api/userManagementService";

export interface BulkUploadModalProps {
  tenantId: string;
  isOpen: boolean;
  onClose: () => void;
  onImportComplete: () => void;
}

export function BulkUploadModal({
  tenantId,
  isOpen,
  onClose,
  onImportComplete,
}: BulkUploadModalProps) {
  const [csvContent, setCsvContent] = useState("");
  const [institutionId, setInstitutionId] = useState("");
  const [validating, setValidating] = useState(false);
  const [starting, setStarting] = useState(false);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [job, setJob] = useState<ImportJobResponse | null>(null);
  const [rowErrors, setRowErrors] = useState<ImportRowErrorResponse[]>([]);
  const [completedMessage, setCompletedMessage] = useState<string | null>(null);

  const handleDownloadTemplate = async () => {
    setDownloadingTemplate(true);
    setError(null);
    try {
      const { data } = await userManagementService.getImportTemplate(tenantId, "USERS");
      const blob = new Blob([data.csvTemplate], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", data.filename || "user_bulk_upload_template.csv");
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setDownloadingTemplate(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (event) => {
        setCsvContent((event.target?.result as string) || "");
      };
      reader.readAsText(file);
    }
  };

  const handlePreValidate = async (e: FormEvent) => {
    e.preventDefault();
    if (!csvContent.trim()) {
      setError("Please select a CSV file or paste CSV content.");
      return;
    }
    setValidating(true);
    setError(null);
    setJob(null);
    setRowErrors([]);
    setCompletedMessage(null);
    try {
      const { data } = await userManagementService.createUserImport(
        tenantId,
        csvContent,
        institutionId.trim() || undefined
      );
      setJob(data);
      if (data.errorRows > 0) {
        const errorsRes = await userManagementService.listImportErrors(tenantId, data.importJobId);
        setRowErrors(errorsRes.data);
      }
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setValidating(false);
    }
  };

  const handleStartImport = async (mode: "CLEAN_ONLY" | "GOOD_ROWS_ONLY") => {
    if (!job) return;
    setStarting(true);
    setError(null);
    try {
      const { data } = await userManagementService.startImport(tenantId, job.importJobId, mode);
      setJob(data);
      setCompletedMessage(`Import completed successfully under ${mode} mode!`);
      onImportComplete();
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setStarting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="ACCOUNT IMPORT"
      title="Bulk upload user accounts"
      titleId="bulk-upload-title"
      description="Import supported user accounts from CSV with pre-validation and row-level error tracking. This workflow does not create student profiles or enrolments. Student CSV admission is not available yet; use Create student profile for individual admissions."
      size="lg"
    >
      <div style={{ padding: "0.5rem 0" }}>
        {/* Step 1: Download template */}
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            padding: "0.75rem 1rem",
            background: "var(--color-bg-subtle, #f8fafc)",
            borderRadius: "6px",
            marginBottom: "1rem",
            border: "1px solid var(--color-border, #e2e8f0)",
          }}
        >
          <div>
            <strong>1. Need a template?</strong>
            <div style={{ fontSize: "0.8125rem", color: "var(--color-text-secondary, #64748b)" }}>
              Download a standard CSV template with the required headers and sample data.
            </div>
          </div>
          <Button
            type="button"
            variant="secondary"
            onClick={handleDownloadTemplate}
            busy={downloadingTemplate}
          >
            Download CSV Template
          </Button>
        </div>

        {/* Form */}
        <form onSubmit={handlePreValidate}>
          <div style={{ marginBottom: "1rem" }}>
            <label
              htmlFor="csv-file-input"
              style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.25rem" }}
            >
              2. Select CSV File
            </label>
            <input
              id="csv-file-input"
              type="file"
              accept=".csv,text/csv"
              onChange={handleFileChange}
              style={{ width: "100%", padding: "0.5rem", borderRadius: "4px", border: "1px solid var(--color-border, #cbd5e1)" }}
            />
          </div>

          <div style={{ marginBottom: "1rem" }}>
            <label
              htmlFor="csv-textarea"
              style={{ display: "block", fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.25rem" }}
            >
              Or paste CSV text directly:
            </label>
            <textarea
              id="csv-textarea"
              rows={4}
              value={csvContent}
              onChange={(e) => setCsvContent(e.target.value)}
              placeholder="email,fullName,userType,mobile,nationalIdLast4,roleCode&#10;john.doe@northbridge.edu.in,John Doe,STAFF,+919876543210,4821,OFFICE_STAFF"
              style={{
                width: "100%",
                padding: "0.5rem",
                fontFamily: "monospace",
                fontSize: "0.85rem",
                borderRadius: "4px",
                border: "1px solid var(--color-border, #cbd5e1)",
              }}
            />
          </div>

          <div style={{ marginBottom: "1rem" }}>
            <TextField
              id="bulk-institution-id"
              label="Institution ID (Optional for college/school scoped uploads)"
              value={institutionId}
              onChange={(e) => setInstitutionId(e.target.value)}
              placeholder="UUID of the institution, or leave empty for university scope"
            />
          </div>

          {error && (
            <div style={{ marginBottom: "1rem" }}>
              <Notice tone="danger">{error}</Notice>
            </div>
          )}

          {completedMessage && (
            <div style={{ marginBottom: "1rem" }}>
              <Notice tone="success">{completedMessage}</Notice>
            </div>
          )}

          {!job && (
            <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
              <Button type="button" variant="secondary" onClick={onClose} disabled={validating}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" busy={validating} disabled={!csvContent.trim()}>
                Pre-validate CSV
              </Button>
            </div>
          )}
        </form>

        {/* Pre-validation Report */}
        {job && (
          <div style={{ marginTop: "1.25rem", borderTop: "1px solid var(--color-border, #e2e8f0)", paddingTop: "1rem" }}>
            <h3 style={{ fontSize: "1rem", margin: "0 0 0.5rem" }}>
              Pre-validation results
            </h3>

            <div className="platform-import-summary">
              <div style={{ background: "var(--color-bg-subtle, #f8fafc)", padding: "0.75rem", borderRadius: "6px", textAlign: "center" }}>
                <div style={{ fontSize: "0.75rem", color: "var(--color-text-secondary, #64748b)" }}>Total Rows</div>
                <div style={{ fontSize: "1.25rem", fontWeight: "bold" }}>{job.totalRows}</div>
              </div>
              <div style={{ background: "#ecfdf5", padding: "0.75rem", borderRadius: "6px", textAlign: "center", color: "#065f46" }}>
                <div style={{ fontSize: "0.75rem" }}>Valid Rows</div>
                <div style={{ fontSize: "1.25rem", fontWeight: "bold" }}>{job.validRows}</div>
              </div>
              <div
                style={{
                  background: job.errorRows > 0 ? "#fef2f2" : "var(--color-bg-subtle, #f8fafc)",
                  padding: "0.75rem",
                  borderRadius: "6px",
                  textAlign: "center",
                  color: job.errorRows > 0 ? "#991b1b" : "inherit",
                }}
              >
                <div style={{ fontSize: "0.75rem" }}>Error Rows</div>
                <div style={{ fontSize: "1.25rem", fontWeight: "bold" }}>{job.errorRows}</div>
              </div>
            </div>

            {/* Error Rows Breakdown Table */}
            {rowErrors.length > 0 && (
              <div style={{ marginBottom: "1rem" }}>
                <h4 style={{ fontSize: "0.875rem", color: "#991b1b", margin: "0 0 0.5rem" }}>
                  Rows with errors can be corrected and imported later:
                </h4>
                <DataTable
                  headers={["Row #", "Field", "Error Code", "Message"]}
                  className="max-h-[180px] overflow-y-auto"
                >
                  {rowErrors.map((err) => (
                    <tr key={err.id}>
                      <td><code>{err.rowNumber}</code></td>
                      <td><strong>{err.fieldName || "—"}</strong></td>
                      <td><Badge tone="neutral">{err.errorCode}</Badge></td>
                      <td style={{ color: "var(--color-text-secondary, #64748b)" }}>{err.errorMessage}</td>
                    </tr>
                  ))}
                </DataTable>
              </div>
            )}

            {/* Statutory Notice */}
            <div
              style={{
                padding: "0.625rem 0.875rem",
                backgroundColor: "var(--color-surface-subtle, #f3f4f6)",
                borderRadius: "6px",
                fontSize: "0.8125rem",
                color: "var(--color-muted)",
                marginBottom: "1rem",
              }}
            >
              <strong>Statutory Notice (DPDP Act 2023 / UIDAI [R2-34]):</strong> Unmasked 12-digit Aadhaar numbers are rejected. Only masked 4-digit national IDs are accepted and persisted.
            </div>

            {/* Actions */}
            {job.status !== "COMPLETED" ? (
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem", flexWrap: "wrap" }}>
                <Button type="button" variant="secondary" onClick={onClose} disabled={starting}>
                  Cancel
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={job.errorRows > 0 || starting}
                  busy={starting}
                  onClick={() => handleStartImport("CLEAN_ONLY")}
                  title={job.errorRows > 0 ? "Fix the errors before importing a clean file" : "Import only clean file"}
                >
                  Import clean only {job.errorRows > 0 ? "(Disabled: errors present)" : ""}
                </Button>
                <Button
                  type="button"
                  variant="primary"
                  disabled={job.validRows === 0 || starting}
                  busy={starting}
                  onClick={() => handleStartImport("GOOD_ROWS_ONLY")}
                >
                  Import {job.validRows} good {job.validRows === 1 ? "row" : "rows"}
                </Button>
              </div>
            ) : (
              <div style={{ display: "flex", justifyContent: "flex-end" }}>
                <Button type="button" variant="primary" onClick={onClose}>
                  Done
                </Button>
              </div>
            )}
          </div>
        )}
      </div>
    </Modal>
  );
}

export default BulkUploadModal;
