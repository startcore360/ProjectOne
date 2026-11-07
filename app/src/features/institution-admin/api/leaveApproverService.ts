import api from "../../../services/axios";

export type ApproverKind = "MANAGEMENT" | "GOVERNING_BODY_APPROVER" | "REGISTRAR" | "VICE_CHANCELLOR";

export type LeaveApproverSetPayload = {
  approverKind: ApproverKind;
  approverUserId: string;
};

export type LeaveApproverSettingItem = {
  leaveApproverSettingId: string;
  tenantId: string;
  subjectRole: "PRINCIPAL" | "VICE_CHANCELLOR" | "DATA_PROTECTION_CONTACT";
  institutionId?: string;
  approverKind: ApproverKind;
  approverUserId: string;
  status: "ACTIVE" | "SUPERSEDED";
  effectiveFrom: string;
  rowVersion: number;
};

export type GoLiveBlockerItem = {
  blocker: "PRINCIPAL_LEAVE_APPROVER_MISSING" | "VC_LEAVE_APPROVER_MISSING" | string;
  institutionId?: string;
};

export type GoLiveCheckResponse = {
  ready: boolean;
  blockers: GoLiveBlockerItem[];
};

export const leaveApproverService = {
  async setPrincipalLeaveApprover(
    tenantId: string,
    institutionId: string,
    payload: LeaveApproverSetPayload
  ): Promise<LeaveApproverSettingItem> {
    const res = await api.put<LeaveApproverSettingItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/principal-leave-approver`,
      payload
    );
    return res.data;
  },

  async getPrincipalLeaveApprover(
    tenantId: string,
    institutionId: string
  ): Promise<LeaveApproverSettingItem> {
    const res = await api.get<LeaveApproverSettingItem>(
      `/api/tenants/${tenantId}/institutions/${institutionId}/principal-leave-approver`
    );
    return res.data;
  },

  async setVcLeaveApprover(
    tenantId: string,
    payload: LeaveApproverSetPayload
  ): Promise<LeaveApproverSettingItem> {
    const res = await api.put<LeaveApproverSettingItem>(
      `/api/tenants/${tenantId}/vc-leave-approver`,
      payload
    );
    return res.data;
  },

  async getVcLeaveApprover(tenantId: string): Promise<LeaveApproverSettingItem> {
    const res = await api.get<LeaveApproverSettingItem>(
      `/api/tenants/${tenantId}/vc-leave-approver`
    );
    return res.data;
  },

  async getGoLiveCheck(tenantId: string): Promise<GoLiveCheckResponse> {
    const res = await api.get<GoLiveCheckResponse>(
      `/api/tenants/${tenantId}/go-live-check`
    );
    return res.data;
  },
};
