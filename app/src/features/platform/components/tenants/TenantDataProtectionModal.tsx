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
    dataClassCode: "STRUCTURE",
    requestedMonths: 12,
    reason: "",
  });
  const [submittingRetention, setSubmittingRetention] = useState(false);

  // Deletion State
  const [confirmName, setConfirmName] = useState("");
  const [deleting, setDeleting] = useState(false);

  const tenantId = tenant?.id;

  useEffect(() => {
    if (!isOpen || !tenantId) return;
    setNotice(null);
    setConfirmName("");

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
        message: `Export request created (ID: ${res.data.exportRequestId}). Status: ${res.data.status}.`,
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
      setRetentionDraft({ dataClassCode: "STRUCTURE", requestedMonths: 12, reason: "" });
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
      const response = await platformTenantService.requestTenantDeletion(tenantId, "PRODUCT_OWNER");
      setNotice({
        tone: "success",
        message: `Deletion request ${response.data.deletionRequestId} created. Status: ${response.data.status}. Next: Product Owner confirmation, then Data Protection Officer decision.`,
      });
      setConfirmName("");
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
        <div style={{ display: "flex", gap: "var(--space-2)", borderBottom: "1px solid var(--color-border-divider)" }}>
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
              <p style={{ margin: 0, fontSize: "var(--fs-body-sm)", color: "var(--color-text-muted)" }}>
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
                headers={["Export ID", "Requested By", "Status", "Requested At", "File Ref", "Download Expires"]}
                empty={exports.length === 0}
              >
                {exports.map((exp) => (
                  <tr key={exp.exportRequestId}>
                    <td>
                      <code>{exp.exportRequestId.slice(0, 8)}...</code>
                    </td>
                    <td>{exp.requestedByKind}</td>
                    <td>
                      <StatusBadge value={exp.status} status={exp.status} />
                    </td>
                    <td>{exp.createdAt ? new Date(exp.createdAt).toLocaleString() : "—"}</td>
                    <td>{exp.fileRef ?? "Pending"}</td>
                    <td>{exp.downloadExpiresAt ? new Date(exp.downloadExpiresAt).toLocaleString() : "—"}</td>
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
              <p style={{ margin: 0, fontSize: "var(--fs-body-sm)", color: "var(--color-text-muted)" }}>
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
                  background: "var(--color-bg-subtle)",
                  padding: "var(--space-3)",
                  borderRadius: "var(--radius-md)",
                  border: "1px solid var(--color-border-divider)",
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
                      <option value="STRUCTURE">Structure</option>
                      <option value="PERSON">Person</option>
                      <option value="ACADEMIC_RECORD">Academic Record</option>
                      <option value="FINANCIAL">Financial</option>
                      <option value="COMMUNICATION">Communication</option>
                      <option value="STUDENT_NOTE">Student Note</option>
                      <option value="GOVERNANCE">Governance</option>
                      <option value="APPROVAL">Approval</option>
                      <option value="AUDIT">Audit</option>
                      <option value="BACKUP">Backup</option>
                      <option value="OPERATIONAL">Operational</option>
                      <option value="BY_OWNER">By Owner</option>
                      <option value="TENANT_LIFETIME">Tenant Lifetime</option>
                      <option value="REFERENCE">Reference</option>
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
                headers={["Data Class", "Retention (Months)", "Effective From", "Status"]}
                empty={retentionPolicies.length === 0}
              >
                {retentionPolicies.map((rp) => (
                  <tr key={rp.tenantRetentionPolicyId}>
                    <td>
                      <strong>{rp.dataClassCode}</strong>
                    </td>
                    <td>{rp.retentionMonths} months</td>
                    <td>{rp.effectiveFrom ? new Date(rp.effectiveFrom).toLocaleDateString() : "—"}</td>
                    <td>
                      <StatusBadge value={rp.status} status={rp.status} />
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
              <strong>Deletion request only.</strong> This step does not delete or purge tenant data.
            </Notice>
            <Notice tone="info">
              Workflow: Requested → Product Owner confirmation → Data Protection Officer decision.
            </Notice>

            <div>
              <label style={{ display: "block", fontSize: "var(--fs-caption)", marginBottom: "4px" }}>
                Type <strong>{tenant.name}</strong> to request deletion
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

            <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)" }}>
              <Button variant="danger" type="submit" disabled={deleting || confirmName !== tenant.name}>
                {deleting ? "Submitting request..." : "Request deletion"}
              </Button>
            </div>
          </form>
        )}
      </div>
    </Modal>
  );
}
