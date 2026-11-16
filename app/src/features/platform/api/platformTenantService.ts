import api from "../../../services/axios";

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
  customDomainAllowed: boolean;
  brandingColorsAllowed: boolean;
  billingCycleMonths: number;
  smsUnitAmount?: number;
  storageUnitAmountPerGb?: number;
  status: string;
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
  // Present only on the create response. It's never stored or returned again.
  firstAdminTemporaryPassword?: string;
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
  customDomainAllowed?: boolean;
  brandingColorsAllowed?: boolean;
  billingCycleMonths?: number;
  smsUnitAmount?: number;
  storageUnitAmountPerGb?: number;
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
  customDomainAllowed?: boolean;
  brandingColorsAllowed?: boolean;
  billingCycleMonths?: number;
  smsUnitAmount?: number;
  storageUnitAmountPerGb?: number;
  status?: string;
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
  requestedByKind: string;
  status: string;
  fileRef?: string;
  downloadExpiresAt?: string;
  createdAt: string;
};

export type DeletionRequestDto = {
  deletionRequestId: string;
  tenantId: string;
  origin: string;
  status: string;
  poConfirmedAt?: string;
  retentionEndsAt?: string;
  deletedAt?: string;
  createdAt: string;
  purgeStatus?: string;
  dpoDecidedAt?: string;
  dpoRemark?: string;
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
  computedAt: string;
  isFinal: boolean;
};

export const platformTenantService = {
  getPlans: () => api.get<PlanItem[]>("/platform/plans"),
  createPlan: (data: CreatePlanPayload) => api.post<PlanItem>("/platform/plans", data),
  updatePlan: (planId: string, data: UpdatePlanPayload) => api.put<PlanItem>(`/platform/plans/${planId}`, data),
  getTenants: (page = 0, size = 50) =>
    api.get<TenantPageResponse>(`/platform/tenants?page=${page}&size=${size}`),
  createTenant: (data: TenantCreateRequest) =>
    api.post<TenantItem>("/platform/tenants", data),
  activateTenant: (tenantId: string, data?: { paymentReference?: string; note?: string }) =>
    api.post<TenantItem>(`/platform/tenants/${tenantId}/activation`, data || {}),
  suspendTenant: (tenantId: string, reason?: string) =>
    api.post<TenantItem>(`/platform/tenants/${tenantId}/suspension`, { reason }),
  archiveTenant: (tenantId: string, reason?: string) =>
    api.post<TenantItem>(`/platform/tenants/${tenantId}/archive`, { reason }),
  reactivateTenant: (tenantId: string) =>
    api.post<TenantItem>(`/platform/tenants/${tenantId}/reactivation`),
  getTenantSettings: (tenantId: string) =>
    api.get<TenantSettings>(`/platform/tenants/${tenantId}/settings`),
  updateTenantSettings: (tenantId: string, data: UpdateTenantSettingsPayload) =>
    api.patch<TenantSettings>(`/platform/tenants/${tenantId}/settings`, data),
  exportTenant: (tenantId: string) =>
    api.post(`/platform/tenants/${tenantId}/export`),
  listExports: (tenantId: string) =>
    api.get(`/platform/tenants/${tenantId}/export`),
  requestTenantDeletion: (tenantId: string, origin = "PRODUCT_OWNER") =>
    api.post<DeletionRequestDto>(`/platform/deletion-requests`, { tenantId, origin }),
  getRetentionPolicies: (tenantId: string) =>
    api.get(`/platform/tenants/${tenantId}/retention`),
  requestRetentionChange: (tenantId: string, data: { dataClassCode: string; requestedMonths: number; reason: string }) =>
    api.post(`/platform/tenants/${tenantId}/retention-change-requests`, data),
  getTenantUsage: (tenantId: string) =>
    api.get<TenantUsageMonthlyDto[]>(`/platform/tenants/${tenantId}/usage`),
};
