import api from "../../../services/axios";

/**
 * E8-S5: headers for D5 writes marked IdempotencyKey, plus If-Match when the caller passes the row version it
 * last read. Without a version the server answers 428 IF_MATCH_REQUIRED on operations that need one.
 */
export function idempotencyHeaders(rowVersion?: number): Record<string, string> {
  const headers: Record<string, string> = { "Idempotency-Key": crypto.randomUUID() };
  if (typeof rowVersion === "number") headers["If-Match"] = `"${rowVersion}"`;
  return headers;
}

export type TenantType = "UNIVERSITY_GROUP" | "STANDALONE_COLLEGE" | "STANDALONE_SCHOOL";
export type IsolationMode = "ISOLATED" | "SHARED";
export type TenantStatus = "CREATED" | "TRIAL" | "ACTIVE" | "SUSPENDED" | "ARCHIVED" | "DELETED";

export type PlanItem = {
  planId: string;
  planName: string;
  pricingType: string;
  priceAmount: number;
  currencyCode: string;
  userLimit?: number;
  storageLimitGb?: number;
  smsLimit?: number;
  apiLimit?: number;
  maxInstitutions?: number;
  maxColleges?: number | null;
  maxSchools?: number | null;
  maxStudents?: number | null;
  maxStaff?: number | null;
  customDomainAllowed: boolean;
  brandingColorsAllowed: boolean;
  billingCycleMonths: number;
  smsUnitAmount?: number;
  storageUnitAmountPerGb?: number;
  setupFeeAmount?: number;
  discountType?: "NONE" | "PERCENT" | "FIXED";
  discountValue?: number;
  contractDurationMonths?: number;
  gracePeriodDays?: number;
  renewalMode?: "AUTOMATIC" | "MANUAL";
  pricingEngineVersion?: "LEGACY" | "V2";
  status: string;
  rowVersion: number;
};

export type TenantItem = {
  tenantId: string;
  tenantName: string;
  tenantType: TenantType;
  planId: string;
  planName: string;
  state: TenantStatus;
  isolationMode: IsolationMode;
  subdomain: string;
  customDomain?: string;
  timeZone: string;
  dataRegion: string;
  trialEndsAt?: string;
  userLimit?: number;
  storageLimitGb?: number;
  smsLimit?: number;
  apiLimit?: number;
  keycloakRealm: string;
  firstAdminEmail?: string;
  firstAdminMobile?: string;
  firstAdminNationalIdLast4?: string;
  createdAt: string;
  suspendedAt?: string;
  archivedAt?: string;
  // Present only on the create response. It's never stored or returned again.
  firstAdminTemporaryPassword?: string;
  /** E8-S5: current row version, sent back as If-Match on activate / suspend / archive. */
  rowVersion?: number;
};

