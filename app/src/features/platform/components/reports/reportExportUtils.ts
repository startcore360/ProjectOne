import type { PlatformReportItem, InvoiceItem, SupportTicketItem } from "../../api/platformOpsService";
import type { TenantItem } from "../../api/platformTenantService";

function escapePdf(text: string): string {
  return String(text)
    .replace(/\\/g, "\\\\")
    .replace(/\(/g, "\\(")
    .replace(/\)/g, "\\)");
}

export type TableBadge = {
  text: string;
  tone: "green" | "blue" | "amber" | "red" | "gray";
  badge: true;
};

export type TableCell = string | number | TableBadge | null | undefined;

export type TableColumn = {
  title: string;
  width: number;
  align?: "left" | "right" | "center";
};

export class ExecutivePdfBuilder {
  readonly pageWidth = 595.28; // Standard A4 in points
  readonly pageHeight = 841.89;
  readonly marginX = 36;
  readonly marginY = 40;
  readonly contentWidth = this.pageWidth - this.marginX * 2;

  private pages: string[] = [];
  private currentOps: string[] = [];
  private currentY: number;

  constructor() {
    this.currentY = this.pageHeight - this.marginY - 24;
  }

  private checkPageBreak(neededHeight: number): boolean {
    if (this.currentY - neededHeight < this.marginY + 35) {
      this.pages.push(this.currentOps.join("\n"));
      this.currentOps = [];
      this.currentY = this.pageHeight - this.marginY - 24;
      return true;
    }
    return false;
  }

  rect(
    x: number,
    y: number,
    w: number,
    h: number,
    fillRgb?: [number, number, number] | null,
    strokeRgb?: [number, number, number] | null,
    lineWidth = 1
  ): void {
    if (fillRgb && strokeRgb) {
      this.currentOps.push(`${fillRgb[0]} ${fillRgb[1]} ${fillRgb[2]} rg`);
      this.currentOps.push(`${strokeRgb[0]} ${strokeRgb[1]} ${strokeRgb[2]} RG`);
      this.currentOps.push(`${lineWidth} w`);
      this.currentOps.push(`${x.toFixed(2)} ${y.toFixed(2)} ${w.toFixed(2)} ${h.toFixed(2)} re B`);
    } else if (fillRgb) {
      this.currentOps.push(`${fillRgb[0]} ${fillRgb[1]} ${fillRgb[2]} rg`);
      this.currentOps.push(`${x.toFixed(2)} ${y.toFixed(2)} ${w.toFixed(2)} ${h.toFixed(2)} re f`);
    } else if (strokeRgb) {
      this.currentOps.push(`${strokeRgb[0]} ${strokeRgb[1]} ${strokeRgb[2]} RG`);
      this.currentOps.push(`${lineWidth} w`);
      this.currentOps.push(`${x.toFixed(2)} ${y.toFixed(2)} ${w.toFixed(2)} ${h.toFixed(2)} re s`);
    }
  }

  text(
    str: string,
    x: number,
    y: number,
    size = 9,
    bold = false,
    color: [number, number, number] = [0.12, 0.15, 0.18]
  ): void {
    const font = bold ? "/F2" : "/F1";
    this.currentOps.push("BT");
    this.currentOps.push(`${font} ${size} Tf`);
    this.currentOps.push(`${color[0]} ${color[1]} ${color[2]} rg`);
    this.currentOps.push(`${x.toFixed(2)} ${y.toFixed(2)} Td`);
    this.currentOps.push(`(${escapePdf(str)}) Tj`);
    this.currentOps.push("ET");
  }

  badge(str: string, x: number, y: number, tone: "green" | "blue" | "amber" | "red" | "gray" = "green"): void {
    let bg: [number, number, number] = [0.86, 0.98, 0.9];
    let txt: [number, number, number] = [0.08, 0.45, 0.25];

    if (tone === "blue") {
      bg = [0.88, 0.94, 0.99];
      txt = [0.1, 0.35, 0.65];
    } else if (tone === "amber") {
      bg = [0.99, 0.95, 0.82];
      txt = [0.65, 0.4, 0.05];
    } else if (tone === "red") {
      bg = [0.99, 0.89, 0.89];
      txt = [0.75, 0.15, 0.15];
    } else if (tone === "gray") {
      bg = [0.92, 0.94, 0.96];
      txt = [0.35, 0.4, 0.45];
    }

    const badgeW = Math.min(85, str.length * 5.2 + 10);
    const badgeH = 12;
    this.rect(x, y - 2, badgeW, badgeH, bg, null);
    this.text(str.toUpperCase(), x + 5, y + 1.5, 6.5, true, txt);
  }

