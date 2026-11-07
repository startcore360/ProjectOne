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
};

export type UpdatePlanPayload = {
  planName?: string;
  priceAmount?: number;
  currencyCode?: string;
  userLimit?: number;
  storageLimitGb?: number;
  smsLimit?: number;
  apiLimit?: number;
  maxInstitutions?: number;
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
  id: string;
  tenantId: string;
  status: string;
  downloadUrl?: string;
  requestedAt: string;
  completedAt?: string;
};

export type RetentionPolicyDto = {
  id: string;
  tenantId: string;
  dataCategory: string;
  dataClassCode?: string;
  retentionPeriodDays: number;
  retentionMonths?: number;
  updatedAt?: string;
};

export type TenantUsageMonthlyDto = {
  id: string;
  tenantId: string;
  usageYear: number;
  usageMonth: number;
  activeUserCount: number;
  storageUsedGb: number;
  loginCount: number;
  apiCallCount: number;
  bandwidthGb: number;
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
  deleteTenant: (tenantId: string, origin = "PRODUCT_OWNER") =>
    api.delete(`/platform/tenants/${tenantId}?origin=${origin}`),
  getRetentionPolicies: (tenantId: string) =>
    api.get(`/platform/tenants/${tenantId}/retention`),
  requestRetentionChange: (tenantId: string, data: { dataClassCode: string; requestedMonths: number; reason: string }) =>
    api.post(`/platform/tenants/${tenantId}/retention`, data),
  getTenantUsage: (tenantId: string) =>
    api.get(`/platform/tenants/${tenantId}/usage`),
};
