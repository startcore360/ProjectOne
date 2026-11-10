import api from "../../../services/axios";

export interface NotificationItem {
  notificationId: string;
  triggerCode: string;
  title: string;
  body: string | null;
  priorityClass: string;
  createdAt: string;
}

export interface LeaveBalanceItem {
  leaveBalanceId: string;
  leaveTypeId: string;
  leaveYear: number;
  creditedDays: number;
  usedDays: number;
  remainingDays: number;
}

export interface LeaveRequestItem {
  leaveRequestId: string;
  leaveTypeId: string;
  unit: string;
  startDate: string;
  endDate: string;
  reason: string;
  daysCounted: number;
  processCode: string;
  status: string;
  createdAt: string;
}

export interface NoticeItem {
  noticeId: string;
  noticeLevel: string;
  noticeKind: string;
  title: string;
  body: string;
  authoredActiveRole: string;
  publishedAt: string | null;
}

export const selfServiceApi = {
  getNotifications: async (tenantId: string): Promise<NotificationItem[]> => {
    const res = await api.get<NotificationItem[]>(`/tenants/${tenantId}/me/notifications`);
    return Array.isArray(res.data) ? res.data : [];
  },
  getLeaveBalances: async (tenantId: string): Promise<LeaveBalanceItem[]> => {
    const res = await api.get<LeaveBalanceItem[]>(`/tenants/${tenantId}/me/leave-balances`);
    return Array.isArray(res.data) ? res.data : [];
  },
  getLeaveRequests: async (tenantId: string): Promise<LeaveRequestItem[]> => {
    const res = await api.get<LeaveRequestItem[]>(`/tenants/${tenantId}/me/leave-requests`);
    return Array.isArray(res.data) ? res.data : [];
  },
  getNotices: async (tenantId: string): Promise<NoticeItem[]> => {
    const res = await api.get<NoticeItem[]>(`/tenants/${tenantId}/notices`);
    return Array.isArray(res.data) ? res.data : [];
  },
};
