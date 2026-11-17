import api from "../../../services/axios";

export type IntegrationTypeItem = {
  integrationTypeId: string;
  integrationTypeCode: string;
  description: string;
  status: "ACTIVE" | "DEPRECATED" | "DISABLED" | string;
  allowedProviders: string[];
  createdAt: string;
  updatedAt: string;
  rowVersion: number;
};

export type CreateIntegrationTypePayload = {
  integrationTypeCode: string;
  description: string;
  allowedProviders?: string[];
};

export type PlatformAuditLogItem = {
  auditOutboxId: string;
  tenantId?: string;
  occurredAt: string;
  actorKind: string;
  actorUserId?: string;
  operation: string;
  actionCode?: string;
  entityType?: string;
  entityId?: string;
  outcome: string;
  correlationId?: string;
};

export type UsageMetricSnapshot = {
  metricCode: string;
  value: number | null;
  asOfDate: string | null;
};

export type UsageCountsResponse = {
  totalTenantCount: number;
  tenantsInAnalytics: number;
  tenantsOptedOut: number;
  metrics: UsageMetricSnapshot[];
};

export type BackupStatusItem = {
  tenantId: string;
  lastDailyBackupAt: string;
  lastBackupOutcome: "SUCCESS" | "FAILED" | "IN_PROGRESS" | string;
  earliestRestorableAt?: string;
  latestRestorableAt?: string;
  lastRestoreTestAt?: string;
  lastRestoreTestOutcome?: string;
};

export type InvoiceItem = {
  invoiceId: string;
  tenantId: string;
  invoiceNumber: string;
  periodStart: string;
  periodEnd: string;
  subtotalAmount: number;
  discountAmount: number;
  totalAmount: number;
  currencyCode: string;
  status: "DRAFT" | "APPROVED" | "SENT" | "PAID";
  approvedBy?: string;
  approvedAt?: string;
  dueAt?: string;
  sentAt?: string;
  paidAt?: string;
  paymentMode?: string;
  paymentReference?: string;
  overdueFlaggedAt?: string;
  overdue: boolean;
  createdAt: string;
};

export type BillingSettingsItem = { invoiceDueDays: number };

export type BillingCycleSkippedTenant = { tenantId: string; reason: string };
export type BillingCycleResponse = { created: InvoiceItem[]; skipped: BillingCycleSkippedTenant[] };

export type InvoiceLineItem = {
  invoiceLineId: string;
  invoiceId: string;
  lineType: string;
  description: string;
  quantity: number;
  unitAmount: number;
  lineAmount: number;
  tenantUsageMonthlyId?: string;
  createdAt: string;
};

export type ApplyDiscountPayload = {
  discountPercent: number;
  reason: string;
};

export type AddInvoiceLinePayload = {
  description: string;
  quantity: number;
  unitAmount: number;
};

export type CreateInvoicePayload = {
  tenantId: string;
  periodStart: string;
  periodEnd: string;
  subtotalAmount: number;
  currencyCode?: string;
};

export type ReleaseItem = {
  releaseId: string;
  versionLabel: string;
  releaseNotes: string;
  isHotfix: boolean;
  status: "UPLOADED" | "STAGING_TESTED" | "DEPLOYED" | "ROLLED_BACK";
  deployedAt?: string;
  rolledBackAt?: string;
  schemaVersion: string;
  createdAt: string;
};

export type CreateReleasePayload = {
  versionLabel: string;
  releaseNotes: string;
  isHotfix?: boolean;
  schemaVersion: string;
};

export type SecurityPolicyItem = {
  minPasswordLength: number;
  requireSpecialChar: boolean;
  requireDigit: boolean;
  maxFailedLogins: number;
  lockoutMinutes: number;
  idleSessionTimeoutMinutes: number;
  mfaRequiredForPrivileged: boolean;
  defaultBackupRetentionDays: number;
};

export type SecurityIncidentItem = {
  securityIncidentId: string;
  tenantId?: string;
  title: string;
  detectedAt: string;
  severity: string;
  ownerUserId: string;
  status: "DETECTED" | "CONTAINED" | "RECOVERED" | "CLOSED";
  externalNotificationRequired: boolean;
  externalNotifiedAt?: string;
  closureNote?: string;
  createdAt: string;
};

