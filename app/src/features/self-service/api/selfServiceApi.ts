import axios from "axios";

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
    const res = await axios.get<NotificationItem[]>(`/v1/tenants/${tenantId}/me/notifications`);
    return res.data;
  },
  getLeaveBalances: async (tenantId: string): Promise<LeaveBalanceItem[]> => {
    const res = await axios.get<LeaveBalanceItem[]>(`/v1/tenants/${tenantId}/me/leave-balances`);
    return res.data;
  },
  getLeaveRequests: async (tenantId: string): Promise<LeaveRequestItem[]> => {
    const res = await axios.get<LeaveRequestItem[]>(`/v1/tenants/${tenantId}/me/leave-requests`);
    return res.data;
  },
  getNotices: async (tenantId: string): Promise<NoticeItem[]> => {
    const res = await axios.get<NoticeItem[]>(`/v1/tenants/${tenantId}/notices`);
    return res.data;
  },
};
