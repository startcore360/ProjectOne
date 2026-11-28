import api from "../../services/axios";

export type TenantExportRequest = {
  exportRequestId: string;
  tenantId: string;
  tenantName: string;
  requestedByKind: string;
  status: string;
  fileRef?: string | null;
  downloadExpiresAt?: string | null;
  createdAt: string;
  rowVersion: number;
};

export type TenantDeletionRequest = {
  deletionRequestId: string;
  tenantId: string;
  tenantName: string;
  origin: string;
  status: string;
  poConfirmedAt?: string | null;
  retentionEndsAt?: string | null;
  deletedAt?: string | null;
  createdAt: string;
  purgeStatus?: string | null;
  dpoDecidedAt?: string | null;
  dpoRemark?: string | null;
  retentionWaivedAt?: string | null;
  retentionWaiverReason?: string | null;
};

const tenantPath = (tenantId: string) => `/tenants/${encodeURIComponent(tenantId)}`;

export const tenantDataProtectionService = {
  listExports: (tenantId: string) =>
    api.get<TenantExportRequest[]>(`${tenantPath(tenantId)}/tenant-export-requests`),
  requestExport: (tenantId: string) =>
    api.post<TenantExportRequest>(`${tenantPath(tenantId)}/tenant-export-requests`),
  downloadExport: (tenantId: string, requestId: string) =>
    api.get<Blob>(`${tenantPath(tenantId)}/tenant-export-requests/${encodeURIComponent(requestId)}/download`, {
      responseType: "blob",
    }),
  listDeletions: (tenantId: string) =>
    api.get<TenantDeletionRequest[]>(`${tenantPath(tenantId)}/tenant-deletion-requests`),
  requestDeletion: (tenantId: string) =>
    api.post<TenantDeletionRequest>(`${tenantPath(tenantId)}/tenant-deletion-requests`),
};
