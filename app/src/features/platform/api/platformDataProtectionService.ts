import api from "../../../services/axios";
import { idempotencyHeaders } from "./platformTenantService";
import type { DeletionRequestDto, ExportRequestDto } from "./platformTenantService";

export type RetentionChangeRequestDto = {
  retentionChangeRequestId: string;
  tenantId: string;
  tenantName: string;
  dataClassCode: string;
  currentMonths: number | null;
  requestedMonths: number;
  reason: string;
  status: string;
  decidedAt?: string;
  createdAt: string;
};

export type DataProtectionDecision = "APPROVED" | "REJECTED";

export type RbacMappingReportRowDto = {
  scope: string; tenantId: string | null; tenantName: string; roleCode: string;
  roleName: string; actionCode: string | null; result: string; detail: string;
};
export type RbacMappingReportDto = {
  mismatches: RbacMappingReportRowDto[];
  noBaseTemplate: RbacMappingReportRowDto[];
  skippedTenants: { tenantId: string; tenantName: string; state: string }[];
};

export const platformDataProtectionService = {
  listExports: () => api.get<ExportRequestDto[]>("/platform/export-requests"),
  listDeletions: () => api.get<DeletionRequestDto[]>("/platform/deletion-requests"),
  listRetentionChanges: () => api.get<RetentionChangeRequestDto[]>("/platform/retention-change-requests"),
  getRbacMappingReport: () => api.get<RbacMappingReportDto>("/platform/rbac-mapping-report"),
  downloadRbacMappingReport: async () => {
    const response = await api.get<Blob>("/platform/rbac-mapping-report/export", { responseType: "blob" });
    const url = URL.createObjectURL(response.data);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "rbac-mapping-report.csv";
    anchor.click();
    URL.revokeObjectURL(url);
  },
  decideExport: (id: string, decision: DataProtectionDecision, remark: string, rowVersion: number) =>
    api.post(`/platform/export-requests/${id}/decision`, { decision, remark }, {
      headers: { "If-Match": `"${rowVersion}"` },
    }),
  decideDeletion: (id: string, decision: DataProtectionDecision, remark: string) =>
    api.post(`/platform/deletion-requests/${id}/decision`, { decision, remark }, { headers: idempotencyHeaders() }),
  decideRetentionChange: (id: string, decision: DataProtectionDecision, remark: string) =>
    api.post(`/platform/retention-change-requests/${id}/decision`, { decision, remark }, { headers: idempotencyHeaders() }),
};

export type PlatformDelegationCandidate={userId:string;fullName:string;email:string};
export type PlatformDelegationRequest={delegateUserId:string;from:string;to:string};
export const platformDelegationService={
 candidates:()=>api.get<PlatformDelegationCandidate[]>("/platform/delegations/candidates"),
 create:(request:PlatformDelegationRequest)=>api.post("/platform/delegations",request,{headers:idempotencyHeaders()}),
};