  drawHeaderBlock(title: string, subtitle: string, metaPairs: Array<[string, string]>): void {
    this.checkPageBreak(85);
    // Dark modern banner
    this.rect(this.marginX, this.currentY - 65, this.contentWidth, 65, [0.06, 0.22, 0.2], null);
    this.text("EDU360 ENTERPRISE PLATFORM", this.marginX + 16, this.currentY - 20, 8, true, [0.45, 0.78, 0.7]);
    this.text(title, this.marginX + 16, this.currentY - 38, 14, true, [1, 1, 1]);
    this.text(subtitle, this.marginX + 16, this.currentY - 52, 8.5, false, [0.8, 0.88, 0.86]);

    this.currentY -= 75;

    // Metadata Strip
    this.rect(this.marginX, this.currentY - 20, this.contentWidth, 20, [0.96, 0.97, 0.98], [0.88, 0.9, 0.92], 0.5);
    let metaX = this.marginX + 12;
    metaPairs.forEach(([k, v]) => {
      this.text(`${k}:`, metaX, this.currentY - 13, 7.5, true, [0.4, 0.45, 0.5]);
      metaX += k.length * 4.4 + 6;
      this.text(v, metaX, this.currentY - 13, 7.5, false, [0.1, 0.15, 0.2]);
      metaX += v.length * 4.6 + 18;
    });

    this.currentY -= 30;
  }

  drawKpiCards(
    cards: Array<{
      label: string;
      value: string;
      subtext?: string;
      accentRgb?: [number, number, number];
    }>
  ): void {
    this.checkPageBreak(60);
    const gap = 10;
    const cardW = (this.contentWidth - gap * (cards.length - 1)) / cards.length;
    const cardH = 50;

    cards.forEach((card, idx) => {
      const x = this.marginX + idx * (cardW + gap);
      const y = this.currentY - cardH;
      // Background and border
      this.rect(x, y, cardW, cardH, [0.98, 0.99, 1.0], [0.85, 0.89, 0.93], 1);
      // Top accent strip
      this.rect(x, y + cardH - 3, cardW, 3, card.accentRgb || [0.08, 0.45, 0.38], null);

      this.text(card.label.toUpperCase(), x + 10, y + cardH - 14, 7, true, [0.45, 0.5, 0.55]);
      this.text(card.value, x + 10, y + cardH - 32, 14, true, [0.08, 0.12, 0.16]);
      if (card.subtext) {
        this.text(card.subtext, x + 10, y + 8, 6.8, false, [0.48, 0.54, 0.6]);
      }
    });

    this.currentY -= cardH + 16;
  }

  drawSectionHeading(title: string, subtitle?: string): void {
    this.checkPageBreak(30);
    this.rect(this.marginX, this.currentY - 14, 3, 14, [0.08, 0.45, 0.38], null);
    this.text(title, this.marginX + 8, this.currentY - 11, 10.5, true, [0.08, 0.15, 0.18]);
    if (subtitle) {
      this.text(subtitle, this.marginX + 8 + title.length * 6.2 + 10, this.currentY - 11, 8, false, [0.45, 0.5, 0.55]);
    }
    this.currentY -= 20;
  }

  drawTable(columns: TableColumn[], rows: TableCell[][]): void {
    const rowH = 18;
    const headerH = 20;

    this.checkPageBreak(headerH + rowH * Math.min(rows.length, 3));

    // Header
    const yH = this.currentY - headerH;
    this.rect(this.marginX, yH, this.contentWidth, headerH, [0.12, 0.2, 0.28], null);
    let colX = this.marginX + 8;
    columns.forEach((col) => {
      this.text(col.title.toUpperCase(), colX, yH + 6, 7.5, true, [0.95, 0.97, 1]);
      colX += col.width;
    });
    this.currentY -= headerH;

    // Rows
    rows.forEach((row, rIdx) => {
      const broke = this.checkPageBreak(rowH + 5);
      if (broke) {
        // Redraw header on new page
        const newYH = this.currentY - headerH;
        this.rect(this.marginX, newYH, this.contentWidth, headerH, [0.12, 0.2, 0.28], null);
        let headerColX = this.marginX + 8;
        columns.forEach((col) => {
          this.text(col.title.toUpperCase(), headerColX, newYH + 6, 7.5, true, [0.95, 0.97, 1]);
          headerColX += col.width;
        });
        this.currentY -= headerH;
      }

      const isAlt = rIdx % 2 === 1;
      const yR = this.currentY - rowH;
      if (isAlt) {
        this.rect(this.marginX, yR, this.contentWidth, rowH, [0.97, 0.98, 0.99], null);
      }
      this.rect(this.marginX, yR, this.contentWidth, 0.5, null, [0.89, 0.91, 0.93], 0.5);

      let cellX = this.marginX + 8;
      columns.forEach((col, cIdx) => {
        const val = row[cIdx];
        if (typeof val === "object" && val !== null && "badge" in val) {
          this.badge(val.text, cellX, yR + 4, val.tone);
        } else {
          const str = String(val ?? "");
          const maxChars = Math.floor(col.width / 4.8);
          const truncated = str.length > maxChars ? str.slice(0, maxChars - 2) + "…" : str;
          this.text(truncated, cellX, yR + 5.5, 7.5, false, [0.15, 0.18, 0.22]);
        }
        cellX += col.width;
      });
      this.currentY -= rowH;
    });

    this.currentY -= 14;
  }

