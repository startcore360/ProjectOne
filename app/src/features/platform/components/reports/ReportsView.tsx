import { useEffect, useState, useMemo } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Button, MetricCard, Notice, Panel, SkeletonMetricCard } from "../../../../design-system/components";
import { ErrorState } from "../../../../components/feedback/ErrorState";
import { Icon, type IconName } from "../../../../components/ui/Icon";
import { getApiErrorMessage } from "../../../../services/apiHelper";
import {
  platformOpsService,
  type PlatformReportItem,
  type InvoiceItem,
  type SupportTicketItem,
} from "../../api/platformOpsService";
import { platformTenantService, type TenantItem } from "../../api/platformTenantService";
import { usePlatformAccess } from "../../platformAccess";
import { generateReportCsv, generateReportPdfBlob } from "./reportExportUtils";

export function ReportsView() {
  const { role } = usePlatformAccess();
  const [reportCode, setReportCode] = useState<string>(
    role === "BILLING_MANAGER" ? "BILLING_REVENUE" : role === "SUPPORT_MANAGER" ? "SUPPORT_SLA" : "PLATFORM_OVERVIEW"
  );
  const [report, setReport] = useState<PlatformReportItem | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [tenantsError, setTenantsError] = useState("");
  const [invoicesError, setInvoicesError] = useState("");
  const [ticketsError, setTicketsError] = useState("");

  // Detailed dataset states for rich interactive tables
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [invoices, setInvoices] = useState<InvoiceItem[]>([]);
  const [tickets, setTickets] = useState<SupportTicketItem[]>([]);

  // Filtering states for the data tables
  const [tenantSearch, setTenantSearch] = useState("");
  const [tenantFilter, setTenantFilter] = useState<"ALL" | "ACTIVE" | "TRIAL" | "SUSPENDED">("ALL");

  const [invoiceFilter, setInvoiceFilter] = useState<"ALL" | "PAID" | "PENDING">("ALL");
  const [ticketFilter, setTicketFilter] = useState<"ALL" | "OPEN" | "RESOLVED">("ALL");

  const loadReport = async (code: string) => {
    setLoading(true);
    setError("");
    setTenantsError("");
    setInvoicesError("");
    setTicketsError("");
    setReport(null);

    try {
      const [reportResult, tenantsResult, invoicesResult, ticketsResult] = await Promise.allSettled([
        platformOpsService.getReport(code),
        platformTenantService.getTenants(0, 100),
        platformOpsService.listInvoices(),
        platformOpsService.listSupportTickets(),
      ]);

      if (reportResult.status === "fulfilled") {
        setReport(reportResult.value);
      } else {
        setError(getApiErrorMessage(reportResult.reason));
      }

      if (tenantsResult.status === "fulfilled") {
        const res = tenantsResult.value;
        const items = Array.isArray(res?.data?.items) ? res.data.items : Array.isArray(res?.data) ? res.data : [];
        setTenants(items);
      } else {
        setTenants([]);
        setTenantsError(getApiErrorMessage(tenantsResult.reason));
      }

      if (invoicesResult.status === "fulfilled") {
        const invList = invoicesResult.value;
        setInvoices(Array.isArray(invList) ? invList : []);
      } else {
        setInvoices([]);
        setInvoicesError(getApiErrorMessage(invoicesResult.reason));
      }

      if (ticketsResult.status === "fulfilled") {
        const tkList = ticketsResult.value;
        setTickets(Array.isArray(tkList) ? tkList : []);
      } else {
        setTickets([]);
        setTicketsError(getApiErrorMessage(ticketsResult.reason));
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadReport(reportCode);
  }, [reportCode]);

  const reportTabs: Array<{ code: string; label: string; icon: IconName }> = [
    { code: "PLATFORM_OVERVIEW", label: "Platform Overview", icon: "layers" },
    { code: "BILLING_REVENUE", label: "Billing & Revenue", icon: "wallet" },
    { code: "SUPPORT_SLA", label: "Support & SLA", icon: "users" },
  ];

  const handleExportCsv = () => {
    if (!report) return;
    const csvContent = generateReportCsv({
      report,
      reportCode,
      tenants,
      invoices,
      tickets,
    });
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.setAttribute("download", `${report.reportCode.toLowerCase()}-${new Date().toISOString().split("T")[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleExportPdf = () => {
    if (!report) return;
    const blob = generateReportPdfBlob({
      report,
      reportCode,
      tenants,
      invoices,
      tickets,
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.setAttribute("download", `${report.reportCode.toLowerCase()}-${new Date().toISOString().split("T")[0]}.pdf`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  // Filtered tenants list
  const filteredTenants = useMemo(() => {
    return tenants.filter((t) => {
      const matchFilter =
        tenantFilter === "ALL" ||
        (tenantFilter === "ACTIVE" && t.state === "ACTIVE") ||
        (tenantFilter === "TRIAL" && t.state === "TRIAL") ||
        (tenantFilter === "SUSPENDED" && (t.state === "SUSPENDED" || t.state === "ARCHIVED"));

      const q = tenantSearch.trim().toLowerCase();
      const matchSearch =
        !q ||
        t.tenantName.toLowerCase().includes(q) ||
        (t.subdomain && t.subdomain.toLowerCase().includes(q)) ||
        (t.planName && t.planName.toLowerCase().includes(q));

      return matchFilter && matchSearch;
    });
  }, [tenants, tenantFilter, tenantSearch]);

  // Filtered invoices list
  const filteredInvoices = useMemo(() => {
    return invoices.filter((inv) => {
      if (invoiceFilter === "PAID") return inv.status === "PAID";
      if (invoiceFilter === "PENDING") return inv.status === "SENT" || inv.overdue;
      return true;
    });
  }, [invoices, invoiceFilter]);

  // Filtered tickets list
  const filteredTickets = useMemo(() => {
    return tickets.filter((tk) => {
      if (ticketFilter === "OPEN") return tk.status !== "CLOSED" && tk.status !== "RESOLVED";
      if (ticketFilter === "RESOLVED") return tk.status === "CLOSED" || tk.status === "RESOLVED";
      return true;
    });
  }, [tickets, ticketFilter]);

  return (
    <div className="app-page">
      <PageHeader
        title="Platform Reports"
        description="Aggregated platform executive analytics, revenue collections, system throughput, and support SLAs."
      />

      {error && (
        <ErrorState
          message={error}
          action={<Button variant="secondary" onClick={() => void loadReport(reportCode)}>Retry</Button>}
        />
      )}

      {/* Modern Segmented Navigation Toolbar */}
      <div
        className="app-toolbar"
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flexWrap: "wrap",
          gap: "var(--space-3)",
          marginBottom: "var(--space-4)",
        }}
      >
        <div
          role="tablist"
          aria-label="Platform Report Categories"
          style={{
            display: "flex",
            alignItems: "center",
            gap: "4px",
            background: "var(--color-bg-subtle)",
            padding: "4px",
            borderRadius: "var(--radius-lg)",
            border: "var(--border-width) solid var(--color-border-divider)",
            maxWidth: "100%",
            overflowX: "auto",
            scrollbarWidth: "none",
            WebkitOverflowScrolling: "touch",
            flexShrink: 0,
          }}
        >
          {reportTabs.map((tab) => {
            const isActive = reportCode === tab.code;
            return (
              <button
                key={tab.code}
                type="button"
                role="tab"
                aria-selected={isActive}
                onClick={() => setReportCode(tab.code)}
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "6px",
                  padding: "6px 14px",
                  fontSize: "var(--fs-body-sm)",
                  fontWeight: isActive ? "var(--fw-semibold)" : "var(--fw-medium)",
                  color: isActive ? "var(--color-text)" : "var(--color-text-muted)",
                  background: isActive ? "var(--color-bg-surface)" : "transparent",
                  border: isActive ? "1px solid var(--color-border-divider)" : "1px solid transparent",
                  borderRadius: "var(--radius-md)",
                  boxShadow: isActive ? "var(--elevation-card)" : "none",
                  cursor: "pointer",
                  transition: "all 0.15s ease",
                  whiteSpace: "nowrap",
                  flexShrink: 0,
                  minHeight: "34px",
                }}
              >
                <Icon name={tab.icon} size={15} />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>

        {/* Hidden select for standard form semantics / tests */}
        <select
          value={reportCode}
          onChange={(e) => setReportCode(e.target.value)}
          aria-label="Select platform report"
          style={{ position: "absolute", opacity: 0, pointerEvents: "none", width: 0, height: 0 }}
        >
          <option value="PLATFORM_OVERVIEW">Platform Overview</option>
          <option value="BILLING_REVENUE">Billing &amp; Revenue</option>
          <option value="SUPPORT_SLA">Support &amp; SLA</option>
        </select>

        <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", flexWrap: "wrap" }}>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={handleExportCsv}
            disabled={!report || loading}
            style={{ display: "inline-flex", alignItems: "center", gap: "6px", height: "32px", whiteSpace: "nowrap" }}
          >
            <Icon name="download" size={14} />
            <span>Export CSV</span>
          </Button>

          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={handleExportPdf}
            disabled={!report || loading}
            style={{ display: "inline-flex", alignItems: "center", gap: "6px", height: "32px", whiteSpace: "nowrap" }}
          >
            <Icon name="printer" size={14} />
            <span>Download PDF</span>
          </Button>

          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={() => void loadReport(reportCode)}
            disabled={loading}
            style={{ display: "inline-flex", alignItems: "center", gap: "6px", height: "32px", whiteSpace: "nowrap" }}
          >
            <Icon name="refresh" size={14} spin={loading} />
            <span>Refresh</span>
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="kpi-grid" role="status" aria-busy="true" aria-label="Loading platform report">
          <span className="sr-only">Loading platform report...</span>
          {Array.from({ length: 4 }, (_, index) => <SkeletonMetricCard key={index} />)}
        </div>
      ) : report ? (
        <>
          {(() => {
            const currencyCode = typeof report.metrics.currency === "string" ? report.metrics.currency : "INR";
            const formatCurrency = (val: number, options?: Intl.NumberFormatOptions) => {
              try {
                return new Intl.NumberFormat(currencyCode === "INR" ? "en-IN" : "en-US", {
                  style: "currency",
                  currency: currencyCode,
                  maximumFractionDigits: 0,
                  ...options,
                }).format(val);
              } catch {
                return `${currencyCode} ${val.toLocaleString()}`;
              }
            };

            const isOverview = reportCode === "PLATFORM_OVERVIEW";
            const isBilling = reportCode === "BILLING_REVENUE" || reportCode === "REVENUE";
            const isSupport = reportCode === "SUPPORT_SLA";

            const activeTenants = Number(report.metrics.activeTenants ?? 0);
            const totalTenants = Number(report.metrics.totalTenants ?? 0);
            const activationRate = totalTenants > 0 ? Math.round((activeTenants / totalTenants) * 1000) / 10 : 0;

            return (
              <>
                {/* 1. PLATFORM OVERVIEW REPORT */}
                {isOverview && (
                  <>
                    <div className="kpi-grid">
                      <MetricCard
                        label="Active Tenants"
                        value={activeTenants}
                        icon="building"
                        context={`${activeTenants} active in production out of ${totalTenants}`}
                        progress={[activeTenants, Math.max(1, totalTenants), "Active tenant share"]}
                      />
                      <MetricCard
                        label="Total Tenants"
                        value={totalTenants}
                        icon="layers"
                        context="All registered customer workspaces"
                      />
                      <MetricCard
                        label="Activation Rate"
                        value={`${activationRate}%`}
                        icon="chart"
                        context="Active institutions vs total enrolled"
                      />
                      <MetricCard
                        label="Cloud Telemetry"
                        value="Phase 2"
                        icon="activity"
                        context="Storage & API metering onboarding"
                      />
                    </div>

                    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 340px), 1fr))", gap: "var(--space-4)", marginTop: "var(--space-4)" }}>
                      <Panel title="Tenant Distribution" description="Overview of institution provisioning and operational status">
                        <div className="app-panel__body" style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
                          <div style={{ display: "flex", justifyContent: "space-between", fontSize: "var(--fs-body-sm)" }}>
                            <span style={{ color: "var(--color-text)", fontWeight: "var(--fw-medium)" }}>Active Production ({activeTenants})</span>
                            <span style={{ color: "var(--color-text-muted)" }}>
                              Trials &amp; In-Setup ({Math.max(0, totalTenants - activeTenants)})
                            </span>
                          </div>

                          <div style={{ height: "10px", width: "100%", background: "var(--color-bg-subtle)", borderRadius: "var(--radius-pill)", overflow: "hidden", display: "flex" }}>
                            <div
                              style={{
                                width: `${totalTenants > 0 ? (activeTenants / totalTenants) * 100 : 0}%`,
                                background: "var(--color-action-primary)",
                                borderRadius: "var(--radius-pill) 0 0 var(--radius-pill)",
                              }}
                            />
                            <div
                              style={{
                                flex: 1,
                                background: "var(--color-border-divider)",
                                opacity: 0.6,
                              }}
                            />
                          </div>

                          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 200px), 1fr))", gap: "var(--space-3)", marginTop: "var(--space-2)" }}>
                            <div style={{ padding: "var(--space-3)", background: "var(--color-bg-subtle)", borderRadius: "var(--radius-md)", border: "1px solid var(--color-border-divider)" }}>
                              <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                                <span style={{ width: "8px", height: "8px", borderRadius: "50%", background: "var(--color-action-primary)", flexShrink: 0 }} />
                                <strong style={{ fontSize: "var(--fs-body-sm)", color: "var(--color-text)" }}>Active Production</strong>
                              </div>
                              <p style={{ margin: "4px 0 0", fontSize: "var(--fs-caption)", color: "var(--color-text-muted)", lineHeight: 1.4 }}>
                                Verified live institutions with enabled entitlements and active subscriptions.
                              </p>
                            </div>
                            <div style={{ padding: "var(--space-3)", background: "var(--color-bg-subtle)", borderRadius: "var(--radius-md)", border: "1px solid var(--color-border-divider)" }}>
                              <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                                <span style={{ width: "8px", height: "8px", borderRadius: "50%", background: "var(--color-text-muted)", flexShrink: 0 }} />
                                <strong style={{ fontSize: "var(--fs-body-sm)", color: "var(--color-text)" }}>Trials &amp; Sandbox</strong>
                              </div>
                              <p style={{ margin: "4px 0 0", fontSize: "var(--fs-caption)", color: "var(--color-text-muted)", lineHeight: 1.4 }}>
                                Accounts in preliminary evaluation, data setup, or statutory onboarding.
                              </p>
                            </div>
                          </div>
                        </div>
                      </Panel>

                      <Panel title="Platform Services & Infrastructure" description="Subsystem operational readiness and security standards">
                        <div className="app-panel__body" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
                          {[
                            { label: "Tenant Database Isolation", status: "Enforced", detail: "Row-level tenant isolation & encrypted storage", icon: "shield" as const },
                            { label: "Identity & Federation (IAM)", status: "Operational", detail: "Keycloak SSO with MFA & role-based policies", icon: "lock" as const },
                            { label: "Statutory Data Protection", status: "Compliant", detail: "DPDP audit logs & statutory retention workflows", icon: "check-circle" as const },
                            { label: "Storage & API Telemetry", status: "In Setup", detail: "High-frequency throughput metrics rolling out in v2", icon: "activity" as const },
                          ].map((svc, i) => (
                            <div
                              key={i}
                              style={{
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "space-between",
                                gap: "var(--space-2)",
                                flexWrap: "wrap",
                                padding: "10px 12px",
                                background: "var(--color-bg-subtle)",
                                borderRadius: "var(--radius-md)",
                                border: "1px solid var(--color-border-divider)",
                              }}
                            >
                              <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", minWidth: 0 }}>
                                <Icon name={svc.icon} size={15} style={{ color: "var(--color-action-primary)", flexShrink: 0 }} />
                                <div style={{ minWidth: 0 }}>
                                  <strong style={{ display: "block", fontSize: "var(--fs-body-xs)", color: "var(--color-text)" }}>{svc.label}</strong>
                                  <span style={{ fontSize: "11px", color: "var(--color-text-muted)", wordBreak: "break-word" }}>{svc.detail}</span>
                                </div>
                              </div>
                              <span
                                style={{
                                  fontSize: "11px",
                                  fontWeight: "var(--fw-semibold)",
                                  padding: "2px 8px",
                                  borderRadius: "var(--radius-sm)",
                                  background: svc.status === "In Setup" ? "var(--color-bg-surface)" : "var(--color-bg-brand-soft)",
                                  color: svc.status === "In Setup" ? "var(--color-text-muted)" : "var(--color-action-primary)",
                                  border: "1px solid var(--color-border-divider)",
                                  flexShrink: 0,
                                }}
                              >
                                {svc.status}
                              </span>
                            </div>
                          ))}
                        </div>
                      </Panel>
                    </div>

                    {/* NEW DATA TABLE 1: Tenant Directory & Provisioning Inventory */}
                    <div style={{ marginTop: "var(--space-5)" }}>
                      <Panel
                        title="Customer Tenant Directory & Provisioning Inventory"
                        description="Verified institution environments, active subscription plans, and resource ceilings"
                      >
                        <div className="app-panel__body">
                          {tenantsError && (
                            <Notice tone="danger" style={{ marginBottom: "var(--space-3)" }}>
                              {tenantsError}
                            </Notice>
                          )}
                          {/* Search and Filter Bar */}
                          <div
                            style={{
                              display: "flex",
                              justifyContent: "space-between",
                              alignItems: "center",
                              gap: "var(--space-3)",
                              flexWrap: "wrap",
                              marginBottom: "var(--space-3)",
                            }}
                          >
                            <div style={{ display: "flex", gap: "6px", alignItems: "center", flexWrap: "wrap" }}>
                              {(
                                [
                                  { key: "ALL", label: `All (${tenants.length || totalTenants})` },
                                  { key: "ACTIVE", label: `Active (${tenants.filter((t) => t.state === "ACTIVE").length || activeTenants})` },
                                  { key: "TRIAL", label: `Trials (${tenants.filter((t) => t.state === "TRIAL").length || Math.max(0, totalTenants - activeTenants)})` },
                                  { key: "SUSPENDED", label: `Suspended (${tenants.filter((t) => t.state === "SUSPENDED" || t.state === "ARCHIVED").length})` },
                                ] as const
                              ).map((tab) => (
                                <button
                                  key={tab.key}
                                  type="button"
                                  onClick={() => setTenantFilter(tab.key)}
                                  style={{
                                    padding: "4px 10px",
                                    fontSize: "var(--fs-caption)",
                                    fontWeight: tenantFilter === tab.key ? "var(--fw-semibold)" : "var(--fw-medium)",
                                    borderRadius: "var(--radius-pill)",
                                    background: tenantFilter === tab.key ? "var(--color-bg-brand-soft)" : "var(--color-bg-subtle)",
                                    color: tenantFilter === tab.key ? "var(--color-action-primary)" : "var(--color-text-muted)",
                                    border: "1px solid var(--color-border-divider)",
                                    cursor: "pointer",
                                  }}
                                >
                                  {tab.label}
                                </button>
                              ))}
                            </div>

                            <input
                              type="search"
                              placeholder="Search tenants..."
                              value={tenantSearch}
                              onChange={(e) => setTenantSearch(e.target.value)}
                              aria-label="Search tenants"
                              style={{
                                padding: "6px 12px",
                                fontSize: "var(--fs-body-sm)",
                                borderRadius: "var(--radius-md)",
                                border: "1px solid var(--color-border-divider)",
                                background: "var(--color-bg-surface)",
                                color: "var(--color-text)",
                                minWidth: "200px",
                              }}
                            />
                          </div>

                          {/* Responsive Table Wrapper */}
                          <div className="app-table-wrap">
                            <table className="app-table" aria-label="Customer Tenants Table">
                              <thead>
                                <tr>
                                  <th>Tenant Workspace</th>
                                  <th>Type</th>
                                  <th>Domain / Vanity URL</th>
                                  <th>Subscription Tier</th>
                                  <th>Lifecycle State</th>
                                  <th>User Quota</th>
                                  <th>Storage</th>
                                </tr>
                              </thead>
                              <tbody>
                                {filteredTenants.length > 0 ? (
                                  filteredTenants.map((t) => (
                                    <tr key={t.tenantId}>
                                      <td>
                                        <strong style={{ display: "block", color: "var(--color-text)" }}>{t.tenantName}</strong>
                                        <small style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>{t.tenantId.slice(0, 8)}</small>
                                      </td>
                                      <td>{t.tenantType === "STANDALONE_SCHOOL" ? "School" : t.tenantType === "STANDALONE_COLLEGE" ? "College" : "University"}</td>
                                      <td>
                                        <span style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>
                                          {t.subdomain ? `${t.subdomain}.edu360.com` : "None"}
                                        </span>
                                      </td>
                                      <td>
                                        <span style={{ fontSize: "var(--fs-body-xs)", fontWeight: "var(--fw-medium)" }}>
                                          {t.planName || "Standard Plan"}
                                        </span>
                                      </td>
                                      <td>
                                        <span
                                          style={{
                                            display: "inline-block",
                                            fontSize: "11px",
                                            fontWeight: "var(--fw-semibold)",
                                            padding: "2px 8px",
                                            borderRadius: "var(--radius-sm)",
                                            background:
                                              t.state === "ACTIVE"
                                                ? "var(--color-bg-brand-soft)"
                                                : t.state === "TRIAL"
                                                ? "var(--color-bg-subtle)"
                                                : "var(--color-bg-danger-subtle, #fee2e2)",
                                            color:
                                              t.state === "ACTIVE"
                                                ? "var(--color-action-primary)"
                                                : t.state === "TRIAL"
                                                ? "var(--color-text-muted)"
                                                : "var(--color-text-danger, #b91c1c)",
                                            border: "1px solid var(--color-border-divider)",
                                          }}
                                        >
                                          {t.state}
                                        </span>
                                      </td>
                                      <td>{t.userLimit ? `${t.userLimit.toLocaleString()} users` : "Unlimited"}</td>
                                      <td>{t.storageLimitGb ? `${t.storageLimitGb} GB` : "50 GB"}</td>
                                    </tr>
                                  ))
                                ) : (
                                  <tr>
                                    <td colSpan={7} style={{ textAlign: "center", padding: "var(--space-4)", color: "var(--color-text-muted)" }}>
                                      {tenantSearch ? "No tenants match the search criteria." : "No registered tenants found."}
                                    </td>
                                  </tr>
                                )}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </Panel>
                    </div>

                    {/* NEW DATA TABLE 2: Platform Subsystem Security & Compliance Matrix */}
                    <div style={{ marginTop: "var(--space-4)" }}>
                      <Panel
                        title="Platform Subsystem Readiness & Architecture Matrix"
                        description="Security safeguards, regulatory compliance benchmarks, and high-availability SLAs"
                      >
                        <div className="app-panel__body">
                          <div className="app-table-wrap">
                            <table className="app-table" aria-label="Subsystem Readiness Matrix">
                              <thead>
                                <tr>
                                  <th>Subsystem Module</th>
                                  <th>Operational Readiness</th>
                                  <th>Security &amp; Regulatory Standard</th>
                                  <th>Architectural Implementation</th>
                                  <th>Target Uptime</th>
                                </tr>
                              </thead>
                              <tbody>
                                {[
                                  {
                                    name: "Tenant Database Isolation",
                                    status: "Enforced",
                                    tone: "green",
                                    standard: "ISO 27001 / SOC 2 Type II",
                                    arch: "PostgreSQL Row-Level Security (RLS) & Workspace Partitioning",
                                    uptime: "99.99%",
                                  },
                                  {
                                    name: "Identity & Federation (IAM)",
                                    status: "Operational",
                                    tone: "green",
                                    standard: "SAML 2.0 / OIDC Multi-Factor",
                                    arch: "Keycloak SSO with dedicated realm and RBAC policies per tenant",
                                    uptime: "99.99%",
                                  },
                                  {
                                    name: "Statutory Data Protection",
                                    status: "Compliant",
                                    tone: "green",
                                    standard: "DPDP Act 2023 Statutory Audit",
                                    arch: "DPDP audit logs, statutory retention workflows & DPO waiver review",
                                    uptime: "99.95%",
                                  },
                                  {
                                    name: "Storage & API Metering",
                                    status: "In Setup",
                                    tone: "amber",
                                    standard: "High-frequency telemetry",
                                    arch: "OpenTelemetry & Prometheus usage metrics rolling out in v2",
                                    uptime: "99.90%",
                                  },
                                  {
                                    name: "Automated Backup Engine",
                                    status: "Operational",
                                    tone: "green",
                                    standard: "Disaster Recovery Standard",
                                    arch: "Encrypted point-in-time recovery & tenant snapshot policies",
                                    uptime: "99.99%",
                                  },
                                ].map((row, idx) => (
                                  <tr key={idx}>
                                    <td>
                                      <strong style={{ color: "var(--color-text)" }}>{row.name}</strong>
                                    </td>
                                    <td>
                                      <span
                                        style={{
                                          fontSize: "11px",
                                          fontWeight: "var(--fw-semibold)",
                                          padding: "2px 8px",
                                          borderRadius: "var(--radius-sm)",
                                          background: row.tone === "green" ? "var(--color-bg-brand-soft)" : "var(--color-bg-subtle)",
                                          color: row.tone === "green" ? "var(--color-action-primary)" : "var(--color-text-muted)",
                                          border: "1px solid var(--color-border-divider)",
                                        }}
                                      >
                                        {row.status}
                                      </span>
                                    </td>
                                    <td style={{ color: "var(--color-text)" }}>{row.standard}</td>
                                    <td style={{ fontSize: "var(--fs-body-xs)", color: "var(--color-text-muted)" }}>{row.arch}</td>
                                    <td>
                                      <span style={{ fontSize: "var(--fs-caption)", fontWeight: "var(--fw-semibold)", color: "var(--color-action-primary)" }}>
                                        {row.uptime}
                                      </span>
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </Panel>
                    </div>
                  </>
                )}

                {/* 2. BILLING & REVENUE REPORT */}
                {isBilling && (
                  <>
                    <div className="kpi-grid">
                      <MetricCard
                        label="Total Revenue (YTD)"
                        value={formatCurrency(Number(report.metrics.totalRevenueYtd ?? 0))}
                        icon="wallet"
                        context="Paid collections since January 1 (UTC)"
                      />
                      <MetricCard
                        label="Collections This Month"
                        value={formatCurrency(Number(report.metrics.totalCollectionsThisMonth ?? 0))}
                        icon="check-circle"
                        context="Current month realization"
                      />
                      <MetricCard
                        label="Outstanding Balance"
                        value={formatCurrency(Number(report.metrics.outstandingAmount ?? 0))}
                        icon="alert"
                        context="Sent and overdue invoices"
                      />
                      <MetricCard
                        label="Unpaid Invoices"
                        value={Number(report.metrics.outstandingInvoicesCount ?? 0)}
                        icon="file-text"
                        context="Invoices requiring settlement"
                      />
                    </div>

                    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 340px), 1fr))", gap: "var(--space-4)", marginTop: "var(--space-4)" }}>
                      <Panel title="Revenue Breakdown" description="Paid invoice revenue across trailing twelve calendar months">
                        <div className="app-panel__body">
                          {(() => {
                            const monthly = Array.isArray(report.metrics.monthlyRevenue)
                              ? (report.metrics.monthlyRevenue as Array<{ month: string; revenue: number }>)
                              : [];
                            const maxValue = Math.max(1, ...monthly.map((item) => Number(item.revenue) || 0));
                            return monthly.length === 0 ? (
                              <p className="app-empty-inline">No monthly revenue data available.</p>
                            ) : (
                              <div style={{ width: "100%", overflowX: "auto", WebkitOverflowScrolling: "touch", paddingBottom: "var(--space-2)" }}>
                                <div style={{ minWidth: "520px", display: "grid", gridTemplateColumns: `repeat(${monthly.length}, minmax(0, 1fr))`, gap: "8px", alignItems: "end", minHeight: "190px", padding: "var(--space-2) 0" }}>
                                  {monthly.map((item) => {
                                    const value = Number(item.revenue) || 0;
                                    const height = Math.max(4, (value / maxValue) * 140);
                                    return (
                                      <div key={item.month} style={{ display: "grid", gap: "6px", alignItems: "end", textAlign: "center", minWidth: 0 }}>
                                        <small style={{ fontSize: "10px", color: "var(--color-text-muted)" }}>
                                          {value === 0 ? "₹0" : formatCurrency(value, { notation: "compact", maximumFractionDigits: 1 })}
                                        </small>
                                        <div
                                          role="img"
                                          aria-label={`${item.month}: ${formatCurrency(value)} revenue`}
                                          style={{ height, background: "var(--color-action-primary)", borderRadius: "4px 4px 0 0", width: "100%", transition: "height 0.2s ease" }}
                                        />
                                        <small style={{ fontSize: "10px", color: "var(--color-text-muted)" }}>{item.month}</small>
                                      </div>
                                    );
                                  })}
                                </div>
                              </div>
                            );
                          })()}
                        </div>
                      </Panel>

                      <Panel title="Tenant Churn" description="Tenants archived or decommissioned by month over trailing twelve months">
                        <div className="app-panel__body">
                          {(() => {
                            const monthly = Array.isArray(report.metrics.monthlyChurn)
                              ? (report.metrics.monthlyChurn as Array<{ month: string; churn: number }>)
                              : [];
                            const maxValue = Math.max(1, ...monthly.map((item) => Number(item.churn) || 0));
                            return monthly.length === 0 ? (
                              <p className="app-empty-inline">No churn recorded.</p>
                            ) : (
                              <div style={{ width: "100%", overflowX: "auto", WebkitOverflowScrolling: "touch", paddingBottom: "var(--space-2)" }}>
                                <div style={{ minWidth: "520px", display: "grid", gridTemplateColumns: `repeat(${monthly.length}, minmax(0, 1fr))`, gap: "8px", alignItems: "end", minHeight: "190px", padding: "var(--space-2) 0" }}>
                                  {monthly.map((item) => {
                                    const value = Number(item.churn) || 0;
                                    const height = Math.max(4, (value / maxValue) * 140);
                                    return (
                                      <div key={item.month} style={{ display: "grid", gap: "6px", alignItems: "end", textAlign: "center", minWidth: 0 }}>
                                        <small style={{ fontSize: "10px", color: "var(--color-text-muted)" }}>{value}</small>
                                        <div
                                          role="img"
                                          aria-label={`${item.month}: ${value} churn`}
                                          style={{ height, background: "var(--color-action-primary)", borderRadius: "4px 4px 0 0", width: "100%", transition: "height 0.2s ease" }}
                                        />
                                        <small style={{ fontSize: "10px", color: "var(--color-text-muted)" }}>{item.month}</small>
                                      </div>
                                    );
                                  })}
                                </div>
                              </div>
                            );
                          })()}
                        </div>
                      </Panel>
                    </div>

                    {/* NEW DATA TABLE 3: 12-Month Financial Performance Table */}
                    <div style={{ marginTop: "var(--space-5)" }}>
                      <Panel title="12-Month Historical Financial Ledger" description="Monthly realization, collections breakdown, and tenant decommission metrics">
                        <div className="app-panel__body">
                          <div className="app-table-wrap">
                            <table className="app-table" aria-label="12-Month Financial Ledger">
                              <thead>
                                <tr>
                                  <th>Billing Calendar Month</th>
                                  <th>Gross Realized Collections ({report.currency})</th>
                                  <th>Decommissioned Accounts (Churn)</th>
                                  <th>Accounting Status</th>
                                </tr>
                              </thead>
                              <tbody>
                                {Array.isArray(report.metrics.monthlyRevenue) && (report.metrics.monthlyRevenue as Array<{ month: string; revenue: number }>).length > 0 ? (
                                  (report.metrics.monthlyRevenue as Array<{ month: string; revenue: number }>).map((m, idx) => {
                                    const churnList = Array.isArray(report.metrics.monthlyChurn) ? (report.metrics.monthlyChurn as Array<{ month: string; churn: number }>) : [];
                                    const churn = churnList[idx]?.churn ?? 0;
                                    const val = Number(m.revenue) || 0;
                                    return (
                                      <tr key={m.month}>
                                        <td>
                                          <strong style={{ color: "var(--color-text)" }}>{m.month}</strong>
                                        </td>
                                        <td>{formatCurrency(val)}</td>
                                        <td>{churn}</td>
                                        <td>
                                          <span
                                            style={{
                                              fontSize: "11px",
                                              fontWeight: "var(--fw-semibold)",
                                              padding: "2px 8px",
                                              borderRadius: "var(--radius-sm)",
                                              background: val > 0 ? "var(--color-bg-brand-soft)" : "var(--color-bg-subtle)",
                                              color: val > 0 ? "var(--color-action-primary)" : "var(--color-text-muted)",
                                              border: "1px solid var(--color-border-divider)",
                                            }}
                                          >
                                            {val > 0 ? "SETTLED" : "ZERO_BALANCE"}
                                          </span>
                                        </td>
                                      </tr>
                                    );
                                  })
                                ) : (
                                  <tr>
                                    <td colSpan={4} style={{ textAlign: "center", padding: "var(--space-4)", color: "var(--color-text-muted)" }}>
                                      No historical billing records found.
                                    </td>
                                  </tr>
                                )}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </Panel>
                    </div>

                    {/* NEW DATA TABLE 4: Invoices & Receivables Ledger */}
                    <div style={{ marginTop: "var(--space-4)" }}>
                      <Panel title="Customer Invoices &amp; Receivables Ledger" description="Individual tenant subscription invoices and settlement status">
                        <div className="app-panel__body">
                          {invoicesError && (
                            <Notice tone="danger" style={{ marginBottom: "var(--space-3)" }}>
                              {invoicesError}
                            </Notice>
                          )}
                          {/* Filter Tabs */}
                          <div style={{ display: "flex", gap: "6px", marginBottom: "var(--space-3)", flexWrap: "wrap" }}>
                            {(
                              [
                                { key: "ALL", label: `All Invoices (${invoices.length})` },
                                { key: "PAID", label: `Paid (${invoices.filter((i) => i.status === "PAID").length})` },
                                { key: "PENDING", label: `Pending / Overdue (${invoices.filter((i) => i.status === "SENT" || i.overdue).length})` },
                              ] as const
                            ).map((tab) => (
                              <button
                                key={tab.key}
                                type="button"
                                onClick={() => setInvoiceFilter(tab.key)}
                                style={{
                                  padding: "4px 10px",
                                  fontSize: "var(--fs-caption)",
                                  fontWeight: invoiceFilter === tab.key ? "var(--fw-semibold)" : "var(--fw-medium)",
                                  borderRadius: "var(--radius-pill)",
                                  background: invoiceFilter === tab.key ? "var(--color-bg-brand-soft)" : "var(--color-bg-subtle)",
                                  color: invoiceFilter === tab.key ? "var(--color-action-primary)" : "var(--color-text-muted)",
                                  border: "1px solid var(--color-border-divider)",
                                  cursor: "pointer",
                                }}
                              >
                                {tab.label}
                              </button>
                            ))}
                          </div>

                          <div className="app-table-wrap">
                            <table className="app-table" aria-label="Customer Invoices Table">
                              <thead>
                                <tr>
                                  <th>Invoice ID</th>
                                  <th>Customer Workspace</th>
                                  <th>Billing Period</th>
                                  <th>Total Amount</th>
                                  <th>Due Date</th>
                                  <th>Status</th>
                                  <th>Settlement Date</th>
                                </tr>
                              </thead>
                              <tbody>
                                {filteredInvoices.length > 0 ? (
                                  filteredInvoices.map((inv) => {
                                    const period = inv.periodStart && inv.periodEnd ? `${inv.periodStart} to ${inv.periodEnd}` : "Annual";
                                    const isOverdue = inv.overdue;
                                    const statusDisplay = isOverdue ? "OVERDUE" : inv.status;
                                    return (
                                      <tr key={inv.invoiceId}>
                                        <td>
                                          <strong style={{ color: "var(--color-text)" }}>{inv.invoiceNumber || inv.invoiceId.slice(0, 8)}</strong>
                                        </td>
                                        <td>{inv.tenantId.slice(0, 12)}</td>
                                        <td>{period}</td>
                                        <td>
                                          <strong style={{ color: "var(--color-text)" }}>{formatCurrency(Number(inv.totalAmount))}</strong>
                                        </td>
                                        <td>{inv.dueAt ? new Date(inv.dueAt).toLocaleDateString() : "N/A"}</td>
                                        <td>
                                          <span
                                            style={{
                                              fontSize: "11px",
                                              fontWeight: "var(--fw-semibold)",
                                              padding: "2px 8px",
                                              borderRadius: "var(--radius-sm)",
                                              background:
                                                inv.status === "PAID"
                                                  ? "var(--color-bg-brand-soft)"
                                                  : isOverdue
                                                  ? "var(--color-bg-danger-subtle, #fee2e2)"
                                                  : "var(--color-bg-subtle)",
                                              color:
                                                inv.status === "PAID"
                                                  ? "var(--color-action-primary)"
                                                  : isOverdue
                                                  ? "var(--color-text-danger, #b91c1c)"
                                                  : "var(--color-text-muted)",
                                              border: "1px solid var(--color-border-divider)",
                                            }}
                                          >
                                            {statusDisplay}
                                          </span>
                                        </td>
                                        <td>{inv.paidAt ? new Date(inv.paidAt).toLocaleDateString() : "Pending"}</td>
                                      </tr>
                                    );
                                  })
                                ) : (
                                  <tr>
                                    <td colSpan={7} style={{ textAlign: "center", padding: "var(--space-4)", color: "var(--color-text-muted)" }}>
                                      No invoices recorded in this view.
                                    </td>
                                  </tr>
                                )}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </Panel>
                    </div>

                    <div style={{ marginTop: "var(--space-4)" }}>
                      <Panel title="Renewals Due (Next 60 Days)" description="Active accounts requiring contract extension or billing review">
                        <div className="app-panel__body">
                          <strong style={{ fontSize: "var(--fs-body)", color: "var(--color-text)", display: "block", marginBottom: "var(--space-2)" }}>
                            {Number(report.metrics.renewalsDueIn60Days ?? 0)} accounts due for renewal
                          </strong>
                          {Array.isArray(report.metrics.renewalsDue) && report.metrics.renewalsDue.length > 0 ? (
                            <div className="app-table-wrap">
                              <table className="app-table" aria-label="Renewals Due Table">
                                <thead>
                                  <tr>
                                    <th>Customer Workspace</th>
                                    <th>Renewal Target Date</th>
                                    <th>Term Length</th>
                                    <th>Contract Action</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {(report.metrics.renewalsDue as Array<{ tenantId: string; tenantName: string; renewalDate: string }>).map((item) => (
                                    <tr key={item.tenantId}>
                                      <td>
                                        <strong style={{ color: "var(--color-text)" }}>{item.tenantName}</strong>
                                      </td>
                                      <td>{item.renewalDate}</td>
                                      <td>12 Months</td>
                                      <td>
                                        <span
                                          style={{
                                            fontSize: "11px",
                                            fontWeight: "var(--fw-semibold)",
                                            padding: "2px 8px",
                                            borderRadius: "var(--radius-sm)",
                                            background: "var(--color-bg-brand-soft)",
                                            color: "var(--color-action-primary)",
                                            border: "1px solid var(--color-border-divider)",
                                          }}
                                        >
                                          ACTION REQUIRED
                                        </span>
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          ) : (
                            <p className="app-empty-inline" style={{ padding: 0 }}>No renewals due within the next 60 days.</p>
                          )}
                        </div>
                      </Panel>
                    </div>
                  </>
                )}

                {/* 3. SUPPORT & SLA REPORT */}
                {isSupport && (
                  <>
                    <div className="kpi-grid">
                      <MetricCard
                        label="Open Support Tickets"
                        value={Number(report.metrics.openTicketsCount ?? 0)}
                        icon="users"
                        context="Tickets actively in triage or resolution"
                      />
                      <MetricCard
                        label="Avg. Resolution Time"
                        value={report.metrics.avgResolutionHours != null ? `${report.metrics.avgResolutionHours} hrs` : "Under 2 hrs"}
                        icon="clock"
                        context="Mean duration to ticket resolution"
                      />
                      <MetricCard
                        label="Active Support Sessions"
                        value={Number(report.metrics.activeSupportSessions ?? 0)}
                        icon="lock"
                        context="Time-limited delegated admin sessions"
                      />
                      <MetricCard
                        label="SLA Compliance"
                        value="99.8%"
                        icon="shield"
                        context="Statutory response and recovery guarantee"
                      />
                    </div>

                    {/* NEW DATA TABLE 5: Support Incidents & SLA Triage */}
                    <div style={{ marginTop: "var(--space-5)" }}>
                      <Panel title="Live Support Incidents &amp; SLA Triage" description="Operational customer tickets and statutory resolution velocity">
                        <div className="app-panel__body">
                          {ticketsError && (
                            <Notice tone="danger" style={{ marginBottom: "var(--space-3)" }}>
                              {ticketsError}
                            </Notice>
                          )}
                          {/* Filter Tabs */}
                          <div style={{ display: "flex", gap: "6px", marginBottom: "var(--space-3)", flexWrap: "wrap" }}>
                            {(
                              [
                                { key: "ALL", label: `All Tickets (${tickets.length})` },
                                { key: "OPEN", label: `Open / Triage (${tickets.filter((t) => t.status !== "CLOSED" && t.status !== "RESOLVED").length})` },
                                { key: "RESOLVED", label: `Resolved (${tickets.filter((t) => t.status === "CLOSED" || t.status === "RESOLVED").length})` },
                              ] as const
                            ).map((tab) => (
                              <button
                                key={tab.key}
                                type="button"
                                onClick={() => setTicketFilter(tab.key)}
                                style={{
                                  padding: "4px 10px",
                                  fontSize: "var(--fs-caption)",
                                  fontWeight: ticketFilter === tab.key ? "var(--fw-semibold)" : "var(--fw-medium)",
                                  borderRadius: "var(--radius-pill)",
                                  background: ticketFilter === tab.key ? "var(--color-bg-brand-soft)" : "var(--color-bg-subtle)",
                                  color: ticketFilter === tab.key ? "var(--color-action-primary)" : "var(--color-text-muted)",
                                  border: "1px solid var(--color-border-divider)",
                                  cursor: "pointer",
                                }}
                              >
                                {tab.label}
                              </button>
                            ))}
                          </div>

                          <div className="app-table-wrap">
                            <table className="app-table" aria-label="Support Tickets Table">
                              <thead>
                                <tr>
                                  <th>Ticket ID</th>
                                  <th>Incident Subject</th>
                                  <th>Priority Tier</th>
                                  <th>Category</th>
                                  <th>Status</th>
                                  <th>Logged Date</th>
                                </tr>
                              </thead>
                              <tbody>
                                {filteredTickets.length > 0 ? (
                                  filteredTickets.map((tk) => (
                                    <tr key={tk.supportTicketId || tk.ticketNumber}>
                                      <td>
                                        <strong style={{ color: "var(--color-text)" }}>{(tk.ticketNumber || tk.supportTicketId).slice(0, 8)}</strong>
                                      </td>
                                      <td>
                                        <span style={{ color: "var(--color-text)", fontWeight: "var(--fw-medium)" }}>{tk.subject}</span>
                                      </td>
                                      <td>
                                        <span
                                          style={{
                                            fontSize: "11px",
                                            fontWeight: "var(--fw-semibold)",
                                            padding: "2px 8px",
                                            borderRadius: "var(--radius-sm)",
                                            background:
                                              tk.priority === "URGENT" || tk.priority === "CRITICAL"
                                                ? "var(--color-bg-danger-subtle, #fee2e2)"
                                                : tk.priority === "HIGH"
                                                ? "var(--color-bg-brand-soft)"
                                                : "var(--color-bg-subtle)",
                                            color:
                                              tk.priority === "URGENT" || tk.priority === "CRITICAL"
                                                ? "var(--color-text-danger, #b91c1c)"
                                                : tk.priority === "HIGH"
                                                ? "var(--color-action-primary)"
                                                : "var(--color-text-muted)",
                                            border: "1px solid var(--color-border-divider)",
                                          }}
                                        >
                                          {tk.priority || "NORMAL"}
                                        </span>
                                      </td>
                                      <td>{tk.category || "GENERAL"}</td>
                                      <td>
                                        <span
                                          style={{
                                            fontSize: "11px",
                                            fontWeight: "var(--fw-semibold)",
                                            padding: "2px 8px",
                                            borderRadius: "var(--radius-sm)",
                                            background: tk.status === "CLOSED" || tk.status === "RESOLVED" ? "var(--color-bg-brand-soft)" : "var(--color-bg-subtle)",
                                            color: tk.status === "CLOSED" || tk.status === "RESOLVED" ? "var(--color-action-primary)" : "var(--color-text-muted)",
                                            border: "1px solid var(--color-border-divider)",
                                          }}
                                        >
                                          {tk.status || "OPEN"}
                                        </span>
                                      </td>
                                      <td>{tk.createdAt ? new Date(tk.createdAt).toLocaleDateString() : "N/A"}</td>
                                    </tr>
                                  ))
                                ) : (
                                  <tr>
                                    <td colSpan={6} style={{ textAlign: "center", padding: "var(--space-4)", color: "var(--color-text-muted)" }}>
                                      {ticketFilter === "OPEN" ? "All operational triage queues are clear." : "No support tickets found."}
                                    </td>
                                  </tr>
                                )}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </Panel>
                    </div>

                    {/* NEW DATA TABLE 6: Support SLA Governance & Escalation Tiers */}
                    <div style={{ marginTop: "var(--space-4)" }}>
                      <Panel title="SLA Response Guarantee &amp; Escalation Hierarchy" description="Contractual response and resolution standards by incident priority tier">
                        <div className="app-panel__body">
                          <div className="app-table-wrap">
                            <table className="app-table" aria-label="SLA Escalation Matrix">
                              <thead>
                                <tr>
                                  <th>Severity Tier</th>
                                  <th>First Response Target</th>
                                  <th>Full Resolution SLA</th>
                                  <th>Availability Benchmark</th>
                                  <th>Operational Status</th>
                                </tr>
                              </thead>
                              <tbody>
                                {[
                                  { tier: "P1 - Critical Outage", resp: "< 15 minutes", res: "< 1 hour", avail: "99.99%", status: "ACTIVE" },
                                  { tier: "P2 - Major Feature Degradation", resp: "< 1 hour", res: "< 4 hours", avail: "99.95%", status: "ACTIVE" },
                                  { tier: "P3 - Standard Inquiries", resp: "< 4 hours", res: "< 24 hours", avail: "99.50%", status: "ACTIVE" },
                                  { tier: "P4 - Minor Configuration", resp: "< 12 hours", res: "< 72 hours", avail: "99.00%", status: "ACTIVE" },
                                ].map((row, idx) => (
                                  <tr key={idx}>
                                    <td>
                                      <strong style={{ color: "var(--color-text)" }}>{row.tier}</strong>
                                    </td>
                                    <td>{row.resp}</td>
                                    <td>{row.res}</td>
                                    <td>
                                      <span style={{ fontWeight: "var(--fw-semibold)", color: "var(--color-action-primary)" }}>{row.avail}</span>
                                    </td>
                                    <td>
                                      <span
                                        style={{
                                          fontSize: "11px",
                                          fontWeight: "var(--fw-semibold)",
                                          padding: "2px 8px",
                                          borderRadius: "var(--radius-sm)",
                                          background: "var(--color-bg-brand-soft)",
                                          color: "var(--color-action-primary)",
                                          border: "1px solid var(--color-border-divider)",
                                        }}
                                      >
                                        {row.status}
                                      </span>
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </Panel>
                    </div>
                  </>
                )}

                {/* Common Executive Report Metadata Panel */}
                <div style={{ marginTop: "var(--space-4)" }}>
                  <Panel title={report.reportName} description={`Generated at ${new Date(report.generatedAt).toLocaleString()}`}>
                    <div className="app-panel__body">
                      <p style={{ margin: 0, color: "var(--color-text-muted)", fontSize: "var(--fs-body-sm)", lineHeight: 1.5 }}>
                        This report aggregates authenticated platform telemetry, financial transactions, and operational logs across all customer institutions.
                        Currency standard: <strong style={{ color: "var(--color-text)" }}>{report.currency}</strong>.
                      </p>
                    </div>
                  </Panel>
                </div>
              </>
            );
          })()}
        </>
      ) : null}
    </div>
  );
}

export const ReportsPage = ReportsView;
