import { useCallback, useEffect, useState, type FormEvent } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import {
  Button,
  CheckboxField,
  DataTable,
  MetricCard,
  Modal,
  Notice,
  Panel,
  SelectField,
  SkeletonCard,
  SkeletonText,
  StatusBadge,
  TextField,
} from "../../../../design-system/components";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import {
  platformOpsService,
  type SecurityPolicyItem,
  type SecurityIncidentItem,
} from "../../api/platformOpsService";
import { usePlatformAccess } from "../../platformAccess";

export function SecurityPolicyView() {
  const { can } = usePlatformAccess();
  const canEditPolicy = can("SECURITY_POLICY_SET");

  const [policy, setPolicy] = useState<SecurityPolicyItem | null>(null);
  const [incidents, setIncidents] = useState<SecurityIncidentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [savingPolicy, setSavingPolicy] = useState(false);
  const [realmPreview, setRealmPreview] = useState<import("../../api/platformOpsService").RealmSyncPreviewItem | null>(null);
  const [realmSyncRun, setRealmSyncRun] = useState<import("../../api/platformOpsService").RealmSyncRunItem | null>(null);
  const [previewingRealms, setPreviewingRealms] = useState(false);
  const [applyingRealms, setApplyingRealms] = useState(false);
  const [retryingRealms, setRetryingRealms] = useState(false);
  const [message, setMessage] = useState("");
  const [policyError, setPolicyError] = useState("");
  const [incidentError, setIncidentError] = useState("");
  const [actionError, setActionError] = useState("");

  // Edit policy state
  const [minPasswordLength, setMinPasswordLength] = useState(12);
  const [requireSpecialChar, setRequireSpecialChar] = useState(true);
  const [requireDigit, setRequireDigit] = useState(true);
  const [maxFailedLogins, setMaxFailedLogins] = useState(5);
  const [lockoutMinutes, setLockoutMinutes] = useState(15);
  const [idleSessionTimeoutMinutes, setIdleSessionTimeoutMinutes] = useState(30);
  const [mfaRequiredForPrivileged, setMfaRequiredForPrivileged] = useState(true);
  const [defaultBackupRetentionDays, setDefaultBackupRetentionDays] = useState(90);

  // New incident modal
  const [showIncidentModal, setShowIncidentModal] = useState(false);
  const [incidentTitle, setIncidentTitle] = useState("");
  const [incidentSeverity, setIncidentSeverity] = useState("MEDIUM");
  const [incidentExternalNotify, setIncidentExternalNotify] = useState(false);
  const [submittingIncident, setSubmittingIncident] = useState(false);
  const [incidentModalError, setIncidentModalError] = useState("");

  const loadPolicy = useCallback(async () => {
    setPolicyError("");
    try {
      const pol = await platformOpsService.getSecurityPolicy();
      setPolicy(pol);
      setMinPasswordLength(pol.minPasswordLength);
      setRequireSpecialChar(pol.requireSpecialChar);
      setRequireDigit(pol.requireDigit);
      setMaxFailedLogins(pol.maxFailedLogins);
      setLockoutMinutes(pol.lockoutMinutes);
      setIdleSessionTimeoutMinutes(pol.idleSessionTimeoutMinutes);
      setMfaRequiredForPrivileged(pol.mfaRequiredForPrivileged);
      setDefaultBackupRetentionDays(pol.defaultBackupRetentionDays);
    } catch (err: unknown) {
      setPolicy(null);
      setPolicyError(err instanceof Error ? err.message : "Failed to load security policy configuration");
    }
  }, []);

  const loadIncidents = useCallback(async () => {
    setIncidentError("");
    try {
      const incs = await platformOpsService.listSecurityIncidents();
      setIncidents(incs);
    } catch (err: unknown) {
      setIncidentError(err instanceof Error ? err.message : "Failed to load incident history");
    }
  }, []);

  const loadData = useCallback(async () => {
    setLoading(true);
    setActionError("");
    await Promise.all([loadPolicy(), loadIncidents()]);
    setLoading(false);
  }, [loadPolicy, loadIncidents]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSavePolicy = async (e: FormEvent) => {
    e.preventDefault();
    setSavingPolicy(true);
    setActionError("");
    setMessage("");
    try {
      const updated = await platformOpsService.updateSecurityPolicy({
        minPasswordLength,
        requireSpecialChar,
        requireDigit,
        maxFailedLogins,
        lockoutMinutes,
        idleSessionTimeoutMinutes,
        mfaRequiredForPrivileged,
        defaultBackupRetentionDays,
      });
      setPolicy(updated);
      setMessage("Platform security policy updated successfully.");
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to save security policy");
    } finally {
      setSavingPolicy(false);
    }
  };

  const handlePreviewRealmSync = async () => {
    setPreviewingRealms(true);
    setActionError("");
    setRealmSyncRun(null);
    try {
      setRealmPreview(await platformOpsService.previewSecurityPolicyRealmSync());
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to preview realm security sync");
    } finally {
      setPreviewingRealms(false);
    }
  };

  const handleApplyRealmSync = async () => {
    if (!realmPreview) return;
    setApplyingRealms(true);
    setActionError("");
    try {
      setRealmSyncRun(await platformOpsService.applySecurityPolicyRealmSync(realmPreview.previewId));
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to apply realm security policy");
    } finally {
      setApplyingRealms(false);
    }
  };

  const handleRetryFailedRealms = async () => {
    if (!realmSyncRun) return;
    setRetryingRealms(true);
    setActionError("");
    try {
      setRealmSyncRun(await platformOpsService.retryFailedSecurityPolicyRealms(realmSyncRun.runId));
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to retry failed realms");
    } finally {
      setRetryingRealms(false);
    }
  };

  const failedRealmCount = realmSyncRun?.results.filter((result) => !result.applied).length ?? 0;

  const handleCreateIncident = async (e: FormEvent) => {
    e.preventDefault();
    setSubmittingIncident(true);
    setIncidentModalError("");
    try {
      await platformOpsService.createSecurityIncident({
        title: incidentTitle.trim(),
        severity: incidentSeverity,
        externalNotificationRequired: incidentExternalNotify,
      });
      setMessage("Security incident logged.");
      setShowIncidentModal(false);
      setIncidentTitle("");
      setIncidentSeverity("MEDIUM");
      setIncidentExternalNotify(false);
      void loadIncidents();
    } catch (err: unknown) {
      setIncidentModalError(err instanceof Error ? err.message : "Failed to log security incident");
    } finally {
      setSubmittingIncident(false);
    }
  };

  const handleUpdateIncidentStatus = async (id: string, newStatus: string) => {
    setActionError("");
    try {
      await platformOpsService.updateSecurityIncident(id, {
        status: newStatus,
        closureNote: newStatus === "CLOSED" ? "Resolved by Data Protection / Security Officer" : undefined,
      });
      setMessage(`Incident marked as ${newStatus}.`);
      void loadIncidents();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to update incident status");
    }
  };

  return (
    <div className="app-page">
      <PageHeader
        title="Security Policy & Incidents"
        description="Global platform authentication controls, password complexity rules, session timeouts, and security incident tracking."
        actions={
          canEditPolicy && (
            <Button onClick={() => {
              setIncidentModalError("");
              setShowIncidentModal(true);
            }}>
              Log Security Incident
            </Button>
          )
        }
      />

      {message && <Notice tone="success">{message}</Notice>}
      {actionError && <Notice tone="danger">{actionError}</Notice>}

      <div className="kpi-grid">
        <MetricCard
          label="MFA Enforcement"
          value={policy ? (policy.mfaRequiredForPrivileged ? "Active" : "Optional") : "—"}
          note="Privileged staff accounts"
          mark="shield"
        />
        <MetricCard
          label="Lockout Threshold"
          value={policy ? `${policy.maxFailedLogins} attempts` : "—"}
          note={policy ? `Lockout for ${policy.lockoutMinutes} mins` : "Pending policy load"}
          mark="lock"
        />
        <MetricCard
          label="Session Timeout"
          value={policy ? `${policy.idleSessionTimeoutMinutes} mins` : "—"}
          note="Idle automatic sign-out"
          mark="clock"
        />
        <MetricCard
          label="Active Incidents"
          value={incidents.filter((i) => i.status !== "CLOSED").length}
          note="Detected or contained"
          mark="alert"
        />
      </div>

      {canEditPolicy && (
        <Panel
          title="Realm Security Policy Re-application"
          description="Preview target realms and policy values before making changes. Credentials and client secrets are never displayed."
        >
          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap", marginBottom: "14px" }}>
            <Button variant="secondary" onClick={() => void handlePreviewRealmSync()} disabled={previewingRealms || applyingRealms || retryingRealms}>
              {previewingRealms ? "Loading Preview..." : "Preview Realm Sync"}
            </Button>
            {realmPreview && (
              <Button onClick={() => void handleApplyRealmSync()} disabled={applyingRealms || retryingRealms || realmPreview.realms.length === 0}>
                {applyingRealms ? "Applying..." : "Apply to Previewed Realms"}
              </Button>
            )}
            {failedRealmCount > 0 && realmSyncRun && (
              <Button variant="secondary" onClick={() => void handleRetryFailedRealms()} disabled={retryingRealms || applyingRealms}>
                {retryingRealms ? "Retrying Failed..." : "Retry Failed Realms (" + failedRealmCount + ")"}
              </Button>
            )}
          </div>
          {realmPreview && (
            <div style={{ marginBottom: "16px" }}>
              <p><strong>Read-only preview</strong> · {realmPreview.realms.length} realm(s) targeted</p>
              <p>
                Password length: {realmPreview.settings.minPasswordLength}; digits: {realmPreview.settings.requireDigit ? "required" : "not required"}; special character: {realmPreview.settings.requireSpecialChar ? "required" : "not required"}; lockout: {realmPreview.settings.maxFailedLogins} failures / {realmPreview.settings.lockoutMinutes} min; idle timeout: {realmPreview.settings.idleSessionTimeoutMinutes} min.
              </p>
              <DataTable headers={["Target realm", "Action"]}>
                {realmPreview.realms.map((realm) => (
                  <tr key={realm}><td>{realm}</td><td>Apply current security baseline</td></tr>
                ))}
              </DataTable>
            </div>
          )}
          {realmSyncRun && (
            <div>
              <p><strong>Execution results</strong> · {realmSyncRun.results.length - failedRealmCount} applied · {failedRealmCount} failed</p>
              <DataTable headers={["Realm", "Result", "Safe error code"]}>
                {realmSyncRun.results.map((result) => (
                  <tr key={result.realm} style={!result.applied ? { background: "var(--color-status-danger-bg)" } : undefined}>
                    <td>{result.realm}</td>
                    <td><StatusBadge value={result.applied ? "APPLIED" : "FAILED"} /></td>
                    <td>{result.error ?? "—"}</td>
                  </tr>
                ))}
              </DataTable>
              {failedRealmCount > 0 && <Notice tone="warning">Failed realms are highlighted. Retry targets only the failed realms from this run.</Notice>}
            </div>
          )}
        </Panel>
      )}

      <div className="app-two-col">
        {/* Policy Configuration Form */}
        <Panel title="Platform Security Rules" description="Set security baseline enforced across realms">
          {loading && !policy ? (
            <SkeletonCard>
              <SkeletonText height="1rem" width="45%" />
              <SkeletonText lines={5} height="1.25rem" />
            </SkeletonCard>
          ) : policyError && !policy ? (
            <ErrorState
              message={policyError}
              action={
                <Button variant="secondary" onClick={() => void loadPolicy()}>
                  Retry Policy Load
                </Button>
              }
            />
          ) : (
            <form onSubmit={handleSavePolicy}>
              <div style={{ display: "grid", gap: "14px" }}>
                <TextField
                  id="sec-min-pwd-len"
                  label="Minimum Password Length"
                  type="number"
                  value={minPasswordLength}
                  onChange={(e) => setMinPasswordLength(Number(e.target.value))}
                  min={8}
                  max={64}
                  disabled={!canEditPolicy}
                  required
                />

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                  <TextField
                    id="sec-max-failed-logins"
                    label="Max Failed Logins"
                    type="number"
                    value={maxFailedLogins}
                    onChange={(e) => setMaxFailedLogins(Number(e.target.value))}
                    min={3}
                    max={10}
                    disabled={!canEditPolicy}
                    required
                  />
                  <TextField
                    id="sec-lockout-minutes"
                    label="Lockout Duration (mins)"
                    type="number"
                    value={lockoutMinutes}
                    onChange={(e) => setLockoutMinutes(Number(e.target.value))}
                    min={5}
                    disabled={!canEditPolicy}
                    required
                  />
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                  <TextField
                    id="sec-idle-timeout"
                    label="Idle Session Timeout (mins)"
                    type="number"
                    value={idleSessionTimeoutMinutes}
                    onChange={(e) => setIdleSessionTimeoutMinutes(Number(e.target.value))}
                    min={5}
                    disabled={!canEditPolicy}
                    required
                  />
                  <TextField
                    id="sec-backup-retention"
                    label="Backup Retention (days)"
                    type="number"
                    value={defaultBackupRetentionDays}
                    onChange={(e) => setDefaultBackupRetentionDays(Number(e.target.value))}
                    min={30}
                    disabled={!canEditPolicy}
                    required
                  />
                </div>

                <div style={{ display: "grid", gap: "8px", marginTop: "4px" }}>
                  <CheckboxField
                    id="sec-require-special"
                    label="Require at least one special character"
                    checked={requireSpecialChar}
                    onChange={(e) => setRequireSpecialChar(e.target.checked)}
                    disabled={!canEditPolicy}
                  />
                  <CheckboxField
                    id="sec-require-digit"
                    label="Require at least one numerical digit"
                    checked={requireDigit}
                    onChange={(e) => setRequireDigit(e.target.checked)}
                    disabled={!canEditPolicy}
                  />
                  <CheckboxField
                    id="sec-mfa-privileged"
                    label="Enforce Multi-Factor Authentication (MFA) for privileged roles"
                    checked={mfaRequiredForPrivileged}
                    onChange={(e) => setMfaRequiredForPrivileged(e.target.checked)}
                    disabled={!canEditPolicy}
                  />
                </div>
              </div>

              {canEditPolicy && (
                <div style={{ marginTop: "20px", display: "flex", justifyContent: "flex-end" }}>
                  <Button type="submit" disabled={savingPolicy}>
                    {savingPolicy ? "Saving Policy..." : "Update Security Policy"}
                  </Button>
                </div>
              )}
            </form>
          )}
        </Panel>

        {/* Incidents Panel */}
        <Panel title="Security Incidents" description="Active threat reports and containment log">
          {incidentError ? (
            <ErrorState
              message={incidentError}
              action={
                <Button variant="secondary" onClick={() => void loadIncidents()}>
                  Retry Incident Load
                </Button>
              }
            />
          ) : (
            <DataTable
              headers={["Title", "Severity", "Status", "Actions"]}
              loading={loading && incidents.length === 0}
              loadingLabel="Loading incidents"
              empty={!loading && incidents.length === 0}
              emptyMessage="No security incidents logged."
            >
              {incidents.map((inc) => (
                <tr key={inc.securityIncidentId}>
                  <td>
                    <strong>{inc.title}</strong>
                    <small className="app-cell-sub">
                      Detected {new Date(inc.detectedAt).toLocaleDateString()}
                    </small>
                  </td>
                  <td>
                    <span style={{ fontWeight: "bold", color: inc.severity === "CRITICAL" ? "var(--color-status-danger-fg)" : "inherit" }}>
                      {inc.severity}
                    </span>
                  </td>
                  <td>
                    <StatusBadge value={inc.status} />
                  </td>
                  <td>
                    <div style={{ display: "flex", gap: "6px" }}>
                      {inc.status === "DETECTED" && canEditPolicy && (
                        <Button size="sm" variant="secondary" onClick={() => handleUpdateIncidentStatus(inc.securityIncidentId, "CONTAINED")}>
                          Contain
                        </Button>
                      )}
                      {inc.status === "CONTAINED" && canEditPolicy && (
                        <Button size="sm" variant="secondary" onClick={() => handleUpdateIncidentStatus(inc.securityIncidentId, "RECOVERED")}>
                          Recover
                        </Button>
                      )}
                      {inc.status !== "CLOSED" && canEditPolicy && (
                        <Button size="sm" variant="secondary" onClick={() => handleUpdateIncidentStatus(inc.securityIncidentId, "CLOSED")}>
                          Close
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </DataTable>
          )}
        </Panel>
      </div>

      {/* New Incident Modal */}
      <Modal
        isOpen={showIncidentModal}
        onClose={() => setShowIncidentModal(false)}
        kicker="SECURITY"
        title="Log Security Incident"
        titleId="log-incident-title"
        description="Report an anomaly, breach attempt, or suspicious platform activity."
        size="sm"
      >
        <form onSubmit={handleCreateIncident}>
          {incidentModalError && (
            <div style={{ marginBottom: "12px" }}>
              <Notice tone="danger">{incidentModalError}</Notice>
            </div>
          )}
          <div style={{ display: "grid", gap: "12px" }}>
            <TextField
              id="incident-title"
              label="Incident Summary / Title"
              value={incidentTitle}
              onChange={(e) => setIncidentTitle(e.target.value)}
              placeholder="e.g. Unusual brute-force attempt on admin auth"
              required
            />
            <SelectField
              id="incident-severity"
              label="Severity Level"
              value={incidentSeverity}
              onChange={(e) => setIncidentSeverity(e.target.value)}
              options={[
                { value: "LOW", label: "Low" },
                { value: "MEDIUM", label: "Medium" },
                { value: "HIGH", label: "High" },
                { value: "CRITICAL", label: "Critical" },
              ]}
            />
            <CheckboxField
              id="incident-external-notify"
              label="CERT-In / External authority notification required"
              checked={incidentExternalNotify}
              onChange={(e) => setIncidentExternalNotify(e.target.checked)}
            />
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
            <Button variant="secondary" onClick={() => setShowIncidentModal(false)} type="button">
              Cancel
            </Button>
            <Button type="submit" busy={submittingIncident}>
              Submit Incident Report
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

export const SecurityPolicyPage = SecurityPolicyView;