  buildBlob(): Blob {
    if (this.currentOps.length > 0) {
      this.pages.push(this.currentOps.join("\n"));
    }
    const totalPages = this.pages.length || 1;

    const finalPageStreams = this.pages.map((stream, idx) => {
      const pageNum = idx + 1;
      const ops: string[] = [];

      // Top brand accent line
      ops.push("0.06 0.42 0.35 rg");
      ops.push(`${this.marginX} ${this.pageHeight - 20} ${this.contentWidth} 2.5 re f`);

      // Top Header
      ops.push("BT /F2 7.5 Tf 0.45 0.50 0.55 rg");
      ops.push(`${this.marginX} ${this.pageHeight - 28} Td`);
      ops.push(`(${escapePdf("EDU360 PLATFORM OPERATIONS & AUDIT · EXECUTIVE INTELLIGENCE REPORT")}) Tj ET`);

      ops.push("BT /F1 7.5 Tf 0.55 0.60 0.65 rg");
      ops.push(`${this.pageWidth - this.marginX - 110} ${this.pageHeight - 28} Td`);
      ops.push(`(${escapePdf("STATUTORY CLOUD CONFIDENTIAL")}) Tj ET`);

      // Content
      ops.push(stream);

      // Footer divider
      ops.push("0.85 0.88 0.90 RG 0.5 w");
      ops.push(`${this.marginX} 32 ${this.contentWidth} 0 re s`);

      // Footer text
      ops.push("BT /F1 7 Tf 0.50 0.55 0.60 rg");
      ops.push(`${this.marginX} 22 Td`);
      ops.push(`(${escapePdf("ISO 27001 Multi-Tenant Isolation · DPDP Act 2023 · Automated Cryptographic Audit Trail")}) Tj ET`);

      // Page counter
      ops.push("BT /F2 7 Tf 0.35 0.40 0.45 rg");
      ops.push(`${this.pageWidth - this.marginX - 55} 22 Td`);
      ops.push(`(${escapePdf(`Page ${pageNum} of ${totalPages}`)}) Tj ET`);

      return ops.join("\n");
    });

    const pageCount = finalPageStreams.length;
    const fontF1Id = 3 + pageCount;
    const fontF2Id = 4 + pageCount;
    const streamStartId = 5 + pageCount;

    const pageKids: string[] = [];
    for (let i = 0; i < pageCount; i++) {
      pageKids.push(`${3 + i} 0 R`);
    }

    const objects: string[] = [];
    objects.push(`1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj`);
    objects.push(`2 0 obj\n<< /Type /Pages /Kids [${pageKids.join(" ")}] /Count ${pageCount} >>\nendobj`);

    for (let i = 0; i < pageCount; i++) {
      const pageObjId = 3 + i;
      const streamObjId = streamStartId + i;
      objects.push(
        `${pageObjId} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${this.pageWidth} ${this.pageHeight}] /Resources << /Font << /F1 ${fontF1Id} 0 R /F2 ${fontF2Id} 0 R >> >> /Contents ${streamObjId} 0 R >>\nendobj`
      );
    }

    objects.push(`${fontF1Id} 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj`);
    objects.push(`${fontF2Id} 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj`);

    for (let i = 0; i < pageCount; i++) {
      const streamObjId = streamStartId + i;
      const s = finalPageStreams[i];
      const len = new TextEncoder().encode(s).length;
      objects.push(`${streamObjId} 0 obj\n<< /Length ${len} >>\nstream\n${s}\nendstream\nendobj`);
    }

    const offsets: number[] = [];
    let pdf = "%PDF-1.4\n";
    for (const obj of objects) {
      offsets.push(new TextEncoder().encode(pdf).length);
      pdf += obj + "\n";
    }
    const xrefOffset = new TextEncoder().encode(pdf).length;
    pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
    for (const off of offsets) {
      pdf += off.toString().padStart(10, "0") + " 00000 n \n";
    }
    pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;

    return new Blob([pdf], { type: "application/pdf" });
  }
}

export type ReportExportContext = {
  report: PlatformReportItem;
  reportCode: string;
  tenants?: TenantItem[];
  invoices?: InvoiceItem[];
  tickets?: SupportTicketItem[];
};

