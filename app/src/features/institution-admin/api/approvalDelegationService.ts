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

export type EligibleDelegate = {
  userAccountId: string;
  displayName: string;
  email: string;
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
};

export type ApprovalInboxItem = {
  approvalRequestId: string;
  stepNo: number;
  approverRoleCode: string;
  decision: string;
  processCode: string;
  subjectType: string;
  subjectId: string;
  institutionId: string | null;
  requesterActiveRole: string;
  requestedAt: string;
  currentStepNo: number;
  requestStatus: string;
};

export type ApprovalRequestSummary = {
  approvalRequestId: string;
  processCode: string;
  status: string;
  currentStepNo: number;
  subject: { type: string; id: string };
  steps: ApprovalStepItem[];
};

export const approvalDelegationService = {
  async listInbox(tenantId: string): Promise<ApprovalInboxItem[]> {
    const res = await api.get<{ items: ApprovalInboxItem[] }>(`/api/tenants/${tenantId}/approvals/inbox`);
    return res.data?.items || [];
  },

  async getApprovalRequest(tenantId: string, requestId: string): Promise<ApprovalRequestSummary> {
    const res = await api.get<ApprovalRequestSummary>(`/api/tenants/${tenantId}/approval-requests/${requestId}`);
    return res.data;
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
      remark,
    });
    return res.data;
  },

  async listEligibleDelegates(tenantId: string): Promise<EligibleDelegate[]> {
    const res = await api.get<EligibleDelegate[]>(`/api/tenants/${tenantId}/delegations/eligible-users`);
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
