import { useCallback, useEffect, useState } from "react";
import { Button, Modal, Notice, SkeletonCard, SkeletonText, StatusBadge, TextField } from "../../../../design-system/components";
import { readErrorMessage } from "../common/types";
import {
  platformTenantService,
  type ProvisioningStatus,
  type TenantItem,
} from "../../api/platformTenantService";

export interface TenantSetupModalProps {
  isOpen: boolean;
  tenantId: string;
  tenantName: string;
  canRetry: boolean;
  onClose: () => void;
  /** Called after a successful retry; may carry a new one-time password for the first admin. */
  onRetried: (tenant: TenantItem) => void;
}

const STEP_STATUS_LABEL: Record<string, string> = {
  DONE: "Done",
  FAILED: "Failed",
  RUNNING: "Running",
  PENDING: "Not started",
  COMPENSATED: "Undone",
};

const ERROR_LABEL: Record<string, string> = {
  KEYCLOAK_UNAVAILABLE: "The sign-in service could not be reached.",
};

// E1-S2: why a tenant's setup failed, and a safe retry from the first unfinished step.
export function TenantSetupModal({ isOpen, tenantId, tenantName, canRetry, onClose, onRetried }: TenantSetupModalProps) {
  const [status, setStatus] = useState<ProvisioningStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [retrying, setRetrying] = useState(false);
  const [retryError, setRetryError] = useState("");
  const [retentionMonths, setRetentionMonths] = useState("");
  const [backupRetentionDays, setBackupRetentionDays] = useState("");

  const load = useCallback(async () => {
    try {
      const { data } = await platformTenantService.getProvisioning(tenantId);
      setStatus(data);
      setLoadError("");
    } catch (err) {
      setLoadError(readErrorMessage(err, "Could not load the setup steps."));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    if (isOpen) void load();
  }, [isOpen, load]);

  // Retention is only stored once its own step finishes, so a retry must ask for it again.
  const needsRetention = !!status?.steps.some((s) => s.stepCode === "P09_RETENTION" && s.status !== "DONE");
  const retentionValid =
    !needsRetention ||
    (Number(retentionMonths) >= 1 && Number(retentionMonths) <= 240 &&
      Number(backupRetentionDays) >= 7 && Number(backupRetentionDays) <= 3650);

  const retry = async () => {
    setRetrying(true);
    setRetryError("");
    try {
      const { data } = await platformTenantService.retryProvisioning(
        tenantId,
        needsRetention
          ? { retentionMonths: Number(retentionMonths), backupRetentionDays: Number(backupRetentionDays) }
          : undefined,
      );
      onRetried(data);
    } catch (err) {
      setRetryError(readErrorMessage(err, "Setup failed again. Check the steps below and try once more."));
      setLoading(true);
      void load();
    } finally {
      setRetrying(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="TENANT SETUP"
      title={`Setup of ${tenantName}`}
      titleId="tenant-setup-title"
      description="Setup runs these steps in order. A failed step is undone safely, and Retry continues from the first unfinished step."
      size="md"
      footer={
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)", width: "100%" }}>
          <Button variant="secondary" onClick={onClose}>Close</Button>
          {canRetry && status?.retryable && (
            <Button onClick={() => void retry()} busy={retrying} disabled={!retentionValid}>
              Retry setup
            </Button>
          )}
        </div>
      }
    >
      {loading ? (
        <div role="status" aria-busy="true" aria-label="Loading setup steps">
          <span className="sr-only">Loading setup steps...</span>
          <div className="app-stack--sm">
            {Array.from({ length: 4 }, (_, index) => (
              <SkeletonCard key={index}>
                <SkeletonText height="1rem" width="55%" />
                <SkeletonText height="0.875rem" width="80%" />
              </SkeletonCard>
            ))}
          </div>
        </div>
      ) : loadError ? (
        <Notice tone="danger">{loadError}</Notice>
      ) : status ? (
        <>
          {retryError && <Notice tone="danger">{retryError}</Notice>}
          {status.overallStatus === "DONE" && <Notice tone="success">Setup is complete.</Notice>}
          <ol style={{ margin: 0, paddingLeft: "var(--space-5)", display: "grid", gap: "var(--space-2)" }}>
            {status.steps.map((step) => (
              <li key={step.stepCode}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: "var(--space-3)", alignItems: "center" }}>
                  <span>{step.description}</span>
                  <StatusBadge value={STEP_STATUS_LABEL[step.status] ?? step.status} />
                </div>
                {step.lastErrorCode && (
                  <small className="app-cell-sub">
                    {ERROR_LABEL[step.lastErrorCode] ?? `Error: ${step.lastErrorCode}`} (attempts: {step.attempts})
                  </small>
                )}
              </li>
            ))}
          </ol>
          {canRetry && status.retryable && needsRetention && (
            <div className="app-form-grid" style={{ marginTop: "var(--space-4)" }}>
              <TextField id="setup-retention-months" label="Data retention (months)" type="number" min={1} max={240}
                required value={retentionMonths} onChange={(e) => setRetentionMonths(e.target.value)}
                hint="Needed again because the retention step did not finish. 1 to 240." />
              <TextField id="setup-backup-days" label="Backup retention (days)" type="number" min={7} max={3650}
                required value={backupRetentionDays} onChange={(e) => setBackupRetentionDays(e.target.value)}
                hint="7 to 3650." />
            </div>
          )}
          {!canRetry && status.retryable && (
            <p className="app-empty-inline">Only someone who can create tenants can retry setup.</p>
          )}
        </>
      ) : null}
    </Modal>
  );
}
