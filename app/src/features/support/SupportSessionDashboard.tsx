import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../services/axios";
import { Badge, Button, MetricCard, Notice, PageHeader, Panel } from "../../design-system/components";
import type { SupportSessionState } from "./useSupportSession";

export interface SupportSessionDashboardProps {
  session: SupportSessionState | null;
  onEndSession: () => void;
}

interface DiagnosticSummary {
  classesCount: number | null;
  academicSessionsCount: number | null;
  departmentsCount: number | null;
  rolesCount: number | null;
  academicSessions: Array<{ academicSessionId: string; sessionLabel: string; isCurrent?: boolean }>;
}

export function SupportSessionDashboard({ session, onEndSession }: SupportSessionDashboardProps) {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState<DiagnosticSummary>({
    classesCount: null,
    academicSessionsCount: null,
    departmentsCount: null,
    rolesCount: null,
    academicSessions: [],
  });
  const [timeRemaining, setTimeRemaining] = useState<string>("");
  const [tenantName, setTenantName] = useState<string>("");

  const tenantId = session?.tenantId;
  const ticketNumber = session?.ticketNumber || "Active Ticket";
  const expiresAt = session?.expiresAt;

  useEffect(() => {
    if (!expiresAt) return;
    const updateCountdown = () => {
      const diff = new Date(expiresAt).getTime() - Date.now();
      if (diff <= 0) {
        setTimeRemaining("Session Expired");
        return;
      }
      const hours = Math.floor(diff / (1000 * 60 * 60));
      const mins = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
      const secs = Math.floor((diff % (1000 * 60)) / 1000);
      setTimeRemaining(`${hours}h ${mins}m ${secs}s`);
    };
    updateCountdown();
    const interval = setInterval(updateCountdown, 1000);
    return () => clearInterval(interval);
  }, [expiresAt]);

  useEffect(() => {
    if (!tenantId) {
      setLoading(false);
      return;
    }
    let isCurrent = true;
    api.get(`/platform/tenants/${tenantId}`)
      .then((res) => { if (isCurrent && res.data?.name) setTenantName(res.data.name); })
      .catch(() => {});
    const fetchDiagnostics = async () => {
      setLoading(true);
      try {
        const [sessionsRes, classesRes, deptsRes, rolesRes] = await Promise.allSettled([
          api.get(`/tenants/${tenantId}/academic-sessions`),
          api.get(`/tenants/${tenantId}/classes`),
          api.get(`/tenants/${tenantId}/departments`),
          api.get(`/tenants/${tenantId}/roles`),
        ]);

        if (!isCurrent) return;

        const sessions = sessionsRes.status === "fulfilled" && Array.isArray(sessionsRes.value.data)
          ? sessionsRes.value.data
          : [];
        const classes = classesRes.status === "fulfilled" && Array.isArray(classesRes.value.data)
          ? classesRes.value.data
          : [];
        const depts = deptsRes.status === "fulfilled" && Array.isArray(deptsRes.value.data)
          ? deptsRes.value.data
          : [];
        const roles = rolesRes.status === "fulfilled" && Array.isArray(rolesRes.value.data)
          ? rolesRes.value.data
          : [];

        setData({
          academicSessionsCount: sessions.length,
          classesCount: classes.length,
          departmentsCount: depts.length,
          rolesCount: roles.length,
          academicSessions: sessions.slice(0, 5),
        });
      } catch {
        // Support token read error or endpoint not yet configured
      } finally {
        if (isCurrent) setLoading(false);
      }
    };

    void fetchDiagnostics();
    return () => {
      isCurrent = false;
    };
  }, [tenantId]);

  return (
    <div className="app-page">
      <PageHeader
        title="Support Investigation Overview"
        description={`Active read-only support session bound to ticket ${ticketNumber}.`}
        actions={
          <Button variant="secondary" onClick={onEndSession}>
            End support session
          </Button>
        }
      />

      <Notice tone="info" inline>
        <strong>Zero-Mutation Guard Active:</strong> You are troubleshooting this tenant in strict read-only mode under customer authorization. All reads are audited to both the tenant outbox and the platform compliance log.
      </Notice>

      {/* KPI Overview Grid */}
      <div className="kpi-grid" style={{ marginTop: "var(--space-4)" }}>
        <MetricCard
          label="Ticket Reference"
          value={ticketNumber}
          note="Customer authorization reference"
        />
        <MetricCard
          label="Time Remaining"
          value={timeRemaining || "Active"}
          note={expiresAt ? `Expires at ${new Date(expiresAt).toLocaleTimeString()}` : "Session active"}
        />
        <MetricCard
          label="Access Scope"
          value="Tenant Read-Only"
          note="State-modifying calls blocked"
        />
        <MetricCard
          label="Tenant"
          value={tenantName || (tenantId ? `${tenantId.slice(0, 8)}…` : "Connected")}
          note={tenantName && tenantId ? `ID: ${tenantId.slice(0, 8)}…` : "Active institution workspace"}
        />
      </div>

      {/* Diagnostic Findings & Navigation */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(360px, 1fr))",
          gap: "var(--space-4)",
          marginTop: "var(--space-4)",
        }}
      >
        {/* Panel 1: Tenant Configuration Health */}
        <Panel
          title="Institution Structure Status"
          description="Live diagnostic counts verified via support-readable API queries."
        >
          {loading ? (
            <p className="app-muted">Scanning tenant configuration…</p>
          ) : (
            <div style={{ display: "grid", gap: "var(--space-3)" }}>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "repeat(auto-fit, minmax(130px, 1fr))",
                  gap: "var(--space-2)",
                }}
              >
                <div style={{ padding: "var(--space-3)", background: "var(--color-bg-subtle)", borderRadius: "var(--radius-md)", border: "1px solid var(--color-border-divider)" }}>
                  <div className="app-muted" style={{ fontSize: "11px", textTransform: "uppercase" }}>Classes</div>
                  <div style={{ fontSize: "var(--fs-h2)", fontWeight: "var(--fw-bold)" }}>{data.classesCount ?? "0"}</div>
                </div>
                <div style={{ padding: "var(--space-3)", background: "var(--color-bg-subtle)", borderRadius: "var(--radius-md)", border: "1px solid var(--color-border-divider)" }}>
                  <div className="app-muted" style={{ fontSize: "11px", textTransform: "uppercase" }}>Sessions</div>
                  <div style={{ fontSize: "var(--fs-h2)", fontWeight: "var(--fw-bold)" }}>{data.academicSessionsCount ?? "0"}</div>
                </div>
                <div style={{ padding: "var(--space-3)", background: "var(--color-bg-subtle)", borderRadius: "var(--radius-md)", border: "1px solid var(--color-border-divider)" }}>
                  <div className="app-muted" style={{ fontSize: "11px", textTransform: "uppercase" }}>Departments</div>
                  <div style={{ fontSize: "var(--fs-h2)", fontWeight: "var(--fw-bold)" }}>{data.departmentsCount ?? "0"}</div>
                </div>
                <div style={{ padding: "var(--space-3)", background: "var(--color-bg-subtle)", borderRadius: "var(--radius-md)", border: "1px solid var(--color-border-divider)" }}>
                  <div className="app-muted" style={{ fontSize: "11px", textTransform: "uppercase" }}>Roles</div>
                  <div style={{ fontSize: "var(--fs-h2)", fontWeight: "var(--fw-bold)" }}>{data.rolesCount ?? "0"}</div>
                </div>
              </div>

              {data.academicSessions.length > 0 && (
                <div style={{ marginTop: "var(--space-2)" }}>
                  <div className="app-muted" style={{ fontSize: "var(--fs-caption)", marginBottom: "var(--space-1)" }}>
                    Configured Academic Sessions:
                  </div>
                  <div style={{ display: "flex", flexWrap: "wrap", gap: "var(--space-1)" }}>
                    {data.academicSessions.map((s) => (
                      <Badge key={s.academicSessionId} tone={s.isCurrent ? "success" : "neutral"}>
                        {s.sessionLabel} {s.isCurrent ? "· Current" : ""}
                      </Badge>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </Panel>

        {/* Panel 2: Read-Only Inspection Destinations */}
        <Panel
          title="Diagnostic Navigation"
          description="Browse the customer's setup to investigate and reproduce the reported issue."
        >
          <div style={{ display: "grid", gap: "var(--space-2)" }}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                padding: "var(--space-3)",
                background: "var(--color-bg-subtle)",
                borderRadius: "var(--radius-md)",
                border: "1px solid var(--color-border-divider)",
              }}
            >
              <div>
                <strong style={{ fontSize: "var(--fs-body)" }}>Academic Structure</strong>
                <p className="app-muted" style={{ margin: "var(--space-1) 0 0", fontSize: "var(--fs-caption)" }}>
                  Inspect configured departments, programs, classes, sections, and catalog.
                </p>
              </div>
              <Button size="sm" variant="secondary" onClick={() => navigate("/university/setup/structure")}>
                Inspect ↗
              </Button>
            </div>

            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                padding: "var(--space-3)",
                background: "var(--color-bg-subtle)",
                borderRadius: "var(--radius-md)",
                border: "1px solid var(--color-border-divider)",
              }}
            >
              <div>
                <strong style={{ fontSize: "var(--fs-body)" }}>Calendar & Sessions</strong>
                <p className="app-muted" style={{ margin: "var(--space-1) 0 0", fontSize: "var(--fs-caption)" }}>
                  Review calendar master events, exams schedules, and session dates.
                </p>
              </div>
              <Button size="sm" variant="secondary" onClick={() => navigate("/university/setup/calendar-events")}>
                Inspect ↗
              </Button>
            </div>

            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                padding: "var(--space-3)",
                background: "var(--color-bg-subtle)",
                borderRadius: "var(--radius-md)",
                border: "1px solid var(--color-border-divider)",
              }}
            >
              <div>
                <strong style={{ fontSize: "var(--fs-body)" }}>Role Previews</strong>
                <p className="app-muted" style={{ margin: "var(--space-1) 0 0", fontSize: "var(--fs-caption)" }}>
                  Preview UI layouts as seen by Principal, HOD, Teacher, Student, or Parent.
                </p>
              </div>
              <Button size="sm" variant="secondary" onClick={() => navigate("/role-previews")}>
                View Previews ↗
              </Button>
            </div>

            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                padding: "var(--space-3)",
                background: "var(--color-bg-subtle)",
                borderRadius: "var(--radius-md)",
                border: "1px solid var(--color-border-divider)",
              }}
            >
              <div>
                <strong style={{ fontSize: "var(--fs-body)" }}>Support Session Status</strong>
                <p className="app-muted" style={{ margin: "var(--space-1) 0 0", fontSize: "var(--fs-caption)" }}>
                  Review the ticket authorization terms and read-only boundary constraints.
                </p>
              </div>
              <Button size="sm" variant="secondary" onClick={() => navigate("/support")}>
                Review ↗
              </Button>
            </div>
          </div>
        </Panel>
      </div>

      {/* Footer Return Card */}
      <div
        style={{
          marginTop: "var(--space-5)",
          padding: "var(--space-4)",
          background: "var(--color-bg-surface)",
          borderRadius: "var(--radius-lg)",
          border: "1px solid var(--color-border-divider)",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flexWrap: "wrap",
          gap: "var(--space-3)",
        }}
      >
        <div>
          <strong>Finished your investigation?</strong>
          <p className="app-muted" style={{ margin: "var(--space-1) 0 0", fontSize: "var(--fs-caption)" }}>
            Ending this support session invalidates the temporary token and returns you to the Platform Console.
          </p>
        </div>
        <Button variant="secondary" onClick={onEndSession}>
          End session & return to console
        </Button>
      </div>
    </div>
  );
}
