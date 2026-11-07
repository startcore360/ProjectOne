import api from "../../../services/axios";

export type GuardianLinkItem = {
  studentGuardianLinkId: string;
  tenantId: string;
  studentProfileId: string;
  guardianProfileId: string;
  guardianName?: string;
  guardianMobile?: string;
  guardianEmail?: string;
  relationship: string;
  isPrimary: boolean;
  status: "PENDING_ACTIVATION" | "ACTIVE" | "BLOCKED";
  changeApprovalRequestId?: string;
  blockedAt?: string;
  blockReason?: string;
  createdAt: string;
  rowVersion: number;
};

export type LinkGuardianRequest = {
  fullName: string;
  mobile: string;
  email?: string;
  relationship: string;
  isPrimary?: boolean;
};

export type GuardianChangeRequest = {
  reason: string;
};

export type GuardianChangeDecision = {
  approved: boolean;
  decisionNotes?: string;
};

export const guardianService = {
  async listGuardianLinks(
    tenantId: string,
    studentId: string
  ): Promise<GuardianLinkItem[]> {
    const res = await api.get<GuardianLinkItem[]>(
      `/api/tenants/${tenantId}/students/${studentId}/guardian-links`
    );
    return res.data;
  },

  async linkGuardian(
    tenantId: string,
    studentId: string,
    request: LinkGuardianRequest,
    institutionId?: string
  ): Promise<GuardianLinkItem> {
    const res = await api.post<GuardianLinkItem>(
      `/api/tenants/${tenantId}/students/${studentId}/guardian-links`,
      request,
      { params: institutionId ? { institutionId } : undefined }
    );
    return res.data;
  },

  async requestGuardianChange(
    tenantId: string,
    linkId: string,
    request: GuardianChangeRequest,
    institutionId?: string
  ): Promise<GuardianLinkItem> {
    const res = await api.post<GuardianLinkItem>(
      `/api/tenants/${tenantId}/guardian-links/${linkId}/change-requests`,
      request,
      { params: institutionId ? { institutionId } : undefined }
    );
    return res.data;
  },

  async decideGuardianChange(
    tenantId: string,
    linkId: string,
    request: GuardianChangeDecision,
    institutionId?: string
  ): Promise<GuardianLinkItem> {
    const res = await api.post<GuardianLinkItem>(
      `/api/tenants/${tenantId}/guardian-links/${linkId}/change-approvals`,
      request,
      { params: institutionId ? { institutionId } : undefined }
    );
    return res.data;
  },
};