export type TenantPageResponse = {
  items: TenantItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type TenantCreateRequest = {
  tenantName: string;
  tenantType: TenantType;
  planId: string;
  subdomain: string;
  isolationMode: IsolationMode;
  firstAdminName: string;
  firstAdminEmail: string;
  firstAdminMobile?: string;
  firstAdminNationalIdLast4?: string;
  retentionMonths: number;
  backupRetentionDays: number;
  timeZone?: string;
  dataRegion?: string;
};

export type CreatePlanPayload = {
  planName: string;
  pricingType?: string;
  priceAmount?: number;
  currencyCode?: string;
  userLimit?: number;
  storageLimitGb?: number;
  smsLimit?: number;
  apiLimit?: number;
  maxInstitutions?: number;
  maxColleges?: number | null;
  maxSchools?: number | null;
  maxStudents?: number | null;
  maxStaff?: number | null;
  customDomainAllowed?: boolean;
  brandingColorsAllowed?: boolean;
  billingCycleMonths?: number;
  smsUnitAmount?: number;
  storageUnitAmountPerGb?: number;
  setupFeeAmount?: number;
  discountType?: "NONE" | "PERCENT" | "FIXED";
  discountValue?: number;
  contractDurationMonths?: number;
  gracePeriodDays?: number;
  renewalMode?: "AUTOMATIC" | "MANUAL";
};

export type UpdatePlanPayload = {
  planName?: string;
  pricingType?: string;
  priceAmount?: number;
  currencyCode?: string;
  userLimit?: number;
  storageLimitGb?: number;
  smsLimit?: number;
  apiLimit?: number;
  maxInstitutions?: number;
  maxColleges?: number | null;
  maxSchools?: number | null;
  maxStudents?: number | null;
  maxStaff?: number | null;
  customDomainAllowed?: boolean;
  brandingColorsAllowed?: boolean;
  billingCycleMonths?: number;
  smsUnitAmount?: number;
  storageUnitAmountPerGb?: number;
  setupFeeAmount?: number;
  discountType?: "NONE" | "PERCENT" | "FIXED";
  discountValue?: number;
  contractDurationMonths?: number;
  gracePeriodDays?: number;
  renewalMode?: "AUTOMATIC" | "MANUAL";
  status?: string;
};

export type CommercialCatalogItemDto = {
  catalogItemId: string;
  itemCode: string;
  itemName: string;
  category: string;
  unitCode: string;
  pricingBasis: string;
  unitAmount: number;
  unitsPerQuantity: number;
  billingIntervalMonths?: number;
  moduleCode?: string;
  description?: string;
  available: boolean;
  rowVersion: number;
};

export type CommercialCatalogItemRequest = {
  itemCode: string;
  itemName: string;
  category: string;
  unitCode: string;
  pricingBasis: string;
  unitAmount: number;
  unitsPerQuantity: number;
  billingIntervalMonths: number;
  moduleCode: string | null;
  description: string | null;
  available: boolean;
};

export type PlanComponentDraft = {
  catalogItemId: string;
  includedQuantity: number;
  unitAmountOverride?: number | null;
  enabled?: boolean;
};

export type PlanComponentDto = PlanComponentDraft & {
  planComponentId: string;
  itemCode: string;
  itemName: string;
  category: string;
  unitCode: string;
  pricingBasis: string;
  unitAmount: number;
  enabled: boolean;
};

export type CustomerQuoteRequest = {
  tenantType: TenantType;
  colleges: number;
  schools: number;
  students: number;
  staff: number;
  requiredModules: string[];
  billingCycleMonths: number;
  includeSetupFee: boolean;
};

export type CustomerQuoteLine = {
  code: string;
  description: string;
  quantity: number;
  unitAmount: number;
  amount: number;
};

export type CustomerPlanQuote = {
  planId: string;
  planName: string;
  pricingType: string;
  eligible: boolean;
  recommendation: string | null;
  fitReasons: string[];
  unmetRequirements: string[];
  subtotal: number;
  discount: number;
  total: number;
  tax: number;
  lines: CustomerQuoteLine[];
};

export type CustomerQuoteResponse = {
  currencyCode: string;
  billingCycleMonths: number;
  plans: CustomerPlanQuote[];
  lowestCostPlanId: string | null;
  bestFitPlanId: string | null;
};

export type TenantPlanChangeResponse = {
  tenantId: string;
  previousPlanId: string;
  activePlanId: string;
  pendingPlanId?: string;
  effective: "NOW" | "RENEWAL";
  nextRenewalAt?: string;
  prorationInvoice?: { invoiceNumber: string; subtotalAmount: number; totalAmount: number };
};

export type TenantSettings = {
  tenantId: string;
  tenantName: string;
  planName: string;
  state: string;
  timeZone: string;
  dataRegion: string;
  analyticsOptOut: boolean;
  customDomain?: string;
  userLimit?: number;
  storageLimitGb?: number;
  smsLimit?: number;
  apiLimit?: number;
  // E1-S3: the plan's own limits, shown beside each field.
  planUserLimit?: number | null;
  planStorageLimitGb?: number | null;
  planSmsLimit?: number | null;
  planApiLimit?: number | null;
  customDomainAllowed?: boolean;
  brandingColorsAllowed?: boolean;
};

export type UpdateTenantSettingsPayload = {
  timeZone?: string;
  dataRegion?: string;
  analyticsOptOut?: boolean;
  customDomain?: string;
  userLimit?: number;
  storageLimitGb?: number;
  smsLimit?: number;
  apiLimit?: number;
};

export type ExportRequestDto = {
  exportRequestId: string;
  tenantId: string;
  tenantName: string;
  requestedByKind: string;
  status: string;
  fileRef?: string;
  downloadExpiresAt?: string;
  createdAt: string;
  rowVersion: number;
};

export type DeletionRequestDto = {
  deletionRequestId: string;
  tenantId: string;
  tenantName: string;
  origin: string;
  status: string;
  poConfirmedAt?: string;
  retentionEndsAt?: string;
  deletedAt?: string;
  createdAt: string;
  purgeStatus?: string;
  dpoDecidedAt?: string;
  dpoRemark?: string;
  retentionWaivedAt?: string;
  retentionWaiverReason?: string;
  /** E8-S5: sent back as If-Match on the Product Owner confirmation. */
  rowVersion?: number;
};

export type RetentionPolicyDto = {
  tenantRetentionPolicyId: string;
  tenantId: string;
  dataClassCode: string;
  retentionMonths: number;
  effectiveFrom: string;
  status: string;
};

export type TenantUsageMonthlyDto = {
  tenantUsageMonthlyId: string;
  tenantId: string;
  usageMonth: string;
  metricCode: string;
  quantity: number;
  measured: boolean;
  computedAt: string;
  isFinal: boolean;
};

// E1-S2: the seven tenant setup steps, in the order they run.
export type ProvisioningStep = {
  executionOrder: number;
  planStep: string;
  stepCode: string;
  description: string;
  status: "PENDING" | "RUNNING" | "DONE" | "FAILED" | "COMPENSATED";
  attempts: number;
  lastErrorCode: string | null;
  startedAt: string | null;
  finishedAt: string | null;
};
export type ProvisioningStatus = {
  tenantId: string;
  overallStatus: "DONE" | "FAILED" | "RUNNING" | "PENDING";
  retryable: boolean;
  steps: ProvisioningStep[];
};

export const platformTenantService = {
  getPlans: () => api.get<PlanItem[]>("/platform/plans"),
  getProvisioning: (tenantId: string) =>
    api.get<ProvisioningStatus>(`/platform/tenants/${tenantId}/provisioning`),
  // May return a new one-time temporary password for the first admin (PD-10).
  retryProvisioning: (tenantId: string, retention?: { retentionMonths: number; backupRetentionDays: number }) =>
    api.post<TenantItem>(`/platform/tenants/${tenantId}/provisioning/retry`, retention ?? {}),
  createPlan: (data: CreatePlanPayload) => api.post<PlanItem>("/platform/plans", data),
  updatePlan: (planId: string, data: UpdatePlanPayload, rowVersion: number) =>
    api.put<PlanItem>(`/platform/plans/${planId}`, data, {
      headers: { "If-Match": `"${rowVersion}"` },
    }),
  getCommercialCatalog: () =>
    api.get<CommercialCatalogItemDto[]>("/platform/subscriptions/catalog"),
  createCommercialCatalogItem: (request: CommercialCatalogItemRequest) =>
    api.post<CommercialCatalogItemDto>("/platform/subscriptions/catalog", request),
  updateCommercialCatalogItem: (itemId: string, request: CommercialCatalogItemRequest, rowVersion: number) =>
    api.put<CommercialCatalogItemDto>(`/platform/subscriptions/catalog/${itemId}`, request, {
      headers: { "If-Match": `"${rowVersion}"` },
    }),
  getPlanComponents: (planId: string) =>
    api.get<PlanComponentDto[]>(`/platform/subscriptions/plans/${planId}/components`),
  replacePlanComponents: (planId: string, components: PlanComponentDraft[], rowVersion: number) =>
    api.put<PlanComponentDto[]>(`/platform/subscriptions/plans/${planId}/components`, components, {
      headers: { "If-Match": `"${rowVersion}"` },
    }),
  quoteCustomer: (request: CustomerQuoteRequest) =>
    api.post<CustomerQuoteResponse>("/platform/subscriptions/quote", request),
  getTenants: (page = 0, size = 50) =>
    api.get<TenantPageResponse>(`/platform/tenants?page=${page}&size=${size}`),
  // E8-S5: D5 marks these writes IdempotencyKey (and If-Match where a version is passed); a fresh key per
  // user action means a network retry of that action is replayed by the server instead of run twice.
  createTenant: (data: TenantCreateRequest) =>
    api.post<TenantItem>("/platform/tenants", data, { headers: idempotencyHeaders() }),
  activateTenant: (tenantId: string, data?: { paymentReference?: string; note?: string }, rowVersion?: number) =>
    api.post<TenantItem>(`/platform/tenants/${tenantId}/activation`, data || {}, { headers: idempotencyHeaders(rowVersion) }),
  suspendTenant: (tenantId: string, reason?: string, rowVersion?: number) =>
    api.post<TenantItem>(`/platform/tenants/${tenantId}/suspension`, { reason }, { headers: idempotencyHeaders(rowVersion) }),
  archiveTenant: (tenantId: string, reason?: string, rowVersion?: number) =>
    api.post<TenantItem>(`/platform/tenants/${tenantId}/archive`, { reason }, { headers: idempotencyHeaders(rowVersion) }),
  reactivateTenant: (tenantId: string) =>
    api.post<TenantItem>(`/platform/tenants/${tenantId}/reactivation`),
  changeTenantPlan: (tenantId: string, payload: { planId: string; effective: "NOW" | "RENEWAL" }, idempotencyKey: string) =>
    api.post<TenantPlanChangeResponse>(`/platform/tenants/${tenantId}/plan-change`, payload, { headers: { "Idempotency-Key": idempotencyKey } }),
  getTenantSettings: (tenantId: string) =>
    api.get<TenantSettings>(`/platform/tenants/${tenantId}/settings`),
  updateTenantSettings: (tenantId: string, data: UpdateTenantSettingsPayload) =>
    api.patch<TenantSettings>(`/platform/tenants/${tenantId}/settings`, data),
  exportTenant: (tenantId: string) =>
    api.post<ExportRequestDto>(`/platform/tenants/${tenantId}/export`, {}, {
      headers: { "Idempotency-Key": `tenant-export-${tenantId}-${crypto.randomUUID()}` },
    }),
  listExports: (tenantId: string) =>
    api.get<ExportRequestDto[]>(`/platform/tenants/${tenantId}/export`),
  requestTenantDeletion: (tenantId: string, origin = "PRODUCT_OWNER") =>
    api.post<DeletionRequestDto>(`/platform/deletion-requests`, { tenantId, origin }, { headers: idempotencyHeaders() }),
  listDeletionRequests: () => api.get<DeletionRequestDto[]>("/platform/deletion-requests"),
  confirmTenantDeletion: (
    requestId: string,
    options: { waiveRetentionWait?: boolean; waiverReason?: string } = {},
    rowVersion?: number,
  ) =>
    api.post<DeletionRequestDto>(`/platform/deletion-requests/${requestId}/confirm`, {
      waiveRetentionWait: options.waiveRetentionWait ?? false,
      waiverReason: options.waiveRetentionWait ? options.waiverReason : undefined,
    }, { headers: idempotencyHeaders(rowVersion) }),
  getRetentionPolicies: (tenantId: string) =>
    api.get<RetentionPolicyDto[]>(`/platform/tenants/${tenantId}/retention`),
  requestRetentionChange: (tenantId: string, data: { dataClassCode: string; requestedMonths: number; reason: string }) =>
    api.post<{ status: string }>(`/platform/tenants/${tenantId}/retention-change-requests`, data),
  getTenantUsage: (tenantId: string) =>
    api.get<TenantUsageMonthlyDto[]>(`/platform/tenants/${tenantId}/usage`),
};
