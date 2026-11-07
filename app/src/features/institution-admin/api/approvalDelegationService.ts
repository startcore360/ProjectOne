import api from "../../../services/axios";

export type DelegationItem = {
  delegationId: string;
  tenantId: string;
  delegatorUserId: string;
  delegateUserId: string;
  delegationBasis: "SAME_OR_HIGHER" | "DESIGNATED_DEPUTY";
  processCode?: string;
  startsAt: string;
  endsAt: string;
  status: "ACTIVE" | "ENDED" | "CANCELLED";
  cancelledAt?: string;
  cancelledBy?: string;
  rowVersion: number;
};

export type CreateDelegationRequest = {
  delegateUserId: string;
  delegationBasis: "SAME_OR_HIGHER" | "DESIGNATED_DEPUTY";
  processCode?: string;
  startsAt: string;
  endsAt: string;
};

export type BulkApprovalRequest = {
  approvalRequestIds: string[];
  decision: "APPROVED" | "REJECTED";
  comment?: string;
};

export type BulkApprovalResponse = {
  batchId: string;
  totalProcessed: number;
  succeeded: number;
  failed: number;
  errors: string[];
};

export type ApprovalStepItem = {
  stepNo: number;
  approverRoleCode: string;
  decision: string;
  actedBy: string | null;
  actedActiveRole: string | null;
  remark: string | null;
  skipReason: string | null;
  decidedAt: string | null;
  approvalRequestId?: string;
};

export const approvalDelegationService = {
  async listInbox(tenantId: string): Promise<ApprovalStepItem[]> {
    const res = await api.get<{ items: ApprovalStepItem[] }>(`/api/tenants/${tenantId}/approvals/inbox`);
    return res.data?.items || [];
  },

  async decideApproval(
    tenantId: string,
    requestId: string,
    decision: "APPROVED" | "REJECTED",
    remark?: string,
    stepNo = 1
  ): Promise<unknown> {
    const res = await api.post(`/api/tenants/${tenantId}/approval-requests/${requestId}/decisions`, {
      stepNo,
      decision,
      remark: remark || (decision === "REJECTED" ? "Rejected by approver" : undefined),
    });
    return res.data;
  },

  async listDelegations(tenantId: string): Promise<DelegationItem[]> {
    const res = await api.get<DelegationItem[]>(`/api/tenants/${tenantId}/delegations`);
    return res.data;
  },

  async createDelegation(
    tenantId: string,
    request: CreateDelegationRequest
  ): Promise<DelegationItem> {
    const res = await api.post<DelegationItem>(`/api/tenants/${tenantId}/delegations`, request);
    return res.data;
  },

  async cancelDelegation(tenantId: string, delegationId: string): Promise<DelegationItem> {
    const res = await api.post<DelegationItem>(
      `/api/tenants/${tenantId}/delegations/${delegationId}/cancellation`
    );
    return res.data;
  },

  async bulkDecide(
    tenantId: string,
    request: BulkApprovalRequest
  ): Promise<BulkApprovalResponse> {
    const res = await api.post<BulkApprovalResponse>(
      `/api/tenants/${tenantId}/approvals/bulk-decisions`,
      request
    );
    return res.data;
  },
};
