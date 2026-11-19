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

export const platformDataProtectionService = {
  listExports: () => api.get<ExportRequestDto[]>("/platform/export-requests"),
  listDeletions: () => api.get<DeletionRequestDto[]>("/platform/deletion-requests"),
  listRetentionChanges: () => api.get<RetentionChangeRequestDto[]>("/platform/retention-change-requests"),
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