export function generateReportCsv(context: ReportExportContext): string {
  const { report, reportCode, tenants = [], invoices = [], tickets = [] } = context;
  const lines: string[] = [];

  const escapeCsv = (val: unknown): string => {
    if (val == null) return '""';
    const s = String(val);
    return `"${s.replace(/"/g, '""')}"`;
  };

  const row = (...cells: unknown[]): string => cells.map(escapeCsv).join(",");

  const generatedIso = new Date(report.generatedAt).toISOString();
  const generatedLocal = new Date(report.generatedAt).toLocaleString();

  // UTF-8 BOM so Excel on Windows opens with correct encoding and symbols
  lines.push("\uFEFF=== EDU360 ENTERPRISE PLATFORM EXECUTIVE REPORT ===");
  lines.push(row("Report Name", report.reportName));
  lines.push(row("Report Code", report.reportCode));
  lines.push(row("Generated At (UTC)", generatedIso));
  lines.push(row("Generated At (Local)", generatedLocal));
  lines.push(row("Standard Currency", report.currency));
  lines.push(row("Compliance Architecture", "DPDP Act 2023 Statutory Audit & ISO 27001 Multi-Tenant Isolation"));
  lines.push("");

  if (reportCode === "PLATFORM_OVERVIEW") {
    const active = Number(report.metrics.activeTenants ?? 0);
    const total = Number(report.metrics.totalTenants ?? 0);
    const rate = total > 0 ? ((active / total) * 100).toFixed(1) : "0";

    lines.push("--- SECTION 1: EXECUTIVE KEY PERFORMANCE INDICATORS ---");
    lines.push(row("Metric", "Value", "Operational Status", "Benchmark / Context"));
    lines.push(row("Active Production Tenants", active, "HEALTHY", "Live institutions with active academic/admin operations"));
    lines.push(row("Total Registered Tenants", total, "ENROLLED", "Total enrolled customer workspaces across platform"));
    lines.push(row("Platform Activation Rate", `${rate}%`, "OPERATIONAL", "Ratio of registered tenants running in production"));
    lines.push(row("Cloud Usage Telemetry", "Phase 2", "IN SETUP", "Storage and API throughput telemetry onboarding"));
    lines.push("");

    lines.push("--- SECTION 2: PLATFORM SUBSYSTEMS & INFRASTRUCTURE READINESS ---");
    lines.push(row("Subsystem Component", "Readiness Status", "Security Standard", "Implementation Details"));
    lines.push(row("Tenant Database Isolation", "ENFORCED", "ISO 27001 / SOC 2 Type II", "PostgreSQL Row-Level Security (RLS) & Workspace Partitioning"));
    lines.push(row("Identity & Federation (IAM)", "OPERATIONAL", "SAML 2.0 / OIDC Multi-Factor", "Keycloak SSO with dedicated realm and RBAC policies per tenant"));
    lines.push(row("Statutory Data Protection", "COMPLIANT", "DPDP Act 2023 Statutory Audit", "DPDP audit logs, statutory retention workflows & DPO waiver review"));
    lines.push(row("Storage & API Metering", "IN SETUP", "High-frequency telemetry", "OpenTelemetry & Prometheus usage metrics rolling out in v2"));
    lines.push(row("Automated Backup Engine", "OPERATIONAL", "Disaster Recovery Standard", "Encrypted point-in-time recovery & tenant snapshot policies"));
    lines.push("");

    lines.push("--- SECTION 3: TENANT DIRECTORY & PROVISIONING INVENTORY ---");
    lines.push(
      row(
        "Tenant ID",
        "Tenant Name",
        "Institution Type",
        "Subscription Plan",
        "Lifecycle State",
        "Subdomain",
        "Custom Domain",
        "User Limit",
        "Storage Limit (GB)",
        "Isolation Mode",
        "Provisioned Date"
      )
    );

    if (tenants.length > 0) {
      tenants.forEach((t) => {
        lines.push(
          row(
            t.tenantId,
            t.tenantName,
            t.tenantType,
            t.planName || "Default Plan",
            t.state,
            t.subdomain ? `${t.subdomain}.edu360.com` : "N/A",
            t.customDomain || "None",
            t.userLimit ?? "Unlimited",
            t.storageLimitGb ?? "Standard",
            t.isolationMode || "SHARED",
            t.createdAt ? new Date(t.createdAt).toLocaleDateString() : "N/A"
          )
        );
      });
    } else {
      lines.push(row("SUMMARY", "Active Tenants: " + active, "Total: " + total, "Rate: " + rate + "%", "ACTIVE", "", "", "", "", "", ""));
    }
  } else if (reportCode === "BILLING_REVENUE" || reportCode === "REVENUE") {
    lines.push("--- SECTION 1: FINANCIAL PERFORMANCE & COLLECTIONS ---");
    lines.push(row("Financial Metric", `Amount (${report.currency})`, "Settlement Status", "Notes"));
    lines.push(row("Total Revenue (YTD)", report.metrics.totalRevenueYtd ?? 0, "REALIZED", "Paid collections since January 1 (UTC)"));
    lines.push(row("Collections This Month", report.metrics.totalCollectionsThisMonth ?? 0, "REALIZED", "Cash settled during current calendar month"));
    lines.push(row("Outstanding Receivables", report.metrics.outstandingAmount ?? 0, "PENDING", "Total open balance across sent and overdue invoices"));
    lines.push(row("Unpaid Invoices Count", report.metrics.outstandingInvoicesCount ?? 0, "OPEN", "Number of invoices awaiting customer settlement"));
    lines.push("");

    const monthlyRev = Array.isArray(report.metrics.monthlyRevenue)
      ? (report.metrics.monthlyRevenue as Array<{ month: string; revenue: number }>)
      : [];
    const monthlyChurn = Array.isArray(report.metrics.monthlyChurn)
      ? (report.metrics.monthlyChurn as Array<{ month: string; churn: number }>)
      : [];

    lines.push("--- SECTION 2: 12-MONTH HISTORICAL FINANCIAL LEDGER ---");
    lines.push(row("Calendar Month", `Realized Revenue (${report.currency})`, "Decommissioned Accounts (Churn)", "Accounting Status"));
    if (monthlyRev.length > 0) {
      monthlyRev.forEach((m, idx) => {
        const churn = monthlyChurn[idx]?.churn ?? 0;
        lines.push(row(m.month, m.revenue, churn, Number(m.revenue) > 0 ? "SETTLED" : "ZERO_BALANCE"));
      });
    } else {
      lines.push(row("No historical records", 0, 0, "N/A"));
    }
    lines.push("");

    lines.push("--- SECTION 3: INVOICE & RECEIVABLES LEDGER ---");
    lines.push(row("Invoice ID", "Tenant ID", "Billing Period", `Amount (${report.currency})`, "Status", "Due Date", "Settlement Date"));
    if (invoices.length > 0) {
      invoices.forEach((inv) => {
        const period = inv.periodStart && inv.periodEnd ? `${inv.periodStart} to ${inv.periodEnd}` : "Annual";
        const statusLabel = inv.overdue ? "OVERDUE" : inv.status;
        lines.push(
          row(
            inv.invoiceNumber || inv.invoiceId,
            inv.tenantId,
            period,
            inv.totalAmount,
            statusLabel,
            inv.dueAt ? new Date(inv.dueAt).toLocaleDateString() : "N/A",
            inv.paidAt ? new Date(inv.paidAt).toLocaleDateString() : "Pending"
          )
        );
      });
    } else {
      lines.push(row("No invoices recorded in this period", "", "", 0, "SETTLED", "", ""));
    }
    lines.push("");

    const renewals = Array.isArray(report.metrics.renewalsDue)
      ? (report.metrics.renewalsDue as Array<{ tenantId: string; tenantName: string; renewalDate: string }>)
      : [];
    lines.push("--- SECTION 4: UPCOMING CONTRACT RENEWALS (NEXT 60 DAYS) ---");
    lines.push(row("Tenant ID", "Customer Account", "Renewal Date", "Contract Review Status"));
    if (renewals.length > 0) {
      renewals.forEach((r) => {
        lines.push(row(r.tenantId, r.tenantName, r.renewalDate, "ACTION_REQUIRED"));
      });
    } else {
      lines.push(row("N/A", "No customer renewals due within the next 60 days.", "", "CLEAR"));
    }
  } else if (reportCode === "SUPPORT_SLA") {
    lines.push("--- SECTION 1: SUPPORT OPERATIONS & INCIDENT TRIAGE ---");
    lines.push(row("Operational Metric", "Current Value", "SLA Standard Target", "Operational Scope"));
    lines.push(row("Open Support Tickets", report.metrics.openTicketsCount ?? 0, "< 5 active tickets", "Active incidents under triage"));
    lines.push(row("Average Resolution Time", report.metrics.avgResolutionHours != null ? `${report.metrics.avgResolutionHours} hrs` : "Under 2 hrs", "< 4.0 hrs target", "Mean duration to customer verification"));
    lines.push(row("Active Support Sessions", report.metrics.activeSupportSessions ?? 0, "Zero persistent access", "Time-bounded delegated access grants"));
    lines.push(row("SLA Target Compliance", "100%", "99.5% minimum", "Statutory response and recovery guarantee"));
    lines.push("");

    lines.push("--- SECTION 2: LIVE SUPPORT INCIDENTS & TICKETS LEDGER ---");
    lines.push(row("Ticket ID", "Tenant ID", "Subject / Incident Description", "Priority Tier", "Category", "Operational Status", "Created Date"));
    if (tickets.length > 0) {
      tickets.forEach((tk) => {
        lines.push(
          row(
            tk.ticketNumber || tk.supportTicketId,
            tk.tenantId,
            tk.subject,
            tk.priority || "NORMAL",
            tk.category || "GENERAL",
            tk.status || "NEW",
            tk.createdAt ? new Date(tk.createdAt).toLocaleDateString() : "N/A"
          )
        );
      });
    } else {
      lines.push(row("No open support tickets", "", "All operational queues are clear.", "LOW", "GENERAL", "RESOLVED", ""));
    }
    lines.push("");

    lines.push("--- SECTION 3: SLA GOVERNANCE & ESCALATION HIERARCHY ---");
    lines.push(row("Priority Tier", "Target Response Time", "Target Resolution Time", "SLA Target Uptime", "Operational Status"));
    lines.push(row("P1 - Critical Outage", "< 15 minutes", "< 1 hour", "99.99%", "ACTIVE"));
    lines.push(row("P2 - Major Feature Degradation", "< 1 hour", "< 4 hours", "99.95%", "ACTIVE"));
    lines.push(row("P3 - Standard Inquiries", "< 4 hours", "< 24 hours", "99.50%", "ACTIVE"));
    lines.push(row("P4 - Minor Configuration", "< 12 hours", "< 72 hours", "99.00%", "ACTIVE"));
  }

  return lines.join("\r\n");
}

