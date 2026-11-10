import { useEffect, useState, type FormEvent } from "react";
import { Modal, Button, DataTable, Notice, StatusBadge } from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import {
  platformTenantService,
  type ExportRequestDto,
  type RetentionPolicyDto,
} from "../../api/platformTenantService";
import type { Tenant } from "../common/types";

interface TenantDataProtectionModalProps {
  isOpen: boolean;
  onClose: () => void;
  tenant: Tenant | null;
  onTenantDeleted?: (tenantId: string) => void;
}

export function TenantDataProtectionModal({
  isOpen,
  onClose,
  tenant,
  onTenantDeleted,
}: TenantDataProtectionModalProps) {
  const [activeTab, setActiveTab] = useState<"export" | "retention" | "deletion">("export");
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState<{ tone: "success" | "danger" | "info"; message: string } | null>(null);

  // Export State
  const [exports, setExports] = useState<ExportRequestDto[]>([]);
  const [exporting, setExporting] = useState(false);

  // Retention State
  const [retentionPolicies, setRetentionPolicies] = useState<RetentionPolicyDto[]>([]);
  const [showRetentionForm, setShowRetentionForm] = useState(false);
  const [retentionDraft, setRetentionDraft] = useState({
    dataClassCode: "STUDENT_RECORDS",
    requestedMonths: 12,
    reason: "",
  });
  const [submittingRetention, setSubmittingRetention] = useState(false);

  // Deletion State
  const [confirmName, setConfirmName] = useState("");
  const [deletionReason, setDeletionReason] = useState("");
  const [deleting, setDeleting] = useState(false);

  const tenantId = tenant?.id;

  useEffect(() => {
    if (!isOpen || !tenantId) return;
    setNotice(null);
    setConfirmName("");
    setDeletionReason("");

    if (activeTab === "export") {
      setLoading(true);
      platformTenantService
        .listExports(tenantId)
        .then((res) => setExports(res.data || []))
        .catch(() => setNotice({ tone: "danger", message: "Failed to load tenant export history." }))
        .finally(() => setLoading(false));
    } else if (activeTab === "retention") {
      setLoading(true);
      platformTenantService
        .getRetentionPolicies(tenantId)
        .then((res) => setRetentionPolicies(res.data || []))
        .catch(() => setNotice({ tone: "danger", message: "Failed to load retention policies." }))
        .finally(() => setLoading(false));
    }
  }, [isOpen, tenantId, activeTab]);

  const handleTriggerExport = async () => {
    if (!tenantId) return;
    setExporting(true);
    setNotice(null);
    try {
      const res = await platformTenantService.exportTenant(tenantId);
      setNotice({
        tone: "success",
        message: `Export request created (ID: ${res.data.id}). Status: ${res.data.status}.`,
      });
      // Refresh export history
      const listRes = await platformTenantService.listExports(tenantId);
      setExports(listRes.data || []);
    } catch {
      setNotice({ tone: "danger", message: "Failed to trigger export request." });
    } finally {
      setExporting(false);
    }
  };

  const handleSubmitRetentionChange = async (e: FormEvent) => {
    e.preventDefault();
    if (!tenantId) return;
    setSubmittingRetention(true);
    setNotice(null);
    try {
      await platformTenantService.requestRetentionChange(tenantId, retentionDraft);
      setNotice({
        tone: "success",
        message: `Retention change requested for ${retentionDraft.dataClassCode} (${retentionDraft.requestedMonths} months). Pending DPO approval.`,
      });
      setShowRetentionForm(false);
      setRetentionDraft({ dataClassCode: "STUDENT_RECORDS", requestedMonths: 12, reason: "" });
      const listRes = await platformTenantService.getRetentionPolicies(tenantId);
      setRetentionPolicies(listRes.data || []);
    } catch {
      setNotice({ tone: "danger", message: "Failed to request retention policy change." });
    } finally {
      setSubmittingRetention(false);
    }
  };

  const handleConfirmDeletion = async (e: FormEvent) => {
    e.preventDefault();
    if (!tenant || !tenantId) return;
    if (confirmName !== tenant.name) {
      setNotice({ tone: "danger", message: `Please type exact institution name "${tenant.name}" to confirm.` });
      return;
    }
    setDeleting(true);
    setNotice(null);
    try {
      await platformTenantService.deleteTenant(tenantId, deletionReason);
      setNotice({
        tone: "success",
        message: `Tenant ${tenant.name} has been queued for deletion / purged successfully.`,
      });
      if (onTenantDeleted) {
        onTenantDeleted(tenantId);
      }
      setTimeout(() => {
        onClose();
      }, 1500);
    } catch {
      setNotice({ tone: "danger", message: "Failed to delete tenant. Please ensure DPO approval or try again." });
    } finally {
      setDeleting(false);
    }
  };

  if (!tenant) return null;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Data Protection & Retention: ${tenant.name}`}
      kicker="Compliance & Governance"
      size="lg"
      footer={
        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        </div>
      }
    >
      <div style={{ display: "grid", gap: "var(--space-4)" }}>
        {notice && <Notice tone={notice.tone}>{notice.message}</Notice>}

        {/* Tab Navigation */}
        <div style={{ display: "flex", gap: "var(--space-2)", borderBottom: "1px solid var(--border-subtle)" }}>
          <Button
            variant={activeTab === "export" ? "primary" : "secondary"}
            size="sm"
            onClick={() => {
              setActiveTab("export");
              setNotice(null);
            }}
          >
            Tenant Export
          </Button>
          <Button
            variant={activeTab === "retention" ? "primary" : "secondary"}
            size="sm"
            onClick={() => {
              setActiveTab("retention");
              setNotice(null);
            }}
          >
            Retention Policy
          </Button>
          <Button
            variant={activeTab === "deletion" ? "danger" : "secondary"}
            size="sm"
            onClick={() => {
              setActiveTab("deletion");
              setNotice(null);
            }}
          >
            Tenant Deletion (GDPR/DPDP)
          </Button>
        </div>

        {/* Export Tab */}
        {activeTab === "export" && (
          <div style={{ display: "grid", gap: "var(--space-3)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <p style={{ margin: 0, fontSize: "var(--fs-body-sm)", color: "var(--text-secondary)" }}>
                Export tenant data bundle adhering to standard legal export specifications.
              </p>
              <Button size="sm" onClick={handleTriggerExport} disabled={exporting}>
                {exporting ? "Requesting export..." : "Trigger New Export"}
              </Button>
            </div>

            {loading ? (
              <Spinner label="Loading export records" />
            ) : (
              <DataTable
                headers={["Export ID", "Status", "Requested At", "Completed At", "Download URL"]}
                empty={exports.length === 0}
              >
                {exports.map((exp) => (
                  <tr key={exp.id}>
                    <td>
                      <code>{exp.id.slice(0, 8)}...</code>
                    </td>
                    <td>
                      <StatusBadge value={exp.status === "COMPLETED" ? "Success" : exp.status === "FAILED" ? "Denied" : "Trial"} status={exp.status} />
                    </td>
                    <td>{exp.requestedAt ? new Date(exp.requestedAt).toLocaleString() : "-"}</td>
                    <td>{exp.completedAt ? new Date(exp.completedAt).toLocaleString() : "Processing"}</td>
                    <td>
                      {exp.downloadUrl && (exp.downloadUrl.startsWith("https://") || exp.downloadUrl.startsWith("/")) ? (
                        <a href={exp.downloadUrl} target="_blank" rel="noreferrer" style={{ color: "var(--color-primary-600, #2563eb)" }}>
                          Download Zip
                        </a>
                      ) : (
                        <span style={{ color: "var(--color-text-muted)" }}>-</span>
                      )}
                    </td>
                  </tr>
                ))}
              </DataTable>
            )}
          </div>
        )}

        {/* Retention Tab */}
        {activeTab === "retention" && (
          <div style={{ display: "grid", gap: "var(--space-3)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <p style={{ margin: 0, fontSize: "var(--fs-body-sm)", color: "var(--text-secondary)" }}>
                Data retention periods for this tenant. Alterations require Data Protection Officer verification.
              </p>
              <Button size="sm" onClick={() => setShowRetentionForm(!showRetentionForm)}>
                {showRetentionForm ? "Cancel" : "Request Policy Change"}
              </Button>
            </div>

            {showRetentionForm && (
              <form
                onSubmit={handleSubmitRetentionChange}
                style={{
                  background: "var(--surface-muted, #f8fafc)",
                  padding: "var(--space-3)",
                  borderRadius: "var(--radius-md)",
                  border: "1px solid var(--border-subtle)",
                  display: "grid",
                  gap: "var(--space-3)",
                }}
              >
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--space-3)" }}>
                  <div>
                    <label style={{ display: "block", fontSize: "var(--fs-caption)", marginBottom: "4px" }}>
                      Data Category
                    </label>
                    <select
                      className="form-control"
                      value={retentionDraft.dataClassCode}
                      onChange={(e) => setRetentionDraft({ ...retentionDraft, dataClassCode: e.target.value })}
                    >
                      <option value="STUDENT_RECORDS">Student Records</option>
                      <option value="ATTENDANCE_LOGS">Attendance Logs</option>
                      <option value="FINANCIAL_LEDGERS">Financial Ledgers</option>
                      <option value="AUDIT_TRAILS">Audit Trails</option>
                    </select>
                  </div>
                  <div>
                    <label style={{ display: "block", fontSize: "var(--fs-caption)", marginBottom: "4px" }}>
                      Requested Retention (Months)
                    </label>
                    <input
                      type="number"
                      className="form-control"
                      min={1}
                      max={120}
                      value={retentionDraft.requestedMonths}
                      onChange={(e) =>
                        setRetentionDraft({ ...retentionDraft, requestedMonths: parseInt(e.target.value, 10) || 12 })
                      }
                      required
                    />
                  </div>
                </div>
                <div>
                  <label style={{ display: "block", fontSize: "var(--fs-caption)", marginBottom: "4px" }}>
                    Reason for Change
                  </label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="e.g. Legal compliance with State Education Board mandate"
                    value={retentionDraft.reason}
                    onChange={(e) => setRetentionDraft({ ...retentionDraft, reason: e.target.value })}
                    required
                  />
                </div>
                <div style={{ display: "flex", justifyContent: "flex-end" }}>
                  <Button size="sm" type="submit" disabled={submittingRetention}>
                    {submittingRetention ? "Submitting..." : "Submit Change Request"}
                  </Button>
                </div>
              </form>
            )}

            {loading ? (
              <Spinner label="Loading retention policies" />
            ) : (
              <DataTable
                headers={["Data Category", "Retention Period (Days)", "Last Updated", "Status"]}
                empty={retentionPolicies.length === 0}
              >
                {retentionPolicies.map((rp) => (
                  <tr key={rp.id}>
                    <td>
                      <strong>{rp.dataCategory}</strong>
                    </td>
                    <td>{rp.retentionPeriodDays} days</td>
                    <td>{rp.updatedAt ? new Date(rp.updatedAt).toLocaleDateString() : "Default"}</td>
                    <td>
                      <StatusBadge value="Active" />
                    </td>
                  </tr>
                ))}
              </DataTable>
            )}
          </div>
        )}

        {/* Deletion Tab */}
        {activeTab === "deletion" && (
          <form onSubmit={handleConfirmDeletion} style={{ display: "grid", gap: "var(--space-3)" }}>
            <Notice tone="danger">
              <strong>Caution: Permanent Action.</strong> Tenant deletion purges tenant schemas, disables identity links, and initiates cold-storage archival under GDPR/DPDP rules.
            </Notice>

            <div>
              <label style={{ display: "block", fontSize: "var(--fs-caption)", marginBottom: "4px" }}>
                Type <strong>{tenant.name}</strong> to confirm deletion
              </label>
              <input
                type="text"
                className="form-control"
                value={confirmName}
                onChange={(e) => setConfirmName(e.target.value)}
                placeholder={tenant.name}
                required
              />
            </div>

            <div>
              <label style={{ display: "block", fontSize: "var(--fs-caption)", marginBottom: "4px" }}>
                Justification / Regulatory Reason
              </label>
              <textarea
                className="form-control"
                rows={3}
                value={deletionReason}
                onChange={(e) => setDeletionReason(e.target.value)}
                placeholder="e.g. Contract expiration, institution closure, right to be forgotten"
                required
              />
            </div>

            <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
              <Button variant="danger" type="submit" disabled={deleting || confirmName !== tenant.name}>
                {deleting ? "Deleting tenant..." : "Delete Tenant Permanently"}
              </Button>
            </div>
          </form>
        )}
      </div>
    </Modal>
  );
}
