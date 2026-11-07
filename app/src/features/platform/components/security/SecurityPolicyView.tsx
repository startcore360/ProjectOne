import { useEffect, useState, type FormEvent } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import {
  Button,
  DataTable,
  MetricCard,
  Notice,
  Panel,
  StatusBadge,
} from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
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
  const [loading, setLoading] = useState(false);
  const [savingPolicy, setSavingPolicy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

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

  const loadData = async () => {
    setLoading(true);
    setError("");
    try {
      const [pol, incs] = await Promise.all([
        platformOpsService.getSecurityPolicy(),
        platformOpsService.listSecurityIncidents(),
      ]);
      setPolicy(pol);
      setIncidents(incs);
      setMinPasswordLength(pol.minPasswordLength);
      setRequireSpecialChar(pol.requireSpecialChar);
      setRequireDigit(pol.requireDigit);
      setMaxFailedLogins(pol.maxFailedLogins);
      setLockoutMinutes(pol.lockoutMinutes);
      setIdleSessionTimeoutMinutes(pol.idleSessionTimeoutMinutes);
      setMfaRequiredForPrivileged(pol.mfaRequiredForPrivileged);
      setDefaultBackupRetentionDays(pol.defaultBackupRetentionDays);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load security configuration");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleSavePolicy = async (e: FormEvent) => {
    e.preventDefault();
    setSavingPolicy(true);
    setError("");
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
      setError(err instanceof Error ? err.message : "Failed to save security policy");
    } finally {
      setSavingPolicy(false);
    }
  };

  const handleCreateIncident = async (e: FormEvent) => {
    e.preventDefault();
    setSubmittingIncident(true);
    setError("");
    try {
      await platformOpsService.createSecurityIncident({
        title: incidentTitle.trim(),
        severity: incidentSeverity,
        externalNotificationRequired: incidentExternalNotify,
      });
      setMessage("Security incident logged.");
      setShowIncidentModal(false);
      setIncidentTitle("");
      loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to log security incident");
    } finally {
      setSubmittingIncident(false);
    }
  };

  const handleUpdateIncidentStatus = async (id: string, newStatus: string) => {
    try {
      await platformOpsService.updateSecurityIncident(id, {
        status: newStatus,
        closureNote: newStatus === "CLOSED" ? "Resolved by Data Protection / Security Officer" : undefined,
      });
      setMessage(`Incident marked as ${newStatus}.`);
      loadData();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update incident status");
    }
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="Security Policy & Incidents"
        description="Global platform authentication controls, password complexity rules, session timeouts, and security incident tracking."
        actions={
          canEditPolicy && (
            <Button onClick={() => setShowIncidentModal(true)}>Log Security Incident</Button>
          )
        }
      />

      {message && <Notice tone="success">{message}</Notice>}
      {error && <Notice tone="danger">{error}</Notice>}

      <div className="kpi-grid">
        <MetricCard
          label="MFA Enforcement"
          value={policy?.mfaRequiredForPrivileged ? "Active" : "Optional"}
          note="Privileged staff accounts"
          mark="shield"
        />
        <MetricCard
          label="Lockout Threshold"
          value={`${policy?.maxFailedLogins ?? 5} attempts`}
          note={`Lockout for ${policy?.lockoutMinutes ?? 15} mins`}
          mark="lock"
        />
        <MetricCard
          label="Session Timeout"
          value={`${policy?.idleSessionTimeoutMinutes ?? 30} mins`}
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

      <div className="platform-two-col">
        {/* Policy Configuration Form */}
        <Panel title="Platform Security Rules" description="Set security baseline enforced across realms">
          {loading ? (
            <div className="platform-empty-inline">
              <Spinner label="Loading policy..." />
            </div>
          ) : (
            <form onSubmit={handleSavePolicy}>
              <div style={{ display: "grid", gap: "14px" }}>
                <label className="field-label">
                  <span>Minimum Password Length</span>
                  <input
                    type="number"
                    className="platform-input"
                    value={minPasswordLength}
                    onChange={(e) => setMinPasswordLength(Number(e.target.value))}
                    min="8"
                    max="64"
                    disabled={!canEditPolicy}
                    required
                  />
                </label>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                  <label className="field-label">
                    <span>Max Failed Logins</span>
                    <input
                      type="number"
                      className="platform-input"
                      value={maxFailedLogins}
                      onChange={(e) => setMaxFailedLogins(Number(e.target.value))}
                      min="3"
                      max="10"
                      disabled={!canEditPolicy}
                      required
                    />
                  </label>
                  <label className="field-label">
                    <span>Lockout Duration (mins)</span>
                    <input
                      type="number"
                      className="platform-input"
                      value={lockoutMinutes}
                      onChange={(e) => setLockoutMinutes(Number(e.target.value))}
                      min="5"
                      disabled={!canEditPolicy}
                      required
                    />
                  </label>
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                  <label className="field-label">
                    <span>Idle Session Timeout (mins)</span>
                    <input
                      type="number"
                      className="platform-input"
                      value={idleSessionTimeoutMinutes}
                      onChange={(e) => setIdleSessionTimeoutMinutes(Number(e.target.value))}
                      min="5"
                      disabled={!canEditPolicy}
                      required
                    />
                  </label>
                  <label className="field-label">
                    <span>Backup Retention (days)</span>
                    <input
                      type="number"
                      className="platform-input"
                      value={defaultBackupRetentionDays}
                      onChange={(e) => setDefaultBackupRetentionDays(Number(e.target.value))}
                      min="30"
                      disabled={!canEditPolicy}
                      required
                    />
                  </label>
                </div>

                <div style={{ display: "grid", gap: "8px", marginTop: "4px" }}>
                  <label style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                    <input
                      type="checkbox"
                      checked={requireSpecialChar}
                      onChange={(e) => setRequireSpecialChar(e.target.checked)}
                      disabled={!canEditPolicy}
                    />
                    <span>Require at least one special character</span>
                  </label>
                  <label style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                    <input
                      type="checkbox"
                      checked={requireDigit}
                      onChange={(e) => setRequireDigit(e.target.checked)}
                      disabled={!canEditPolicy}
                    />
                    <span>Require at least one numerical digit</span>
                  </label>
                  <label style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                    <input
                      type="checkbox"
                      checked={mfaRequiredForPrivileged}
                      onChange={(e) => setMfaRequiredForPrivileged(e.target.checked)}
                      disabled={!canEditPolicy}
                    />
                    <span>Enforce Multi-Factor Authentication (MFA) for privileged roles</span>
                  </label>
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
          {incidents.length === 0 ? (
            <p className="platform-empty-inline">No security incidents logged.</p>
          ) : (
            <DataTable headers={["Title", "Severity", "Status", "Actions"]}>
              {incidents.map((inc) => (
                <tr key={inc.securityIncidentId}>
                  <td>
                    <strong>{inc.title}</strong>
                    <small className="platform-cell-sub">
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
      {showIncidentModal && (
        <div className="platform-modal-backdrop">
          <div className="platform-modal" role="dialog" aria-label="Log Security Incident">
            <header>
              <h2>Log Security Incident</h2>
              <p>Report an anomaly, breach attempt, or suspicious platform activity.</p>
            </header>
            <form onSubmit={handleCreateIncident}>
              <div style={{ display: "grid", gap: "12px" }}>
                <label className="field-label">
                  <span>Incident Summary / Title</span>
                  <input
                    type="text"
                    className="platform-input"
                    value={incidentTitle}
                    onChange={(e) => setIncidentTitle(e.target.value)}
                    placeholder="e.g. Unusual brute-force attempt on admin auth"
                    required
                  />
                </label>
                <label className="field-label">
                  <span>Severity Level</span>
                  <select
                    className="platform-input"
                    value={incidentSeverity}
                    onChange={(e) => setIncidentSeverity(e.target.value)}
                  >
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                    <option value="CRITICAL">Critical</option>
                  </select>
                </label>
                <label style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                  <input
                    type="checkbox"
                    checked={incidentExternalNotify}
                    onChange={(e) => setIncidentExternalNotify(e.target.checked)}
                  />
                  <span>CERT-In / External authority notification required</span>
                </label>
              </div>
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", marginTop: "20px" }}>
                <Button variant="secondary" onClick={() => setShowIncidentModal(false)} type="button">
                  Cancel
                </Button>
                <Button type="submit" disabled={submittingIncident}>
                  {submittingIncident ? "Logging..." : "Submit Incident Report"}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export const SecurityPolicyPage = SecurityPolicyView;