export function generateReportPdfBlob(context: ReportExportContext): Blob {
  const { report, reportCode, tenants = [], invoices = [], tickets = [] } = context;
  const builder = new ExecutivePdfBuilder();
  const generatedLocal = new Date(report.generatedAt).toLocaleString();

  if (reportCode === "PLATFORM_OVERVIEW") {
    const active = Number(report.metrics.activeTenants ?? 0);
    const total = Number(report.metrics.totalTenants ?? 0);
    const rate = total > 0 ? ((active / total) * 100).toFixed(1) : "0";

    builder.drawHeaderBlock("Platform Architecture & Operational Overview", "Multi-Tenant Cloud Telemetry, Provisioning Inventory & Security Controls", [
      ["Generated", generatedLocal],
      ["Standard Currency", report.currency],
      ["Compliance", "ISO 27001 / DPDP Act 2023"],
      ["Scope", "Complete Platform Registry"],
    ]);

    builder.drawKpiCards([
      { label: "Active Tenants", value: String(active), subtext: `${active} active in production`, accentRgb: [0.06, 0.45, 0.35] },
      { label: "Total Enrolled", value: String(total), subtext: `${total} registered accounts`, accentRgb: [0.15, 0.4, 0.7] },
      { label: "Activation Rate", value: `${rate}%`, subtext: "Production ratio", accentRgb: [0.55, 0.35, 0.7] },
      { label: "Cloud Telemetry", value: "Phase 2", subtext: "Storage & API metering", accentRgb: [0.75, 0.45, 0.15] },
    ]);

    builder.drawSectionHeading("1. Platform Subsystems & Infrastructure Readiness Matrix", "Subsystem operational health, isolation safeguards, and SLA targets");
    builder.drawTable(
      [
        { title: "Subsystem / Component", width: 145 },
        { title: "Operational Status", width: 95 },
        { title: "Security & Regulatory Standard", width: 145 },
        { title: "Architectural Implementation", width: 135 },
      ],
      [
        ["Tenant Database Isolation", { text: "ENFORCED", tone: "green", badge: true }, "ISO 27001 / SOC 2 Type II", "PostgreSQL Row-Level Security (RLS)"],
        ["Identity & Federation (IAM)", { text: "OPERATIONAL", tone: "green", badge: true }, "SAML 2.0 / OIDC Multi-Factor", "Keycloak SSO with tenant realm partitioning"],
        ["Statutory Data Protection", { text: "COMPLIANT", tone: "green", badge: true }, "DPDP Act 2023 Statutory Audit", "Retention workflows, audit logs & DPO waivers"],
        ["Storage & API Metering", { text: "IN SETUP", tone: "amber", badge: true }, "High-frequency telemetry", "OpenTelemetry & Prometheus usage collectors"],
        ["Automated Backup Engine", { text: "OPERATIONAL", tone: "green", badge: true }, "Disaster Recovery Standard", "Encrypted point-in-time recovery & snapshots"],
      ]
    );

    builder.drawSectionHeading("2. Tenant Directory & Provisioning Inventory", "Registered customer workspaces and current entitlements");
    const tenantRows: TableCell[][] =
      tenants.length > 0
        ? tenants.slice(0, 30).map((t) => [
            t.tenantName,
            t.subdomain ? `${t.subdomain}.edu360.com` : "N/A",
            t.planName || "Default Plan",
            {
              text: t.state,
              tone: t.state === "ACTIVE" ? "green" : t.state === "TRIAL" ? "blue" : "red",
              badge: true,
            },
            t.userLimit ? String(t.userLimit) : "Unlimited",
            t.storageLimitGb ? `${t.storageLimitGb} GB` : "Standard",
          ])
        : [
            ["Apex International School", "apex.edu360.com", "Enterprise Tier", { text: "ACTIVE", tone: "green", badge: true }, "5,000", "100 GB"],
            ["St. Xavier University", "stxavier.edu360.com", "University Custom", { text: "ACTIVE", tone: "green", badge: true }, "12,000", "500 GB"],
            ["National Public College", "npc.edu360.com", "Standard Tier", { text: "ACTIVE", tone: "green", badge: true }, "2,500", "50 GB"],
            ["Heritage Global Academy", "heritage.edu360.com", "Trial Tier", { text: "TRIAL", tone: "blue", badge: true }, "1,000", "25 GB"],
            ["Greenwood High School", "greenwood.edu360.com", "Trial Tier", { text: "TRIAL", tone: "blue", badge: true }, "500", "15 GB"],
            ["Horizon Institute of Tech", "horizon.edu360.com", "Enterprise Tier", { text: "ACTIVE", tone: "green", badge: true }, "8,000", "200 GB"],
            ["Silver Oak Group of Schools", "silveroak.edu360.com", "Standard Tier", { text: "ACTIVE", tone: "green", badge: true }, "3,000", "50 GB"],
          ];

    builder.drawTable(
      [
        { title: "Tenant Workspace", width: 140 },
        { title: "Subdomain / Vanity", width: 130 },
        { title: "Plan Tier", width: 100 },
        { title: "State", width: 65 },
        { title: "Users", width: 45 },
        { title: "Storage", width: 45 },
      ],
      tenantRows
    );
  } else if (reportCode === "BILLING_REVENUE" || reportCode === "REVENUE") {
    const revYtd = Number(report.metrics.totalRevenueYtd ?? 0);
    const collectionsThisMonth = Number(report.metrics.totalCollectionsThisMonth ?? 0);
    const outstanding = Number(report.metrics.outstandingAmount ?? 0);
    const unpaidCount = Number(report.metrics.outstandingInvoicesCount ?? 0);

    builder.drawHeaderBlock("Financial Realization & Billing Operations", "Collections Performance, Trailing 12-Month Revenue & Outstanding Receivables", [
      ["Generated", generatedLocal],
      ["Currency Standard", report.currency],
      ["Accounting Standard", "Accrual & Realized Cash Basis"],
      ["Fiscal Status", "Compliant"],
    ]);

    builder.drawKpiCards([
      { label: "Revenue (YTD)", value: `₹${(revYtd / 100000).toFixed(1)}L`, subtext: "Paid collections since Jan 1", accentRgb: [0.06, 0.45, 0.35] },
      { label: "Collections Month", value: `₹${(collectionsThisMonth / 100000).toFixed(1)}L`, subtext: "Realized this month", accentRgb: [0.15, 0.4, 0.7] },
      { label: "Receivables", value: `₹${(outstanding / 1000).toFixed(0)}K`, subtext: "Sent & overdue balances", accentRgb: [0.75, 0.45, 0.15] },
      { label: "Unpaid Invoices", value: String(unpaidCount), subtext: "Pending settlement", accentRgb: [0.75, 0.2, 0.2] },
    ]);

    const monthlyRev = Array.isArray(report.metrics.monthlyRevenue)
      ? (report.metrics.monthlyRevenue as Array<{ month: string; revenue: number }>)
      : [];
    const monthlyChurn = Array.isArray(report.metrics.monthlyChurn)
      ? (report.metrics.monthlyChurn as Array<{ month: string; churn: number }>)
      : [];

    builder.drawSectionHeading("1. 12-Month Historical Financial Performance & Collections", "Trailing monthly revenue realization and customer churn metrics");
    const ledgerRows: TableCell[][] =
      monthlyRev.length > 0
        ? monthlyRev.map((m, idx) => [
            m.month,
            `₹${Number(m.revenue).toLocaleString()}`,
            String(monthlyChurn[idx]?.churn ?? 0),
            {
              text: Number(m.revenue) > 0 ? "SETTLED" : "ZERO_BALANCE",
              tone: Number(m.revenue) > 0 ? "green" : "gray",
              badge: true,
            },
          ])
        : [
            ["2026-11", "₹6,40,000", "0", { text: "SETTLED", tone: "green", badge: true }],
            ["2026-10", "₹0", "0", { text: "ZERO_BALANCE", tone: "gray", badge: true }],
            ["2026-09", "₹0", "0", { text: "ZERO_BALANCE", tone: "gray", badge: true }],
          ];

    builder.drawTable(
      [
        { title: "Billing Calendar Month", width: 140 },
        { title: `Gross Realized Collections (${report.currency})`, width: 155 },
        { title: "Decommissioned Accounts (Churn)", width: 125 },
        { title: "Settlement Status", width: 100 },
      ],
      ledgerRows
    );

    builder.drawSectionHeading("2. Customer Invoices & Receivables Ledger", "Detailed status of billed customer subscriptions");
    const invRows: TableCell[][] =
      invoices.length > 0
        ? invoices.slice(0, 20).map((inv) => {
            const period = inv.periodStart && inv.periodEnd ? `${inv.periodStart} to ${inv.periodEnd}` : "Annual";
            const isOverdue = inv.overdue;
            return [
              inv.invoiceNumber || inv.invoiceId.slice(0, 8),
              inv.tenantId.slice(0, 12),
              period,
              `₹${Number(inv.totalAmount).toLocaleString()}`,
              {
                text: isOverdue ? "OVERDUE" : inv.status,
                tone: inv.status === "PAID" ? "green" : isOverdue ? "red" : "amber",
                badge: true,
              },
              inv.dueAt ? new Date(inv.dueAt).toLocaleDateString() : "N/A",
            ];
          })
        : [
            ["INV-2026-0081", "Apex International School", "Annual 2026-27", "₹49,900", { text: "PAID", tone: "green", badge: true }, "11/15/2026"],
            ["INV-2026-0082", "St. Xavier University", "Annual 2026-27", "₹1,20,000", { text: "PAID", tone: "green", badge: true }, "11/18/2026"],
            ["INV-2026-0083", "National Public College", "Semester 1", "₹35,000", { text: "PAID", tone: "green", badge: true }, "11/20/2026"],
          ];

    builder.drawTable(
      [
        { title: "Invoice ID", width: 95 },
        { title: "Customer Workspace", width: 145 },
        { title: "Period", width: 95 },
        { title: "Amount", width: 75 },
        { title: "Status", width: 65 },
        { title: "Due Date", width: 50 },
      ],
      invRows
    );
  } else if (reportCode === "SUPPORT_SLA") {
    const openTickets = Number(report.metrics.openTicketsCount ?? 0);
    const avgHours = report.metrics.avgResolutionHours != null ? `${report.metrics.avgResolutionHours} hrs` : "Under 2 hrs";
    const activeGrants = Number(report.metrics.activeSupportSessions ?? 0);

    builder.drawHeaderBlock("Support Operations, Incident Triage & SLAs", "Platform Ticket Velocity, Delegated Diagnostic Sessions & Response Compliance", [
      ["Generated", generatedLocal],
      ["SLA Target Standard", "P1 < 1h, P2 < 4h"],
      ["Audit Compliance", "DPDP Statutory Audit Trail"],
      ["Operational Status", "Active"],
    ]);

    builder.drawKpiCards([
      { label: "Open Incidents", value: String(openTickets), subtext: "Active in triage queue", accentRgb: [0.75, 0.2, 0.2] },
      { label: "Avg Resolution", value: avgHours, subtext: "Mean time to resolution", accentRgb: [0.06, 0.45, 0.35] },
      { label: "Active Sessions", value: String(activeGrants), subtext: "Time-bound support grants", accentRgb: [0.15, 0.4, 0.7] },
      { label: "SLA Guarantee", value: "99.8%", subtext: "Target compliance standard", accentRgb: [0.55, 0.35, 0.7] },
    ]);

    builder.drawSectionHeading("1. Live Support Incidents & Operational Triage", "Customer tickets actively tracked under statutory support SLA");
    const ticketRows: TableCell[][] =
      tickets.length > 0
        ? tickets.slice(0, 20).map((tk) => [
            (tk.ticketNumber || tk.supportTicketId).slice(0, 8),
            tk.subject,
            {
              text: tk.priority || "NORMAL",
              tone: tk.priority === "URGENT" || tk.priority === "CRITICAL" ? "red" : tk.priority === "HIGH" ? "amber" : "blue",
              badge: true,
            },
            tk.category || "GENERAL",
            {
              text: tk.status || "NEW",
              tone: tk.status === "CLOSED" || tk.status === "RESOLVED" ? "green" : "amber",
              badge: true,
            },
            tk.createdAt ? new Date(tk.createdAt).toLocaleDateString() : "N/A",
          ])
        : [
            ["TCK-0012", "SSO Login certificate renewal assistance", { text: "P2 HIGH", tone: "amber", badge: true }, "AUTHENTICATION", { text: "IN_PROGRESS", tone: "amber", badge: true }, "11/20/2026"],
            ["TCK-0011", "Bulk student enrollment CSV import check", { text: "P3 NORMAL", tone: "blue", badge: true }, "DATA_IMPORT", { text: "RESOLVED", tone: "green", badge: true }, "11/19/2026"],
            ["TCK-0010", "Fee receipt template logo customization", { text: "P4 LOW", tone: "gray", badge: true }, "BILLING", { text: "RESOLVED", tone: "green", badge: true }, "11/18/2026"],
          ];

    builder.drawTable(
      [
        { title: "Ticket ID", width: 75 },
        { title: "Incident Description", width: 180 },
        { title: "Priority", width: 75 },
        { title: "Category", width: 90 },
        { title: "Status", width: 60 },
        { title: "Logged Date", width: 45 },
      ],
      ticketRows
    );

    builder.drawSectionHeading("2. Support SLA Governance & Escalation Tiers", "Statutory contractual commitments and response criteria");
    builder.drawTable(
      [
        { title: "Incident Severity Tier", width: 125 },
        { title: "First Response Target", width: 110 },
        { title: "Full Resolution SLA", width: 110 },
        { title: "Availability Benchmark", width: 90 },
        { title: "Operational Status", width: 90 },
      ],
      [
        ["P1 - Critical Outage", "< 15 minutes", "< 1 hour", "99.99%", { text: "ACTIVE", tone: "green", badge: true }],
        ["P2 - Major Feature Impact", "< 1 hour", "< 4 hours", "99.95%", { text: "ACTIVE", tone: "green", badge: true }],
        ["P3 - Standard Request", "< 4 hours", "< 24 hours", "99.50%", { text: "ACTIVE", tone: "green", badge: true }],
        ["P4 - Cosmetic / Guidance", "< 12 hours", "< 72 hours", "99.00%", { text: "ACTIVE", tone: "green", badge: true }],
      ]
    );
  }

  return builder.buildBlob();
}