export type SupportTicketItem = {
  supportTicketId: string;
  tenantId: string;
  ticketNumber: string;
  raisedByUserId: string;
  raisedByName: string;
  subject: string;
  description: string;
  priority: string;
  category: string;
  status: "NEW" | "TRIAGED" | "ASSIGNED" | "ESCALATED" | "RESOLVED" | "CLOSED";
  assignedTo?: string;
  escalatedAt?: string;
  resolutionNote?: string;
  createdAt: string;
};

export type SupportAccessGrantItem = {
  supportAccessGrantId: string;
  ticketId: string;
  tenantId: string;
  grantedByUserId: string;
  institutionId?: string;
  grantedAt: string;
  expiresAt: string;
  withdrawnAt?: string;
  createdAt: string;
};

export type PlatformReportItem = {
  reportCode: string;
  reportName: string;
  generatedAt: string;
  currency: string;
  metrics: Record<string, unknown>;
};

export const platformOpsService = {
  async listIntegrationTypes(): Promise<IntegrationTypeItem[]> {
    const res = await api.get<IntegrationTypeItem[]>("/platform/integration-types");
    return res.data;
  },

  async createIntegrationType(payload: CreateIntegrationTypePayload): Promise<IntegrationTypeItem> {
    const res = await api.post<IntegrationTypeItem>("/platform/integration-types", payload);
    return res.data;
  },

  async listAuditLogs(): Promise<PlatformAuditLogItem[]> {
    const res = await api.get<PlatformAuditLogItem[] | { items?: PlatformAuditLogItem[] }>("/platform/audit-logs");
    if (Array.isArray(res.data)) return res.data;
    if (Array.isArray(res.data?.items)) return res.data.items;
    return [];
  },

  async getUsageCounts(): Promise<UsageCountsResponse> {
    const res = await api.get<UsageCountsResponse>("/platform/usage-counts");
    return res.data;
  },

  async getPlatformBackupStatus(): Promise<BackupStatusItem[]> {
    const res = await api.get<BackupStatusItem[]>("/platform/backup-status");
    return res.data;
  },

  // Billing & Invoices
  async getBillingSettings(): Promise<BillingSettingsItem> {
    const res = await api.get<BillingSettingsItem>("/platform/billing/settings");
    return res.data;
  },

  async updateBillingSettings(invoiceDueDays: number): Promise<BillingSettingsItem> {
    const res = await api.put<BillingSettingsItem>("/platform/billing/settings", { invoiceDueDays });
    return res.data;
  },

  async createBillingCycle(payload: { periodStart: string; periodEnd: string }, idempotencyKey: string): Promise<BillingCycleResponse> {
    const res = await api.post<BillingCycleResponse>("/platform/billing-cycles", payload, { headers: { "Idempotency-Key": idempotencyKey } });
    return res.data;
  },

  async listInvoices(tenantId?: string): Promise<InvoiceItem[]> {
    const query = tenantId ? `?tenantId=${tenantId}` : "";
    const res = await api.get<InvoiceItem[]>(`/platform/invoices${query}`);
    return res.data;
  },

  async createInvoice(payload: CreateInvoicePayload): Promise<InvoiceItem> {
    const res = await api.post<InvoiceItem>("/platform/invoices", payload);
    return res.data;
  },

  async listInvoiceLines(invoiceId: string): Promise<InvoiceLineItem[]> {
    const res = await api.get<InvoiceLineItem[]>(`/platform/invoices/${invoiceId}/lines`);
    return res.data;
  },

  async addInvoiceLine(invoiceId: string, payload: AddInvoiceLinePayload): Promise<InvoiceItem> {
    const res = await api.post<InvoiceItem>(`/platform/invoices/${invoiceId}/lines`, payload);
    return res.data;
  },

  async removeInvoiceLine(invoiceId: string, lineId: string): Promise<InvoiceItem> {
    const res = await api.delete<InvoiceItem>(`/platform/invoices/${invoiceId}/lines/${lineId}`);
    return res.data;
  },

  async approveInvoice(invoiceId: string, idempotencyKey: string): Promise<InvoiceItem> {
    const res = await api.post<InvoiceItem>(`/platform/invoices/${invoiceId}/approval`, {}, {
      headers: { "Idempotency-Key": idempotencyKey },
    });
    return res.data;
  },

  async sendInvoice(invoiceId: string): Promise<InvoiceItem> {
    const res = await api.post<InvoiceItem>(`/platform/invoices/${invoiceId}/send`);
    return res.data;
  },

  async applyDiscount(invoiceId: string, payload: ApplyDiscountPayload, idempotencyKey: string): Promise<InvoiceItem> {
    const res = await api.post<InvoiceItem>(`/platform/invoices/${invoiceId}/discounts`, payload, {
      headers: { "Idempotency-Key": idempotencyKey },
    });
    return res.data;
  },

  async flagOverdue(invoiceId: string): Promise<InvoiceItem> {
    const res = await api.post<InvoiceItem>(`/platform/invoices/${invoiceId}/overdue-flags`);
    return res.data;
  },

  async recordPayment(invoiceId: string, payload: { paymentMode: string; paymentReference: string }): Promise<InvoiceItem> {
    const res = await api.post<InvoiceItem>(`/platform/invoices/${invoiceId}/record-payment`, payload);
    return res.data;
  },

  // Releases
  async listReleases(): Promise<ReleaseItem[]> {
    const res = await api.get<ReleaseItem[]>("/platform/releases");
    return res.data;
  },

  async createRelease(payload: CreateReleasePayload): Promise<ReleaseItem> {
    const res = await api.post<ReleaseItem>("/platform/releases", payload);
    return res.data;
  },

  async updateRelease(releaseId: string, payload: Partial<CreateReleasePayload>): Promise<ReleaseItem> {
    const res = await api.put<ReleaseItem>(`/platform/releases/${releaseId}`, payload);
    return res.data;
  },

  async transitionRelease(releaseId: string, targetStatus: string): Promise<ReleaseItem> {
    const res = await api.post<ReleaseItem>(`/platform/releases/${releaseId}/transitions`, { targetStatus });
    return res.data;
  },

  // Reports
  async getReport(reportCode: string): Promise<PlatformReportItem> {
    const res = await api.get<PlatformReportItem>(`/platform/reports/${reportCode}`);
    return res.data;
  },

  // Security Policy & Incidents
  async getSecurityPolicy(): Promise<SecurityPolicyItem> {
    const res = await api.get<SecurityPolicyItem>("/platform/security-policy");
    return res.data;
  },

  async updateSecurityPolicy(payload: Partial<SecurityPolicyItem>): Promise<SecurityPolicyItem> {
    const res = await api.put<SecurityPolicyItem>("/platform/security-policy", payload);
    return res.data;
  },

  async listSecurityIncidents(): Promise<SecurityIncidentItem[]> {
    const res = await api.get<SecurityIncidentItem[]>("/platform/security-incidents");
    return res.data;
  },

  async createSecurityIncident(payload: { tenantId?: string; title: string; severity?: string; externalNotificationRequired?: boolean }): Promise<SecurityIncidentItem> {
    const res = await api.post<SecurityIncidentItem>("/platform/security-incidents", payload);
    return res.data;
  },

  async updateSecurityIncident(id: string, payload: { status?: string; closureNote?: string }): Promise<SecurityIncidentItem> {
    const res = await api.patch<SecurityIncidentItem>(`/platform/security-incidents/${id}`, payload);
    return res.data;
  },

  // Support
  async listSupportTickets(tenantId?: string): Promise<SupportTicketItem[]> {
    const query = tenantId ? `?tenantId=${tenantId}` : "";
    const res = await api.get<SupportTicketItem[]>(`/platform/support-tickets${query}`);
    return res.data;
  },

  async updateSupportTicket(ticketId: string, payload: { status?: string; assignedTo?: string; resolutionNote?: string; priority?: string; category?: string }): Promise<SupportTicketItem> {
    const res = await api.patch<SupportTicketItem>(`/platform/support-tickets/${ticketId}`, payload);
    return res.data;
  },

  async escalateSupportTicket(ticketId: string, payload: { to: "ENGINEERING" | "RELEASE_MANAGER"; note: string }): Promise<SupportTicketItem> {
    const res = await api.post<SupportTicketItem>(`/platform/support-tickets/${ticketId}/escalation`, payload);
    return res.data;
  },

  async assumeSupportSession(ticketId: string): Promise<{ token: string; expiresAt: string; tenantLoginAddress: string }> {
    const res = await api.post<{ token: string; expiresAt: string; tenantLoginAddress: string }>(
      "/platform/support-sessions",
      { ticketId },
    );
    return res.data;
  },

  async endCurrentSupportSession(): Promise<void> {
    await api.delete("/platform/support-sessions/current");
  },

  async listActiveGrants(): Promise<SupportAccessGrantItem[]> {
    const res = await api.get<SupportAccessGrantItem[]>("/platform/support-access-grants");
    return res.data;
  },
};
